package com.articleTraceBack.pojo;

import lombok.Data;

/**
 * 会话摘要（对应 proto SessionSummary，会话列表项）。
 */
@Data
public class AgentSession {
    private String sessionId;      // 会话 ID（UUID）
    private String title;          // 标题
    private Integer messageCount;  // 消息条数（user + assistant）
    private String updatedAt;      // 最后更新时间
}
