package com.articleTraceBack.Utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Component
public class IPUtil {
    /** 取客户端真实 IP（兼容反向代理） */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * IP + User-Agent 的混合指纹（md5），用于按客户端维度限流与失败计数。
     *
     * <p>⚠️ 上面这些头都由客户端提供、可任意伪造（线上经 frp 隧道时 nginx 还是追加式写入，
     * 最左值仍可伪造），所以这枚指纹只是**辅助**维度：防爆破靠 {@code LoginAttemptService}
     * 的账号维度，依赖它的日志 / 统计也不能当成可信事实。</p>
     */
    public static String mixOf(HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "unknown";
        }
        return DigestUtils.md5DigestAsHex((ip + "/" + userAgent).getBytes(StandardCharsets.UTF_8));
    }
}