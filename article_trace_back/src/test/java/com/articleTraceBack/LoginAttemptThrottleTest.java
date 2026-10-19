package com.articleTraceBack;

import com.articleTraceBack.Service.LoginAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录失败限流的双维度（运维区 P0-7 的代码配合项）：账号维度必须独立于可伪造的 IP 指纹——
 * 攻击者换个 {@code X-Forwarded-For} 就换个桶的话，防爆破等于没有。
 *
 * <pre>mvn test -Dtest=LoginAttemptThrottleTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class LoginAttemptThrottleTest {

    @Autowired
    private LoginAttemptService loginAttemptService;

    private final String username = "zzt" + (System.nanoTime() % 100000000L);
    private final String clientA = "zzt-ip-a-" + System.nanoTime();
    private final String clientB = "zzt-ip-b-" + System.nanoTime();

    @AfterEach
    public void cleanup() {
        loginAttemptService.clear(username, clientA);
        loginAttemptService.clear(username, clientB);
    }

    /** 核心断言：同一个账号换客户端指纹，失败次数照样累加（IP 伪造不再能重置桶） */
    @Test
    public void accountBucketSurvivesClientFingerprintHopping() {
        assertFalse(loginAttemptService.needCaptcha(username, clientA), "初始不该要图形码");

        // 每次都换一个客户端指纹，模拟攻击者改 X-Forwarded-For
        for (int i = 0; i < LoginAttemptService.CAPTCHA_THRESHOLD; i++) {
            loginAttemptService.recordFailure(username, "zzt-hop-" + i + "-" + System.nanoTime());
        }

        assertTrue(loginAttemptService.needCaptcha(username, clientB),
                "换指纹后仍应要图形码——账号维度的桶在起作用");
    }

    @Test
    public void blockAppliesToFreshClientFingerprint() {
        for (int i = 0; i < LoginAttemptService.BLOCK_THRESHOLD; i++) {
            loginAttemptService.recordFailure(username, "zzt-hop2-" + i + "-" + System.nanoTime());
        }

        assertTrue(loginAttemptService.blockRemainingSeconds(username, clientB) > 0,
                "账号达阈值进黑名单后，换个指纹也要被拦");
    }

    @Test
    public void clearResetsBothDimensions() {
        for (int i = 0; i < LoginAttemptService.CAPTCHA_THRESHOLD; i++) {
            loginAttemptService.recordFailure(username, clientA);
        }
        assertTrue(loginAttemptService.needCaptcha(username, clientA), "前置：该客户端已达阈值");

        loginAttemptService.clear(username, clientA);

        assertFalse(loginAttemptService.needCaptcha(username, clientA), "清算后账号维度应清零");
        assertEquals(0, loginAttemptService.blockRemainingSeconds(username, clientA), "清算后不应仍在黑名单");
    }

    @Test
    public void otherAccountsAreNotAffected() {
        String other = username + "x";
        for (int i = 0; i < LoginAttemptService.BLOCK_THRESHOLD; i++) {
            loginAttemptService.recordFailure(username, "zzt-hop3-" + i + "-" + System.nanoTime());
        }

        assertTrue(loginAttemptService.blockRemainingSeconds(username, clientB) > 0, "前置：该账号已被锁");
        assertFalse(loginAttemptService.needCaptcha(other, "zzt-fresh-" + System.nanoTime()),
                "别的账号不该被牵连");
        loginAttemptService.clear(other, null);
    }
}
