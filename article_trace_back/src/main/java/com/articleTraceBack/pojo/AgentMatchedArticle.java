package com.articleTraceBack.pojo;

import lombok.Data;

/**
 * 问答命中的文章（对应 proto MatchedArticle，不含正文）。
 */
@Data
public class AgentMatchedArticle {
    private Long id;
    private String title;
    private String categoryName;
    private String snippet;   // 命中片段摘要
    private double score;     // 相关度分数（RRF 归一化，仅作排序参考）
}
