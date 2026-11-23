package com.articleTraceBack.Utils;

/** 会话过期（JWT 到期，或 Redis 里的令牌已被清掉/顶掉）：回 401 + expired=true，前端清会话后重新登录。 */
public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException() {
        super("登录已过期，请重新登录");
    }
}
