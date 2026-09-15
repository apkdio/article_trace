package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.EmailTemplateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Year;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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

    /** 邮件模板名（对应 templates/email/ 下的 email-code.html 与 email-code.txt） */
    private static final String TEMPLATE_NAME = "email-code";
    /** 邮件主题（最终主题还会被 email.subjectPrefix 加上 [文迹] 前缀） */
    private static final String MAIL_SUBJECT = "邮箱验证码";

    private static final String KEY_CODE = "email:code:%s:%s";
    private static final String KEY_COOLDOWN = "email:code:cooldown:%s:%s";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 原子校验：只有取出的值与传入值相同才删除该 key。
     *
     * <p>等价于「GET 比对成功后 DEL」，但整个过程在 Redis 内一次完成，
     * 避免并发下同一个验证码被两个请求各消费一次；
     * 同时保留「输错不消费」的语义（错误的尝试不销毁验证码）。</p>
     */
    private static final DefaultRedisScript<Long> VERIFY_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if v and v == ARGV[1] then redis.call('DEL', KEYS[1]) return 1 end "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final MailService mailService;
    private final EmailTemplateUtil emailTemplateUtil;

    /** 模板里的 logo 地址；必须是公网可访问的绝对 URL，留空则渲染出空 src */
    @Value("${email.logoUrl:}")
    private String logoUrl;

    public EmailCodeServiceImpl(@Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate,
                                MailService mailService,
                                EmailTemplateUtil emailTemplateUtil) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.mailService = mailService;
        this.emailTemplateUtil = emailTemplateUtil;
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

            // 3. 渲染模板并提交投递（异步，失败由重试任务补发）
            renderAndSend(normalized, code);

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

    /**
     * 渲染邮件模板并提交投递。
     *
     * <p>两个载体同时投递：支持 HTML 的客户端渲染富文本，纯文本客户端回落到 {@code .txt}。
     * 任一模板缺失都视为部署问题，只记日志不发信——宁可不发，也不发半成品。</p>
     */
    private void renderAndSend(String email, String code) {
        Map<String, String> vars = new HashMap<>();
        vars.put("code", code);
        vars.put("minutes", String.valueOf(CODE_TTL_MINUTES));
        vars.put("year", String.valueOf(Year.now().getValue()));
        vars.put("logoUrl", logoUrl == null ? "" : logoUrl.trim());

        String text = emailTemplateUtil.render(TEMPLATE_NAME, "txt", vars);
        String html = emailTemplateUtil.render(TEMPLATE_NAME, "html", vars);
        if (text == null || html == null) {
            log.error("email code not sent: template missing, email={}, text={}, html={}",
                    email, text != null, html != null);
            return;
        }
        mailService.send(email, MAIL_SUBJECT, text, html);
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
            // 一次性：匹配成功才失效；输错不消费（可在有效期内重填）
            Long matched = stringRedisTemplate.execute(
                    VERIFY_AND_DELETE_SCRIPT, java.util.Collections.singletonList(key), code.trim());
            return matched != null && matched == 1L;
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
