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

    //接收业务数据,生成token并返回
    public String genToken(Map<String, Object> claims, int type) {
        // 0为12小时，1为48小时
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

    //接收token,验证token,并返回业务数据
    public Map<String, Object> parseToken(String token) {
        return JWT.require(Algorithm.HMAC256(secret_KEY))
                .build()
                .verify(token)
                .getClaim("userInfo")
                .asMap();
    }

}
