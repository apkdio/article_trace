package com.articleTraceBack.Service;

import com.wf.captcha.SpecCaptcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码实现（easy-captcha + Redis）。
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    /** 有效期（秒） */
    private static final long TTL_SECONDS = 120;
    private static final String KEY_PREFIX = "captcha:";
    private static final int WIDTH = 130;
    private static final int HEIGHT = 44;
    private static final int LENGTH = 4;

    private final StringRedisTemplate stringRedisTemplate;

    public CaptchaServiceImpl(@Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Map<String, String> generate() {
        SpecCaptcha captcha = new SpecCaptcha(WIDTH, HEIGHT, LENGTH);
        String code = captcha.text().toLowerCase(Locale.ROOT);
        String captchaId = UUID.randomUUID().toString().replace("-", "");

        stringRedisTemplate.opsForValue()
                .set(KEY_PREFIX + captchaId, code, TTL_SECONDS, TimeUnit.SECONDS);

        Map<String, String> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("image", captcha.toBase64());
        return result;
    }

    @Override
    public boolean verify(String captchaId, String code) {
        if (captchaId == null || captchaId.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        try {
            String key = KEY_PREFIX + captchaId;
            String saved = stringRedisTemplate.opsForValue().get(key);
            // 无论对错都删除：一次有效，避免被反复尝试
            stringRedisTemplate.delete(key);
            return saved != null && saved.equalsIgnoreCase(code.trim());
        } catch (Exception e) {
            log.error("captcha verify failed: id={}", captchaId, e);
            return false;
        }
    }
}
