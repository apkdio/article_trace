package com.articleTraceBack.Utils;

import java.util.Map;

/**
 * 当前登录用户的唯一取值入口（TokenCheck 负责 set / remove）。
 * <p>
 * {@link #require()} 只给受 TokenCheck 保护的接口用：取不到直接抛 {@link NotLoggedInException} → 401；
 * {@link #optional()} 给 {@code /reader/**} 这类可选登录的公开接口用：null 表示匿名。
 * 异步链路（SSE 回调线程）没有 ThreadLocal，需要用户信息时按参数传进去，别在这里取。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public record Session(int id, String username, int type) {
    }

    public static Session require() {
        Session session = optional();
        if (session == null) {
            throw new NotLoggedInException();
        }
        return session;
    }

    public static Session optional() {
        Map<String, Object> user = ThreadLocalUtil.get();
        if (user == null) {
            return null;
        }
        return new Session((int) user.get("id"), String.valueOf(user.get("name")), (int) user.get("type"));
    }
}
