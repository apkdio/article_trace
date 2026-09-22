package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 头像审核记录。{@code status}：0 待审 / 1 通过 / 2 拒绝；待审头像存 avatar 桶，通过前不写 {@code user.user_pic}。 */
@Data
@TableName("avatar_apply")
public class AvatarApply {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 申请人 user.id */
    private Integer userId;

    /** 待审头像对象名（存 avatar 桶） */
    private String pendingPic;

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

    /** 联查展示：申请人昵称 */
    @TableField(exist = false)
    private String nickname;

    /** 待审头像访问链接（原图） */
    @TableField(exist = false)
    private String pendingPicSrc;

    /** 待审头像访问链接（缩略图） */
    @TableField(exist = false)
    private String pendingPicThumbSrc;
}
