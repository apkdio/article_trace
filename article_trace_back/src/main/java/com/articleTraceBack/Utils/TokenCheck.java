package com.articleTraceBack.Utils;

import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.*;

@Component
// 请求拦截器
// 窝腰验Token
public class TokenCheck implements AsyncHandlerInterceptor {
    @Value("${spring.tokenCheck.notAllowUrl.writer}")
    private String[] writerNotAllow;
    @Value("${spring.tokenCheck.notAllowUrl.reader}")
    private String[] readerNotAllow;
    private String[] mergedReaderNotAllow;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        // 去重合并
        Set<String> set = new LinkedHashSet<>(Arrays.asList(writerNotAllow));
        Collections.addAll(set, readerNotAllow);
        mergedReaderNotAllow = set.toArray(new String[0]);
    }

    public TokenCheck(JwtUtil jwtUtil, StringRedisTemplate stringRedisTemplate) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String token = request.getHeader("Authorization");
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
            if (type == 1) {
                for (String url : mergedReaderNotAllow) {
                    if (request.getRequestURI().contains(url)) {
                        response.setStatus(401);
                        return false;
                    }
                }
            } else if (type == 2) {
                for (String url : readerNotAllow) {
                    if (request.getRequestURI().contains(url)) {
                        response.setStatus(401);
                        return false;
                    }
                }
            } else if (type != 0) return false;
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
