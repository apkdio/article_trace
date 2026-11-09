package com.articleTraceBack.pojo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 注册请求体。注册一律创建为读者（type=2），成为作者走独立的申请-审批流程。 */
@Data
public class RegisterUserPojo {
    @NotEmpty(message = "不能为空！")
    @Pattern(regexp = "^\\S{1,15}$", message = "为1-15个非空白字符！")
    private String username;
    @NotEmpty(message = "不能为空！")
    @Pattern(regexp = "^\\S{1,72}$", message = "为1-72个非空白字符！")
    private String password;
    @NotEmpty(message = "不能为空！")
    @Pattern(regexp = "^\\S{1,72}$", message = "为1-72个非空白字符！")
    private String confirmPassword;
    @NotEmpty(message = "不能为空！")
    private String email;
    @NotEmpty(message = "不能为空！")
    private String emailCode;
}
