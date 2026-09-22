package com.articleTraceBack.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 登录失败计数实现（Redis）：{@code login:fail:{key}} 记次数、{@code login:block:{key}} 为黑名单标记；固定窗口，仅在首次失败时设 TTL。 */
@Slf4j
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final String KEY_FAIL = "login:fail:%s";
    private static final String KEY_BLOCK = "login:block:%s";

    private final StringRedisTemplate stringRedisTemplate;

    public LoginAttemptServiceImpl(@Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public long blockRemainingSeconds(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return 0;
        }
        try {
            Long ttl = stringRedisTemplate.getExpire(blockKey(clientKey), TimeUnit.SECONDS);
            return (ttl < 0) ? 0 : ttl;
        } catch (Exception e) {
            log.error("read login block ttl failed: key={}", clientKey, e);
            return 0;
        }
    }

    @Override
    public boolean needCaptcha(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return false;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(failKey(clientKey));
            return value != null && Long.parseLong(value) >= CAPTCHA_THRESHOLD;
        } catch (Exception e) {
            log.error("read login fail count failed: key={}", clientKey, e);
            return false;
        }
    }

    @Override
    public long recordFailure(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return 0;
        }
        try {
            String key = failKey(clientKey);
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return 0;
            }
            // 固定窗口：只在首次失败时设 TTL，后续失败不续期
            if (count == 1) {
                stringRedisTemplate.expire(key, WINDOW_MINUTES, TimeUnit.MINUTES);
            }
            if (count >= BLOCK_THRESHOLD) {
                stringRedisTemplate.opsForValue()
                        .set(blockKey(clientKey), "1", BLOCK_MINUTES, TimeUnit.MINUTES);
                log.warn("login blocked: key={}, failures={}", clientKey, count);
            }
            return count;
        } catch (Exception e) {
            log.error("record login failure failed: key={}", clientKey, e);
            return 0;
        }
    }

    @Override
    public void clear(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(List.of(failKey(clientKey), blockKey(clientKey)));
        } catch (Exception e) {
            log.error("clear login failure failed: key={}", clientKey, e);
        }
    }

    private String failKey(String clientKey) {
        return String.format(KEY_FAIL, clientKey);
    }

    private String blockKey(String clientKey) {
        return String.format(KEY_BLOCK, clientKey);
    }
}
