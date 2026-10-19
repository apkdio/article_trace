package com.articleTraceBack.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败计数实现（Redis）：{@code login:fail:<键>} 记次数、{@code login:block:<键>} 为黑名单标记；
 * 固定窗口，仅在首次失败时设 TTL。两个维度各占一组键——账号名带 {@code u:} 前缀，IP+UA 指纹原样，
 * 前缀是为了两者不撞键。
 */
@Slf4j
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final String KEY_FAIL = "login:fail:%s";
    private static final String KEY_BLOCK = "login:block:%s";
    /** 账号维度键前缀 */
    private static final String ACCOUNT_PREFIX = "u:";

    private final StringRedisTemplate stringRedisTemplate;

    public LoginAttemptServiceImpl(@Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public long blockRemainingSeconds(String username, String clientKey) {
        return Math.max(blockTtl(accountKey(username)), blockTtl(clientKey));
    }

    @Override
    public boolean needCaptcha(String username, String clientKey) {
        return failCount(accountKey(username)) >= CAPTCHA_THRESHOLD
                || failCount(clientKey) >= CAPTCHA_THRESHOLD;
    }

    @Override
    public long recordFailure(String username, String clientKey) {
        long byAccount = bump(accountKey(username));
        long byClient = bump(clientKey);
        long failures = Math.max(byAccount, byClient);
        if (failures >= BLOCK_THRESHOLD) {
            // 两个维度一起拉黑：账号维度挡住换 IP 的爆破，客户端维度挡住一台机器扫多个账号
            block(accountKey(username));
            block(clientKey);
            log.warn("login blocked: username={}, clientKey={}, failures={}", username, clientKey, failures);
        }
        return failures;
    }

    @Override
    public void clear(String username, String clientKey) {
        delete(accountKey(username));
        delete(clientKey);
    }

    /** 账号维度的键；空账号名返回 null（调用方跳过该维度） */
    private String accountKey(String username) {
        return (username == null || username.isBlank()) ? null : ACCOUNT_PREFIX + username;
    }

    private long blockTtl(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        try {
            Long ttl = stringRedisTemplate.getExpire(blockKey(key), TimeUnit.SECONDS);
            return (ttl == null || ttl < 0) ? 0 : ttl;
        } catch (Exception e) {
            log.error("read login block ttl failed: key={}", key, e);
            return 0;
        }
    }

    private long failCount(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(failKey(key));
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception e) {
            log.error("read login fail count failed: key={}", key, e);
            return 0;
        }
    }

    /** 计数 +1；固定窗口只在首次失败时设 TTL，后续失败不续期 */
    private long bump(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        try {
            String redisKey = failKey(key);
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count == null) {
                return 0;
            }
            if (count == 1) {
                stringRedisTemplate.expire(redisKey, WINDOW_MINUTES, TimeUnit.MINUTES);
            }
            return count;
        } catch (Exception e) {
            log.error("record login failure failed: key={}", key, e);
            return 0;
        }
    }

    private void block(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(blockKey(key), "1", BLOCK_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("write login block failed: key={}", key, e);
        }
    }

    private void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(List.of(failKey(key), blockKey(key)));
        } catch (Exception e) {
            log.error("clear login failure failed: key={}", key, e);
        }
    }

    private String failKey(String key) {
        return String.format(KEY_FAIL, key);
    }

    private String blockKey(String key) {
        return String.format(KEY_BLOCK, key);
    }
}
