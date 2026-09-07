package com.articleTraceBack.pojo;

import lombok.Data;

import java.util.List;

/**
 * 问答接口返回结果（对应 proto AskReply）。
 */
@Data
public class AgentAskResult {
    private boolean ok;                              // 是否成功
    private String answer;                           // LLM 生成的自然语言答案
    private List<AgentMatchedArticle> articles;      // 命中的文章列表（按相关度降序）
    private String message;                          // 错误信息（ok=false 时）
}
