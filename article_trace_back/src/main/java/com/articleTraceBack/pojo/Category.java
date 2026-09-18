package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Category {
    @TableId(type = IdType.AUTO)
    private Integer id;//主键ID

    @NotBlank(message = "分类名称不能为空！")
    @Size(max = 20, message = "分类名称长度不能超过 20 个字符！")
    @Pattern(regexp = "^\\S(.*\\S)?$", message = "分类名称首尾不能是空格！")
    private String categoryName;//分类名称

    @NotNull(message = "分类别名不能为空！")
    @Size(max = 30, message = "分类别名长度不能超过 30 个字符！")
    private String categoryAlias;//分类别名

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer createUser;//创建人ID

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer lastUpdateUser;//最后更新用户ID

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;//创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;//更新时间

    @TableField(exist = false)
    private String createUserName; // 链接创建用户的名称

    @TableField(exist = false)
    private String lastUpdateUserName; // 链接最后更新用户的名称
}
