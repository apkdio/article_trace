package com.articleTraceBack.rpc;

import com.articleTraceBack.rpc.gen.Article;
import com.articleTraceBack.rpc.gen.ArticleState;

import java.time.format.DateTimeFormatter;

/**
 * Java 侧 Article 实体 ↔ proto Article 消息的转换器。
 * 契约约定的时间格式为 "yyyy-MM-dd HH:mm:ss"。
 */
public final class ArticleProtoMapper {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ArticleProtoMapper() {
    }

    /**
     * 将 Java 侧文章实体转换为 proto Article。
     *
     * @param pojo         Java 侧文章实体
     * @param content      正文（纯文本或 HTML，agent 侧负责清洗）
     * @param author       作者昵称（可空）
     * @param categoryName 分类名（可空）
     * @param coverUrl     封面可访问 URL（可空）
     */
    public static Article toProto(com.articleTraceBack.pojo.Article pojo,
                                  String content,
                                  String author,
                                  String categoryName,
                                  String coverUrl) {
        Article.Builder builder = Article.newBuilder()
                .setId(pojo.getId())
                .setTitle(pojo.getTitle() == null ? "" : pojo.getTitle())
                .setState(toState(pojo.getState()))
                .setCategoryId(pojo.getCategoryId() == null ? 0 : pojo.getCategoryId())
                .setViews(pojo.getViews() == null ? 0 : pojo.getViews());

        if (content != null) {
            builder.setContent(content);
        }
        if (categoryName != null) {
            builder.setCategoryName(categoryName);
        }
        if (coverUrl != null) {
            builder.setCoverUrl(coverUrl);
        }
        if (author != null) {
            builder.setAuthor(author);
        }
        if (pojo.getCreateTime() != null) {
            builder.setCreateTime(pojo.getCreateTime().format(FORMATTER));
        }
        if (pojo.getUpdateTime() != null) {
            builder.setUpdateTime(pojo.getUpdateTime().format(FORMATTER));
        }
        return builder.build();
    }

    /** Java 侧 state 值 → proto 枚举 */
    private static ArticleState toState(Integer state) {
        if (state == null) {
            return ArticleState.DRAFT;
        }
        ArticleState s = ArticleState.forNumber(state);
        return s != null ? s : ArticleState.DRAFT;
    }
}
