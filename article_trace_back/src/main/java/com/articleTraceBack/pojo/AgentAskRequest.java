package com.articleTraceBack.pojo;

import lombok.Data;

/**
 * 问答接口请求体（对应 proto AskRequest）。
 */
@Data
public class AgentAskRequest {
    private String query;      // 用户问题（必填）
    private String sessionId;  // 会话 ID（可选，多轮上下文）
    private Long categoryId;   // 限定分类检索（可选，0/空 表示不限定）
    private Integer topK;      // 返回文章数（可选，默认由 agent 配置决定）
}
