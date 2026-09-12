package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信。
 *
 * <p>{@code senderId}：-1 系统消息；正整数为用户 ID；NULL 表示发送方已注销。
 * <br>{@code receiverId}：用户 ID；NULL 表示接收方已注销。</p>
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String title;

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
