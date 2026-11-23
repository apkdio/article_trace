package com.articleTraceBack.Utils;

import com.articleTraceBack.exception.BodyImageLimitException;
import com.articleTraceBack.exception.BusinessException;
import com.articleTraceBack.exception.ForbiddenException;
import com.articleTraceBack.exception.NotLoggedInException;
import com.articleTraceBack.exception.SessionExpiredException;
import com.articleTraceBack.pojo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 业务异常到返回体的唯一出口：字段名决定 key，缺省落 error。 */
public class BusinessExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    public void fieldIsUsedAsKey() {
        Result<Map<String, Object>> result =
                handler.handleBusiness(new BusinessException("title", "你已写过同名文章！"));
        assertEquals(1, result.getCode());
        assertEquals(Map.of("title", "你已写过同名文章！"), result.getMessage());
        assertNull(result.getData());
    }

    @Test
    public void missingFieldFallsBackToError() {
        Result<Map<String, Object>> result = handler.handleBusiness(new BusinessException("删除失败"));
        assertEquals(Map.of("error", "删除失败"), result.getMessage());
    }

    @Test
    public void bodyImageLimitKeepsContentField() {
        Result<Map<String, Object>> result =
                handler.handleBusiness(new BodyImageLimitException("正文最多 15 张图片"));
        assertEquals(Map.of("content", "正文最多 15 张图片"), result.getMessage());
    }

    @Test
    public void payloadIsMergedWithFieldMessage() {
        Result<Map<String, Object>> result = handler.handleBusiness(
                new BusinessException("password", "密码错误！", Map.of("needCaptcha", true, "remaining", 2)));
        assertEquals(Map.of("password", "密码错误！", "needCaptcha", true, "remaining", 2),
                result.getMessage());
    }

    @Test
    public void notLoggedInIs401WithExpiredFalse() {
        ResponseEntity<Result<Map<String, Object>>> response = handler.handleNotLoggedIn(new NotLoggedInException());
        assertEquals(401, response.getStatusCode().value());
        assertEquals(Map.of("error", "未登录！", "expired", false), response.getBody().getMessage());
    }

    @Test
    public void sessionExpiredIs401WithExpiredTrue() {
        ResponseEntity<Result<Map<String, Object>>> response = handler.handleSessionExpired(new SessionExpiredException());
        assertEquals(401, response.getStatusCode().value());
        assertEquals(Map.of("error", "登录已过期，请重新登录", "expired", true), response.getBody().getMessage());
    }

    @Test
    public void forbiddenIs403() {
        ResponseEntity<Result<Map<String, Object>>> response = handler.handleForbidden(new ForbiddenException());
        assertEquals(403, response.getStatusCode().value());
        assertEquals(Map.of("error", "无权访问！"), response.getBody().getMessage());
    }
}
