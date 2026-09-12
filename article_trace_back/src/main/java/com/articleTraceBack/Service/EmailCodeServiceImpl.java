package com.articleTraceBack.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码实现。
 *
 * <p>流程：冷却检查 → 生成 6 位码 → 提交异步投递 → 写入 Redis（5 分钟有效期）。
 * 校验为一次性，成功后立即删除。</p>
 */
@Slf4j
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    /** 验证码有效期（分钟） */
    private static final long CODE_TTL_MINUTES = 5;
    /** 同一邮箱发送冷却（秒） */
    private static final long COOLDOWN_SECONDS = 60;
    /** 验证码位数 */
    private static final int CODE_LENGTH = 6;

    private static final String KEY_CODE = "email:code:%s:%s";
    private static final String KEY_COOLDOWN = "email:code:cooldown:%s:%s";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;
    private final MailService mailService;

    public EmailCodeServiceImpl(@Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate,
                                MailService mailService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.mailService = mailService;
    }

    @Override
    public boolean send(String email, String scene) {
        if (email == null || email.isBlank() || scene == null || scene.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            // 1. 冷却检查（防止刷验证码）
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(String.format(KEY_COOLDOWN, scene, normalized), "1",
                            COOLDOWN_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(acquired)) {
                log.info("email code rejected by cooldown: email={}, scene={}", normalized, scene);
                return false;
            }

            // 2. 生成验证码
            String code = randomCode();

            // 3. 提交投递（异步，失败由重试任务补发）
            mailService.send(normalized, "邮箱验证码",
                    "你的验证码是：" + code + "\n\n5 分钟内有效，请勿泄露给他人。\n若非本人操作，请忽略本邮件。");

            // 4. 暂存 Redis
            stringRedisTemplate.opsForValue()
                    .set(String.format(KEY_CODE, scene, normalized), code,
                            CODE_TTL_MINUTES, TimeUnit.MINUTES);

            log.info("email code sent: email={}, scene={}", normalized, scene);
            return true;
        } catch (Exception e) {
            log.error("send email code failed: email={}, scene={}", normalized, scene, e);
            return false;
        }
    }

    @Override
    public boolean verify(String email, String scene, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()
                || scene == null || scene.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            String key = String.format(KEY_CODE, scene, normalized);
            String saved = stringRedisTemplate.opsForValue().get(key);
            if (saved == null || !saved.equals(code.trim())) {
                return false;
            }
            // 一次性：校验通过立即失效
            stringRedisTemplate.delete(key);
            return true;
        } catch (Exception e) {
            log.error("verify email code failed: email={}, scene={}", normalized, scene, e);
            return false;
        }
    }

    private String randomCode() {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        return String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(bound));
    }
}
