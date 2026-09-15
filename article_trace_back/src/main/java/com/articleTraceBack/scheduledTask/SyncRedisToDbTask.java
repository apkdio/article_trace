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
 * 把 Redis 中累积的浏览量增量汇总进 MySQL。
 *
 * <p><b>为什么不用 {@code RENAME} 搬数据</b>：{@code RENAME} 会覆盖已存在的目标键。
 * 一旦上一轮在批量写库前失败，{@code :processing} 里就留着未消费的增量，
 * 下一轮 {@code rename} 会直接把它冲掉——这批浏览量永久丢失，且日志只留下一条 "Sync failed"。
 * 若期间没有新的浏览（原键不存在），那份残留更是永远无人处理。</p>
 *
 * <p>现在改为用 Lua 把最新增量**累加**进 {@code :processing}：既不清掉残留，也不漏掉新数据，
 * 且整个合并是原子的。只有批量写库成功后才删除 {@code :processing}——失败则原样保留，下一轮继续消费。</p>
 *
 * <p><b>投递语义是「至少一次」，不是「恰好一次」</b>：若批量写库成功、但在删除 {@code :processing}
 * 之前进程退出，下一轮会重放同一份增量，导致该批浏览量<b>多算一次</b>。
 * 这是 Redis 与 MySQL 之间无事务的固有限制，无法根除——只能接受极端情况下少量偏大。
 * 方向是刻意选的：宁可多算不可少算（少算意味着用户真实浏览被抹掉）。</p>
 */
@Component
@EnableScheduling
@Slf4j
public class SyncRedisToDbTask {

    /**
     * 把 {@code KEYS[1]} 哈希的每个字段值累加到 {@code KEYS[2]}，然后删除 {@code KEYS[1]}。
     *
     * <p>用 {@code HINCRBY} 而不是覆盖：目标键里可能还有上一轮未消费完的残留。</p>
     */
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
