package com.articleTraceBack.Utils;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.*;

@Component
// 请求拦截器
// 窝腰验Token
public class TokenCheck implements AsyncHandlerInterceptor {
    // 规则按 Ant 通配符语义匹配（配置里写 /xxx/**），字面 contains 对带通配符的规则恒不命中
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    @Value("${spring.tokenCheck.notAllowUrl.writer}")
    private String[] writerNotAllow;
    @Value("${spring.tokenCheck.notAllowUrl.reader}")
    private String[] readerNotAllow;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    public TokenCheck(JwtUtil jwtUtil, StringRedisTemplate stringRedisTemplate) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        // 令牌只认 Cookie：它是 HttpOnly 的，脚本拿不到；Authorization 头对同源 JS 完全可见，
        // 留着它就等于给 XSS 留了后门，所以不再兼容旧的头传方式。
        String token = CookieUtil.readToken(request);
        if (uri.matches("/reader/.*")) {
            // 先清除上一次遗留的user信息
            ThreadLocalUtil.remove();
            // 针对 添加评论 、 删除评论  需要登录
            if (!uri.equals("/reader/addComment")
                    && !uri.equals("/reader/deleteComment")) {
                // 避免登录用户的信息被忽略
                try {
                    Map<String, Object> userInfo = jwtUtil.parseToken(token);
                    if (userInfo.get("type") != null) {
                        ThreadLocalUtil.set(userInfo);
                    }
                } catch (Exception ignored) {
                }
                return true;
            }
        }
        if (token == null) {
            response.setStatus(401);
            return false;
        }
        try {
            Map<String, Object> userInfo = jwtUtil.parseToken(token);
            if (userInfo.get("type") == null) {
                response.setStatus(401);
                return false;
            }
            int type = (int) userInfo.get("type");
            // 未知角色一律拒（token 由服务端签发，正常到不了这里）
            if (type != 0 && type != 1 && type != 2) {
                response.setStatus(401);
                return false;
            }
            if (blocked(uri, type, writerNotAllow, readerNotAllow)) {
                response.setStatus(401);
                return false;
            }
            String redisToken = stringRedisTemplate.opsForValue().get(userInfo.get("name").toString());
            if (StringUtils.isBlank(redisToken)) {
                throw new Exception();
            }
            if (redisToken.equals(token)) {
                ThreadLocalUtil.set(userInfo);
                return true;
            }
            throw new Exception();
        } catch (Exception e) {
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 角色拦截判定（纯函数以便矩阵用例直测）：作者只被作者表拦，读者被两张表的并集拦，站长不拦。
     * 前端 router/index.js 的 beforeEach（meta.roles）维护着同一份入口分级——改这里或 notAllowUrl 时
     * 必须同步前端 meta 与 TokenCheckTest 矩阵，三处口径一致，否则出现「页面放行、接口 401」或反向闪跳。
     */
    static boolean blocked(String uri, int type, String[] writerNotAllow, String[] readerNotAllow) {
        if (type == 1) {
            return matchesAny(writerNotAllow, uri);
        }
        if (type == 2) {
            return matchesAny(writerNotAllow, uri) || matchesAny(readerNotAllow, uri);
        }
        return false;
    }

    private static boolean matchesAny(String[] patterns, String uri) {
        for (String pattern : patterns) {
            if (MATCHER.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    // 异步（SSE 等）请求开始并发处理后及时清理，避免 ThreadLocal 随线程回池悬挂
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request,
                                               HttpServletResponse response, Object handler) {
        ThreadLocalUtil.remove();
    }

    // 完成会话后销毁ThreadLocalUtil存储的变量
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response, Object handler, Exception ex) {
        ThreadLocalUtil.remove();
    }
}
