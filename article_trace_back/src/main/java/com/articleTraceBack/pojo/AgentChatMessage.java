package com.articleTraceBack.pojo;

import lombok.Data;

/**
 * 会话消息（对应 proto ChatMessage）。
 */
@Data
public class AgentChatMessage {
    private String role;      // user / assistant
    private String content;   // 消息内容
    private String ts;        // 时间戳
}
