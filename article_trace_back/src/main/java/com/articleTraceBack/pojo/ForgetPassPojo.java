package com.articleTraceBack.pojo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 找回密码请求体（邮箱 + 验证码方式）。
 */
@Data
public class ForgetPassPojo {

    @NotEmpty(message = "不能为空！")
    private String email;

    @NotEmpty(message = "不能为空！")
    private String emailCode;

    @NotEmpty(message = "不能为空！")
    @Pattern(regexp = "^\\S{1,72}$", message = "长度不符合！")
    private String password;

    @NotEmpty(message = "不能为空！")
    @Pattern(regexp = "^\\S{1,72}$", message = "长度不符合！")
    private String confirmPassword;
}
