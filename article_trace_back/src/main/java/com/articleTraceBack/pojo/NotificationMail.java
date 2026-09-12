package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件投递记录（P2 使用：失败可重试、可追溯）。
 */
@Data
@TableName("notification_mail")
public class NotificationMail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toEmail;

    private String subject;

    private String content;

    /** pending / sent / failed */
    private String status;

    private Integer retryCount;

    private String error;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentTime;
}
