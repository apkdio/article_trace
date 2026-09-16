package com.articleTraceBack.Utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * 登录令牌的 Cookie 读写。
 *
 * <p><b>为什么要放在 Cookie 而不是 localStorage</b>：localStorage 对同源 JS 完全可读，
 * 一旦出现 XSS（哪怕只是某个第三方脚本被投毒），token 立刻被偷走，攻击者可以拿着它冒充本人。
 * HttpOnly 的 Cookie 拿不到 {@code document.cookie}，脚本偷不走。</p>
 *
 * <p><b>代价是引入了 CSRF</b>：Cookie 由浏览器自动携带，攻击者站点发起的请求也会带上它。
 * 用 {@code SameSite=Lax} 挡住——它规定跨站请求不带 Cookie，除非是顶层导航的 GET；
 * 而本项目所有写操作都是 POST / PATCH / DELETE，因此这一条就够，无需再维护 CSRF token。</p>
 */
public final class CookieUtil {

    /** 令牌 Cookie 名；带项目前缀，避免同域下其它应用重名 */
    public static final String TOKEN_COOKIE = "article_trace_token";

    private CookieUtil() {
    }

    /**
     * 写入登录令牌。
     *
     * @param maxAgeSeconds 有效期（秒），应与 Redis 里 token 的 TTL 一致
     * @param secure        是否只在 HTTPS 下发送；本地 http 调试必须为 false，否则浏览器不会保存
     */
    public static void writeToken(HttpServletResponse response, String token,
                                  long maxAgeSeconds, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 清除登录令牌。
     *
     * <p>下发一个过期时间为 0 的同名 Cookie，让浏览器立刻丢弃它。</p>
     */
    public static void clearToken(HttpServletResponse response, boolean secure) {
        writeToken(response, "", 0, secure);
    }

    /** 从请求里读登录令牌；没有该 Cookie 时返回 null */
    public static String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
