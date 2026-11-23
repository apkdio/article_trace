package com.articleTraceBack.Utils;

/**
 * 预期内的业务失败：服务端只抛它，由 {@link GlobalExceptionHandler} 统一转成 {@code Result.error}。
 * {@code field} 决定错误挂在哪个字段上（title / cover / content / error…），为空时落 {@code error}。
 */
public class BusinessException extends RuntimeException {
    private final String field;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
