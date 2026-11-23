package com.articleTraceBack.exception;

/** 正文图片超出限额：保存入口抛出，由全局异常处理器转成 content 字段错误。 */
public class BodyImageLimitException extends BusinessException {
    public BodyImageLimitException(String message) {
        super("content", message);
    }
}
