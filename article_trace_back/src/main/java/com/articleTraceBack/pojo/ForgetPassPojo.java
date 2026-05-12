package com.articleTraceBack.pojo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ForgetPassPojo {
    @NotEmpty
    @NotNull
    private String username;

    @NotNull
    @NotEmpty
    private String resetPassword;

    @NotNull
    @NotEmpty
    @Pattern(regexp = "^\\S{1,72}$",message = "长度不符合！")
    private String password;

    @NotNull
    @NotEmpty
    @Pattern(regexp = "^\\S{1,72}$",message = "长度不符合！")
    private String confirmPassword;
}
