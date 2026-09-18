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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    /** 固定一个来源指纹：用例之间互不影响，清理时也能精确删掉 */
    private static final String CLIENT = "test-client";

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
        // 失败计数与锁定键也要清，否则一个用例的锁定会把后面的用例一起锁死
        stringRedisTemplate.delete("email:code:fail:" + SCENE + ":" + TEST_EMAIL);
        stringRedisTemplate.delete("email:code:lock:" + SCENE + ":" + TEST_EMAIL);
        stringRedisTemplate.delete("email:code:client:" + CLIENT);
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
        assertEquals(EmailCodeService.CODE_WRONG,
                emailCodeService.verify(TEST_EMAIL, SCENE, wrong, CLIENT), "错误验证码应校验失败");

        // 正确验证码 → 通过，且一次性
        assertEquals(EmailCodeService.CODE_OK,
                emailCodeService.verify(TEST_EMAIL, SCENE, saved, CLIENT), "正确验证码应通过");
        assertEquals(EmailCodeService.CODE_WRONG,
                emailCodeService.verify(TEST_EMAIL, SCENE, saved, CLIENT), "验证码应一次性失效");
    }

    /**
     * 核心回归：连续输错到阈值后，**即使输入正确的验证码也不能通过**。
     *
     * <p>之前只有一段「比对成功才 DEL」的脚本 —— 输错不消费，意味着 6 位码在 5 分钟
     * 有效期内可以被无限次撞库。作废与锁定必须挡在正确码前面，才叫真的堵住。</p>
     */
    @Test
    public void testWrongCodesExhaustAndLock() {
        assertTrue(emailCodeService.send(TEST_EMAIL, SCENE), "首次发送应被受理");
        String codeKey = "email:code:" + SCENE + ":" + TEST_EMAIL;
        String code = stringRedisTemplate.opsForValue().get(codeKey);
        assertNotNull(code, "验证码应写入 Redis");
        String wrong = code.equals("000000") ? "111111" : "000000";

        // 前 4 次：只是错误，验证码仍然有效
        for (int i = 1; i < 5; i++) {
            assertEquals(EmailCodeService.CODE_WRONG,
                    emailCodeService.verify(TEST_EMAIL, SCENE, wrong, CLIENT), "第 " + i + " 次失败应只是报错");
        }
        // 第 5 次失败：作废 + 上锁
        assertEquals(EmailCodeService.CODE_EXHAUSTED,
                emailCodeService.verify(TEST_EMAIL, SCENE, wrong, CLIENT), "第 5 次失败应作废验证码");
        assertNull(stringRedisTemplate.opsForValue().get(codeKey), "作废后验证码必须被删除");

        // 关键断言：锁定后拿正确的码也进不去
        assertEquals(EmailCodeService.CODE_LOCKED,
                emailCodeService.verify(TEST_EMAIL, SCENE, code, CLIENT), "锁定后正确验证码也不得通过");

        // 锁定期间重发也要被挡：只锁 verify 不锁 send 等于没锁
        assertFalse(emailCodeService.send(TEST_EMAIL, SCENE), "锁定期间不允许重新获取验证码");
        assertTrue(emailCodeService.lockRemainingSeconds(TEST_EMAIL, SCENE) > 0, "锁定应有剩余时长");
    }

    @Test
    public void testCooldownBlocksResend() {
        assertTrue(emailCodeService.send(TEST_EMAIL, SCENE), "首次发送应被受理");
        assertFalse(emailCodeService.send(TEST_EMAIL, SCENE), "冷却期内重复发送应被拒绝");
    }

    @Test
    public void testConcurrentVerifyConsumesOnlyOnce() throws Exception {
        assertTrue(emailCodeService.send(TEST_EMAIL, SCENE), "首次发送应被受理");
        String codeKey = "email:code:" + SCENE + ":" + TEST_EMAIL;
        String code = stringRedisTemplate.opsForValue().get(codeKey);
        assertNotNull(code, "验证码应写入 Redis");

        int threads = 8;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger passed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (emailCodeService.verify(TEST_EMAIL, SCENE, code, CLIENT) == EmailCodeService.CODE_OK) {
                        passed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(20, TimeUnit.SECONDS), "并发校验应在超时前完成");
        pool.shutdown();

        assertEquals(1, passed.get(), "并发校验同一个验证码时只应有一个请求通过");
    }
}
