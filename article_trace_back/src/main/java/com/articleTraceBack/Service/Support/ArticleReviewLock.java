package com.articleTraceBack.Service.Support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 单篇文章的审核占用：同一时间只让一个站长进入审核。
 *
 * <p>只放 Redis、带 TTL：正常审核完主动释放，放弃审核或进程退出则等 TTL 自然过期。
 * Redis 不可用时 fail-open（放行），宁可少拦一次，也不让锁把审核卡死；真正的并发仍由 version CAS 兜底。</p>
 */
@Slf4j
@Component
public class ArticleReviewLock {

    /** 占用的自然过期时间：放弃审核时最迟这么久后别人能接手 */
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "article:review-lock:";

    private final StringRedisTemplate redis;

    public ArticleReviewLock(@Qualifier("stringRedisTemplateArticle") StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试占用；同一个人重复进入视为续期。
     *
     * @return true = 可以审核（占用成功，或 Redis 故障放行）
     */
    public boolean tryLock(int articleId, int userId, String username) {
        String key = PREFIX + articleId;
        String reviewer = userId + ":" + username;
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, reviewer, TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            String current = redis.opsForValue().get(key);
            if (current != null && current.startsWith(userId + ":")) {
                redis.expire(key, TTL);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("review lock unavailable, fail-open: articleId={}", articleId, e);
            return true;
        }
    }

    /** 释放占用；只删自己持有的那个，避免误删别人刚抢到的锁 */
    public void release(int articleId, int userId) {
        String key = PREFIX + articleId;
        try {
            String current = redis.opsForValue().get(key);
            if (current != null && current.startsWith(userId + ":")) {
                redis.delete(key);
            }
        } catch (Exception e) {
            log.warn("release review lock failed: articleId={}", articleId, e);
        }
    }
}
