package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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
        if (!enabled) {
            log.info("data sync scheduler is disabled. Redis data will not synchronize to Mysql !");
            return;
        }
        log.info("starting synchronizing pending view increments from Redis to Mysql...");
        try {
            String processingKey = viewKey + ":processing";
            if (!redisTemplate.hasKey(viewKey)) {
                log.info("No pending views to sync.");
                return;
            }

            redisTemplate.rename(viewKey, processingKey);

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
            redisTemplate.delete(processingKey);

            redisTemplate.delete("article:hot:top10");
            log.info("Sync complete: {} articles updated.", deltas.size());
        } catch (Exception e) {
            log.error("Sync failed", e);
        }
    }
}
