package com.articleTraceBack.Utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 同一秒内两次登录也必须签出不同的 token，否则 Redis 里的「顶号」判断失效。 */
public class JwtUtilTest {

    private JwtUtil jwt() {
        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret_KEY", "test-secret-for-unit-test");
        ReflectionTestUtils.setField(jwt, "longTime", 72 * 3600 * 1000);
        ReflectionTestUtils.setField(jwt, "shortTime", 24 * 3600 * 1000);
        return jwt;
    }

    @Test
    public void sameSecondLoginsProduceDifferentTokens() {
        JwtUtil jwt = jwt();
        Map<String, Object> claims = Map.of("id", 1, "name", "u", "type", 1);
        String first = jwt.genToken(claims, 0);
        String second = jwt.genToken(claims, 0);
        assertNotNull(first);
        assertNotEquals(first, second, "claims 与 exp 相同也必须有 jti 区分，否则旧令牌顶不掉");
    }
}
