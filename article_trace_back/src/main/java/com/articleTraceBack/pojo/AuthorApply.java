package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作者申请（读者 → 作者）。
 *
 * <p>{@code status}：0 待审 / 1 通过 / 2 拒绝。</p>
 */
@Data
@TableName("author_apply")
public class AuthorApply {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 申请人 user.id */
    private Integer userId;

    /** 申请理由 */
    private String reason;

    /** 0 待审 / 1 通过 / 2 拒绝 */
    private Integer status;

    /** 拒绝原因 */
    private String rejectReason;

    /** 审批人 user.id */
    private Integer reviewUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 联查展示：申请人用户名 */
    @TableField(exist = false)
    private String username;

    /** 联查展示：申请人昵称 */
    @TableField(exist = false)
    private String nickname;
}
