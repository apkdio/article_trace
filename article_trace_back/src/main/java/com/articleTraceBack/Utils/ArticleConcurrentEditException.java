package com.articleTraceBack.Utils;

/** 同一篇文章被别人先改了：条件更新影响 0 行时抛出，由全局处理器转成「请刷新后重试」这类明确提示。 */
public class ArticleConcurrentEditException extends BusinessException {
    public ArticleConcurrentEditException(String message) {
        super("error", message);
    }
}
