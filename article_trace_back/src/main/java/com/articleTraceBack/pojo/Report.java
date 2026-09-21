package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报记录。
 *
 * <p>举报对象是三种表之一，所以 {@code targetType + targetId} 必须成对使用——
 * 三张表的 id 各自自增，单看 id 分不清指的是文章还是评论。</p>
 *
 * <p>{@code status} 只有三档：0 待处理 / 1 已处置 / 2 已驳回。**不设「受理中」**——
 * 举报的处理动作只有「看完了，确实违规」与「看完了，不违规」两种，
 * 中间态没有对应的动作，加上去只会让站长多点一次按钮。</p>
 *
 * <p>处置本身**不在这里做**：站长点「删除该评论」走的是 {@code /reader/deleteComment}、
 * 「下架该文章」走的是 {@code /article/assess}，本记录只负责记「谁报了谁、报了没有」。</p>
 */
@Data
@TableName("report")
public class Report {

    /** 待处理 */
    public static final int STATUS_PENDING = 0;
    /** 已处置（认定违规） */
    public static final int STATUS_HANDLED = 1;
    /** 已驳回举报（未认定违规） */
    public static final int STATUS_REJECTED = 2;

    /** 举报对象：文章 */
    public static final String TARGET_ARTICLE = "article";
    /** 举报对象：评论 */
    public static final String TARGET_COMMENT = "comment";
    /** 举报对象：用户 */
    public static final String TARGET_USER = "user";

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 举报人 user.id */
    private Integer reporterId;

    /** article / comment / user */
    private String targetType;

    /** 举报对象 id，与 targetType 一起确定唯一对象 */
    private Integer targetId;

    /** 举报理由 */
    private String reason;

    /** 0 待处理 / 1 已处置 / 2 已驳回 */
    private Integer status;

    /** 处置人 user.id */
    private Integer handleUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 联查展示：举报人用户名 */
    @TableField(exist = false)
    private String reporterUsername;

    /** 联查展示：举报人昵称 */
    @TableField(exist = false)
    private String reporterNickname;

    /** 联查展示：处置人用户名 */
    @TableField(exist = false)
    private String handleUsername;

    /** 联查展示：被举报对象的摘要（文章标题 / 评论正文 / 用户昵称） */
    @TableField(exist = false)
    private String targetSummary;

    /**
     * 联查展示：被举报对象的父级 id（目前只有评论用得上——它所属的文章 id）。
     *
     * <p>评论没有自己的页面，站长处置时要么跳到文章下看，要么复用
     * {@code /reader/deleteComment} 删掉，而那个接口要求 articleId，所以这里带出来。</p>
     */
    @TableField(exist = false)
    private Integer targetParentId;
}
