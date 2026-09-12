package com.articleTraceBack.pojo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 注册请求体。
 *
 * <p>注册一律创建为读者（type=2）；「成为作者」走独立的申请-审批流程。</p>
 */
@Data
public class RegisterUserPojo {
    @NotEmpty(message = "不能为空！")
    private String username;
    @NotEmpty(message = "不能为空！")
    private String password;
    @NotEmpty(message = "不能为空！")
    private String confirmPassword;
    @NotEmpty(message = "不能为空！")
    private String email;
    @NotEmpty(message = "不能为空！")
    private String emailCode;
}
