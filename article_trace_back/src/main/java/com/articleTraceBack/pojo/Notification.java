package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内信。{@code senderId}：-1 系统消息 / 正整数为用户 ID / NULL 表示发送方已注销；{@code receiverId} 为 NULL 表示接收方已注销。 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 类型：系统消息 */
    public static final String TYPE_SYSTEM = "system";
    /** 类型：审核类——头像 / 作者申请 / 资料（昵称、个签）都归这里 */
    public static final String TYPE_AUDIT = "audit";
    /** 类型：举报相关 */
    public static final String TYPE_REPORT = "report";

    private String title;

    /** system-系统 / audit-审核 / report-举报 */
    @TableField("`type`")
    private String type;

    /** -1 = 系统消息；NULL = 发送方已注销 */
    private Integer senderId;

    /** NULL = 接收方已注销 */
    private Integer receiverId;

    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 0 未读 / 1 已读 */
    @TableField("is_read")
    private Integer isRead;
}
