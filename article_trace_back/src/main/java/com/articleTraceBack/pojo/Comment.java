package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comments")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Integer id;
    @NotNull(message = "不能缺失！")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer articleId;
    @NotNull(message = "不能缺失！")
    private Integer userId;
    @NotEmpty(message = "内容不能为空！")
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String userPic;

    @TableField(exist = false)
    private String nickName;
    @TableField(exist = false)
    private String userPicSrc;
    @TableField(exist = false)
    private String email;
}
