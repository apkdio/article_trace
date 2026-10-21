package com.articleTraceBack.pojo;

/**
 * 文章写路径的入口快照：并发判断只认 {@code version}，
 * 状态用于审核的业务校验，正文与封面对象名用于写库成功后清理旧文件。
 */
public record ArticleSnapshot(Integer state, String contentKey, String coverKey, Long version) {
}
