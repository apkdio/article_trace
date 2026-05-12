package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@EnableScheduling
@Slf4j
public class SyncRedisToDbTask {
    @Value("${spring.data.redis.viewKey}")
    private String viewKey;
    private final StringRedisTemplate redisTemplate;
    private final ArticleMapper articleMapper;
    @Value("${scheduler.sync.enabled}")
    private Boolean enabled;

    public SyncRedisToDbTask(@Qualifier("stringRedisTemplateArticle") StringRedisTemplate redisTemplate, ArticleMapper articleMapper) {
        this.redisTemplate = redisTemplate;
        this.articleMapper = articleMapper;
    }

    @Scheduled(cron = "${scheduler.sync.cron}")
    public void syncIncrementalViews() {
        if(!enabled){
            log.info("data sync scheduler is disabled. Redis data will not synchronize to Mysql !");
            return;
        }
        log.info("starting synchronizing data from Redis to Mysql...");
        try {
            // 从 ZSet 中获取所有文章 ID 及其阅读数
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(viewKey, 0, -1);

            if (tuples == null || tuples.isEmpty()) {
                log.info("No data to sync.");
                return;
            }

            // 转换为 Map<Long, Long>
            Map<Long, Long> updates = new HashMap<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                Long articleId = Long.valueOf(Objects.requireNonNull(tuple.getValue()));
                Long views = Objects.requireNonNull(tuple.getScore()).longValue();
                updates.put(articleId, views);
            }

            // 批量更新数据库（使用 CASE WHEN 直接覆盖）
            articleMapper.batchIncrementViews(updates);

        } catch (Exception e) {
            log.error("Sync failed", e);
        } finally {
            log.info("synchronizing data complete !");
        }
    }
}