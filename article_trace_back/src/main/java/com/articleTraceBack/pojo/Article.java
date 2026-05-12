package com.articleTraceBack.pojo;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Article {
    @TableId(type = IdType.AUTO)
    private Integer id;//主键ID

    @NotNull(message = "不能为空！")
    @NotEmpty
    @Pattern(regexp = "^\\S{1,30}$", message = "为1-30个非空白字符！")
    private String title;//文章标题

    @NotNull(message = "不能为空！")
    @NotEmpty
    private String content;//文章内容
    private String coverImg;//封面图像

    @NotNull(message = "不能为空！")
    private Integer state;//发布状态 驳回 3 | 待审核 2 | 已发布1 | 草稿 0

    @NotNull(message = "不能为空！")
    private Integer categoryId;//文章分类id

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer createUser;//创建人ID

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;//创建时间

    private Long views;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;//更新时间

    @TableField(exist = false)
    private String createUserName;// 链接创建用户的名称

    @TableField(exist = false)
    private String categoryName;//链接文章种类名

    @TableField(exist = false)
    private String coverImgSrc;
}
