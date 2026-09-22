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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 邮箱验证码实现：冷却检查 → 生成 6 位码 → 异步投递 → 写 Redis（5 分钟）；校验一次性，成功即删。 */
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
    /** 同一邮箱在同一场景下的连续失败次数 */
    private static final String KEY_FAIL = "email:code:fail:%s:%s";
    /** 失败超阈值后的邮箱锁定；独立于验证码寿命，否则锁的时长会随「第几次才用尽」漂移 */
    private static final String KEY_LOCK = "email:code:lock:%s:%s";
    /** 同一来源（IP+UA）的请求计数，防止脚本换着邮箱扫 */
    private static final String KEY_CLIENT = "email:code:client:%s";

    /** 连续失败达到该次数即作废验证码并锁定邮箱 */
    private static final long FAIL_THRESHOLD = 5;
    /** 锁定与失败计数的存活时间（秒），与验证码有效期对齐 */
    private static final long LOCK_SECONDS = CODE_TTL_MINUTES * 60;
    /** 同一来源的请求窗口（秒）与窗口内上限 */
    private static final long CLIENT_WINDOW_SECONDS = 60;
    private static final long CLIENT_THRESHOLD = 30;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
    * 一次 Lua 完成「来源限流 → 锁定判定 → 比对 → 计数 → 作废 / 上锁」，利用 Redis 单线程串行保证并发正确性。
    * 返回码与 {@link EmailCodeService} 的 CODE_* 一致。
    */
    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>(
            // KEYS[1] 验证码  KEYS[2] 失败计数  KEYS[3] 邮箱锁定  KEYS[4] 来源计数
            // ARGV[1] 用户输入  ARGV[2] 计数TTL  ARGV[3] 失败阈值  ARGV[4] 锁定TTL
            // ARGV[5] 来源阈值  ARGV[6] 来源窗口
            "local hits = redis.call('INCR', KEYS[4]) "
                    + "if hits == 1 then redis.call('EXPIRE', KEYS[4], ARGV[6]) end "
                    + "if hits > tonumber(ARGV[5]) then return -3 end "
                    // 先判限流再碰邮箱维度的计数：扫描行为不该把别人的账号锁掉
                    + "if redis.call('EXISTS', KEYS[3]) == 1 then return -2 end "
                    + "local v = redis.call('GET', KEYS[1]) "
                    + "if not v then return 0 end "
                    + "if v == ARGV[1] then "
                    + "  redis.call('DEL', KEYS[1]) redis.call('DEL', KEYS[2]) return 1 end "
                    + "local n = redis.call('INCR', KEYS[2]) "
                    + "if n == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end "
                    + "if n >= tonumber(ARGV[3]) then "
                    // 必须删码：留着的话第 6 次撞对仍然会通过，计数就白加了
                    + "  redis.call('DEL', KEYS[1]) "
                    + "  redis.call('SET', KEYS[3], '1', 'EX', ARGV[4]) "
                    + "  return -1 end "
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
            // 0. 锁定检查：连续失败超阈值后，连「重新获取」也挡住。
            //    只锁 verify 不锁 send 等于没锁——重新发一枚码，计数与作废就都绕过去了。
            if (lockRemainingSeconds(normalized, scene) > 0) {
                log.info("email code rejected by lock: email={}, scene={}", normalized, scene);
                return false;
            }

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

    /** 渲染邮件模板并提交投递；HTML 与纯文本两载体同时投递，任一模板缺失只记日志不发信。 */
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
    public int verify(String email, String scene, String code, String clientKey) {
        if (email == null || email.isBlank() || code == null || code.isBlank()
                || scene == null || scene.isBlank()) {
            return CODE_WRONG;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            Long result = stringRedisTemplate.execute(VERIFY_SCRIPT,
                    List.of(String.format(KEY_CODE, scene, normalized),
                            String.format(KEY_FAIL, scene, normalized),
                            String.format(KEY_LOCK, scene, normalized),
                            String.format(KEY_CLIENT, (clientKey == null || clientKey.isBlank())
                                    ? "unknown" : clientKey)),
                    code.trim(),
                    String.valueOf(LOCK_SECONDS), String.valueOf(FAIL_THRESHOLD),
                    String.valueOf(LOCK_SECONDS), String.valueOf(CLIENT_THRESHOLD),
                    String.valueOf(CLIENT_WINDOW_SECONDS));
            return (result == null) ? CODE_WRONG : result.intValue();
        } catch (Exception e) {
            log.error("verify email code failed: email={}, scene={}", normalized, scene, e);
            return CODE_WRONG;
        }
    }

    @Override
    public long lockRemainingSeconds(String email, String scene) {
        if (email == null || email.isBlank() || scene == null || scene.isBlank()) {
            return 0;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Long ttl = stringRedisTemplate.getExpire(String.format(KEY_LOCK, scene, normalized),
                TimeUnit.SECONDS);
        return (ttl == null || ttl < 0) ? 0 : ttl;
    }

    private String randomCode() {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        return String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(bound));
    }
}
