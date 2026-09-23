package com.articleTraceBack;

import com.articleTraceBack.Service.CaptchaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 拉图限流：计数与设过期必须在同一个 Lua 里完成，进程中途退出也不能留下永不过期的计数键。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class CaptchaRateLimitTest {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testRateKeyAlwaysHasTtl() {
        String clientKey = "zz-test-" + System.currentTimeMillis();
        Map<String, String> result = captchaService.generate(clientKey);
        assertNotNull(result, "首次拉图不应被限流");

        Long ttl = stringRedisTemplate.getExpire("captcha:rate:" + clientKey, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 60, "限流计数键必须带 60 秒过期，实际 TTL=" + ttl);
    }
}
