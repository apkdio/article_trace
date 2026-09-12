package com.articleTraceBack;

import com.articleTraceBack.Service.EmailCodeService;
import com.articleTraceBack.mapper.NotificationMailMapper;
import com.articleTraceBack.pojo.NotificationMail;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮箱验证码测试：发送、冷却限流、一次性校验。
 *
 * <p>用不可达邮箱，避免测试真的发出去；投递记录在用例后清理。</p>
 * <pre>mvn test -Dtest=EmailCodeServiceTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EmailCodeServiceTest {

    private static final String TEST_EMAIL = "code-test@example.invalid";
    private static final String SCENE = EmailCodeService.SCENE_REGISTER;

    @Autowired
    private EmailCodeService emailCodeService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private NotificationMailMapper mailMapper;

    @AfterEach
    public void cleanup() {
        stringRedisTemplate.delete("email:code:" + SCENE + ":" + TEST_EMAIL);
        stringRedisTemplate.delete("email:code:cooldown:" + SCENE + ":" + TEST_EMAIL);
        QueryWrapper<NotificationMail> w = new QueryWrapper<>();
        w.eq("to_email", TEST_EMAIL);
        mailMapper.delete(w);
    }

    @Test
    public void testSendAndVerifyOnce() {
        assertTrue(emailCodeService.send(TEST_EMAIL, SCENE), "首次发送应被受理");

        String codeKey = "email:code:" + SCENE + ":" + TEST_EMAIL;
        String saved = stringRedisTemplate.opsForValue().get(codeKey);
        assertNotNull(saved, "验证码应写入 Redis");
        assertTrue(saved.matches("\\d{6}"), "验证码应为 6 位数字");

        // 错误验证码
        String wrong = saved.equals("000000") ? "111111" : "000000";
        assertFalse(emailCodeService.verify(TEST_EMAIL, SCENE, wrong), "错误验证码应校验失败");

        // 正确验证码 → 通过，且一次性
        assertTrue(emailCodeService.verify(TEST_EMAIL, SCENE, saved), "正确验证码应通过");
        assertFalse(emailCodeService.verify(TEST_EMAIL, SCENE, saved), "验证码应一次性失效");
    }

    @Test
    public void testCooldownBlocksResend() {
        assertTrue(emailCodeService.send(TEST_EMAIL, SCENE), "首次发送应被受理");
        assertFalse(emailCodeService.send(TEST_EMAIL, SCENE), "冷却期内重复发送应被拒绝");
    }
}
