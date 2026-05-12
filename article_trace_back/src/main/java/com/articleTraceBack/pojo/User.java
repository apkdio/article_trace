package com.articleTraceBack.pojo;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @NotNull
    @TableId(type = IdType.AUTO) // 自增策略使用数据库自增
    private Integer id; // 用户ID

    @NotEmpty(groups = {login.class,update.class})
    @Pattern(regexp = "^\\S{1,15}$", message = "为1-15个非空白字符！")
    private String username; // 用户昵称

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    // 仅在Java 对象 -> Json 返回前端时不写入 反过来写入该字段
    @Pattern(regexp = "^\\S{1,72}$", message = "为1-72个非空白字符！")
    @NotEmpty(groups = login.class)
    private String password; // 用户密码

    @TableField("reset_pass")
    @JsonIgnore
    private String resetPass; // 忘记密码时的校验密码

    @NotEmpty
    @Pattern(regexp = "^\\S{1,10}$", message = "为1-10个非空白字符！")
    private String nickname; // 别名

    @NotEmpty
    @Email(message = "不是一个邮箱格式！")
    private String email; // 邮箱

    @TableField("user_pic")
    private String userPic; // 头像

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime; // 创建日期

    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime; // 更新日期

    @TableField("last_login")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLogin; // 最后登录时间

    @NotNull
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private int type; //用户类型 0-站长 1-作者 2-读者

    @TableField(exist = false)
    private int articlesTotal; //发表文章数

    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private int rememberMe;

    @TableField(exist = false)
    private String userPicSrc;

    public interface login {
    }
    public interface update{}
}
