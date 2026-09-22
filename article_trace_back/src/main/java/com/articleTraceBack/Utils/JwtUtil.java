package com.articleTraceBack.Utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
// JWT相关
public class JwtUtil {
    @Value("${JWT.Secure_KEY}")
    private String secret_KEY;
    @Value("${JWT.longTime}")
    private int longTime;
    @Value("${JWT.shortTime}")
    private int shortTime;

    /** 接收业务数据并生成 token 返回（按 type 决定有效期） */
    public String genToken(Map<String, Object> claims, int type) {
        // type=1（记住我）用 longTime（72 小时），否则用 shortTime（24 小时）
        long expireTime;
        if (type == 1) {
            expireTime = longTime;
        } else {
            expireTime = shortTime;
        }
        return JWT.create()
                .withClaim("userInfo", claims)
                .withExpiresAt(new Date(System.currentTimeMillis() + expireTime))
                .sign(Algorithm.HMAC256(secret_KEY));
    }

    /** 校验 token 并返回其中的业务数据；无效抛异常 */
    public Map<String, Object> parseToken(String token) {
        return JWT.require(Algorithm.HMAC256(secret_KEY))
                .build()
                .verify(token)
                .getClaim("userInfo")
                .asMap();
    }

}
