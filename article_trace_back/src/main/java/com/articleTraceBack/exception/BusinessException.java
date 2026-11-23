package com.articleTraceBack.exception;

import java.util.Map;

/**
 * 预期内的业务失败：服务端只抛它，由 {@link GlobalExceptionHandler} 统一转成 {@code Result.error}。
 * {@code field} 决定错误挂在哪个字段上（title / cover / content / error…），为空时落 {@code error}；
 * {@code payload} 用于附带前端还要读的元信息（登录的 needCaptcha / remaining / blocked）。
 */
public class BusinessException extends RuntimeException {
    private final String field;
    private final Map<String, Object> payload;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        this(field, message, Map.of());
    }

    public BusinessException(String field, String message, Map<String, Object> payload) {
        super(message);
        this.field = field;
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public String field() {
        return field;
    }

    public Map<String, Object> payload() {
        return payload;
    }
}
