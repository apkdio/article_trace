package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报记录。{@code targetType + targetId} 必须成对使用（三张表 id 各自自增）。
 * {@code status} 只有 0 待处理 / 1 已处置 / 2 已驳回，不设中间态；处置动作仍走各自原有接口。
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

    /** 联查展示：被举报对象的父级 id（目前仅评论使用，即所属文章 id），供站长处置时复用 {@code /reader/deleteComment}。 */
    @TableField(exist = false)
    private Integer targetParentId;
}
