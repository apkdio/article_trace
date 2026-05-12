package com.articleTraceBack.pojo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterUserPojo {
    @NotEmpty(message = "不能为空！")
    private String username;
    @NotEmpty(message = "不能为空！")
    private String password;
    @NotEmpty(message = "不能为空！")
    private String confirmPassword;
    @NotNull(message = "不能缺失！")
    private String registerPassword;
    @NotNull(message = "不能缺失！")
    private int type;
}
