package com.articleTraceBack.Utils;

/** 受保护接口取不到当前用户时抛出；由 GlobalExceptionHandler 统一转成 401。 */
public class NotLoggedInException extends RuntimeException {
    public NotLoggedInException() {
        super("未登录！");
    }
}
