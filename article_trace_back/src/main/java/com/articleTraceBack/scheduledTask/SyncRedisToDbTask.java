package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.constant.RedisKeys;
import com.articleTraceBack.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 Redis 累积的浏览量增量汇总进 MySQL。用 Lua 原子累加进 {@code :processing} 而非 RENAME（后者会覆盖上一轮未消费的残留）。
 * 投递语义为「至少一次」：极端情况下可能多算一次，宁可多算不可少算。
 */
@Component
@EnableScheduling
@Slf4j
public class SyncRedisToDbTask {

    /** 把 {@code KEYS[1]} 的每个字段累加到 {@code KEYS[2]} 后删除 {@code KEYS[1]}；用 HINCRBY 以保留目标键中上一轮的残留。 */
    private static final DefaultRedisScript<Long> MERGE_VIEWS_SCRIPT = new DefaultRedisScript<>(
            "local entries = redis.call('HGETALL', KEYS[1]) "
                    + "for i = 1, #entries, 2 do "
                    + "  redis.call('HINCRBY', KEYS[2], entries[i], entries[i + 1]) "
                    + "end "
                    + "redis.call('DEL', KEYS[1]) "
                    + "return #entries / 2",
            Long.class);

    @Value("${spring.data.redis.viewKey}")
    private String viewKey;

    private final StringRedisTemplate redisTemplate;
    private final ArticleMapper articleMapper;

    @Value("${scheduler.sync.enabled}")
    private Boolean enabled;

    public SyncRedisToDbTask(@Qualifier("stringRedisTemplateArticle") StringRedisTemplate redisTemplate,
                            ArticleMapper articleMapper) {
        this.redisTemplate = redisTemplate;
        this.articleMapper = articleMapper;
    }

    /** 把 Redis 浏览量增量汇总进 MySQL（每 10 分钟） */
    @Scheduled(cron = "${scheduler.sync.cron}")
    public void syncIncrementalViews() {
        if (!enabled) {
            log.info("data sync scheduler is disabled. Redis data will not synchronize to Mysql !");
            return;
        }

        String processingKey = viewKey + ":processing";
        try {
            // 1. 把本轮累积的增量并入 processing。累加而非覆盖，所以上一轮的残留不会被冲掉。
            Long merged = redisTemplate.execute(MERGE_VIEWS_SCRIPT, List.of(viewKey, processingKey));
            boolean hasPending = redisTemplate.hasKey(processingKey);
            if ((merged == null || merged == 0) && !hasPending) {
                log.info("No pending views to sync.");
                return;
            }

            // 2. 消费 processing —— 这里既有本轮并入的，也可能有上一轮失败留下的
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(processingKey);
            if (entries.isEmpty()) {
                redisTemplate.delete(processingKey);
                log.info("No pending views to sync.");
                return;
            }

            Map<Long, Long> deltas = new HashMap<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                Long articleId = Long.valueOf(entry.getKey().toString());
                Long delta = Long.valueOf(entry.getValue().toString());
                deltas.put(articleId, delta);
            }

            articleMapper.batchAddViews(deltas);
            // 写库成功才删。若这里之前进程退出，下一轮会重放这批增量、导致多算一次——
            // 属于「至少一次」语义的固有代价（Redis 与 MySQL 无事务），比少算可接受。
            redisTemplate.delete(processingKey);

            redisTemplate.delete(RedisKeys.ARTICLE_HOT_TOP10);
            log.info("Sync complete: {} articles updated.", deltas.size());
        } catch (Exception e) {
            // 此时 processingKey 原样保留，下一轮会连同新数据一起消费
            log.error("Sync failed, pending increments kept in {} for the next round", processingKey, e);
        }
    }
}
