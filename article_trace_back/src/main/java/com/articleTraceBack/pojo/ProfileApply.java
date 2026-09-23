package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 昵称 / 个签审核记录。{@code status}：0 待审 / 1 通过 / 2 拒绝；待审期间 user 上的旧值照常生效，通过后才写回。 */
@Data
@TableName("profile_apply")
public class ProfileApply {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    /** 资料类型：昵称 */
    public static final int TYPE_NICKNAME = 1;
    /** 资料类型：个签 */
    public static final int TYPE_SIGNATURE = 2;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 申请人 user.id */
    private Integer userId;

    /** 1 昵称 / 2 个签 */
    private Integer type;

    /** 待审的昵称 / 个签文本 */
    private String pendingValue;

    /** 0 待审 / 1 通过 / 2 拒绝 */
    private Integer status;

    /** 拒绝理由 */
    private String rejectReason;

    /** 审核人 user.id */
    private Integer reviewUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    /** 提交时间（审核列表展示用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 联查展示：申请人用户名 */
    @TableField(exist = false)
    private String username;
}
