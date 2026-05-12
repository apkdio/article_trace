package com.articleTraceBack.pojo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdatePassPojo {
    @NotNull
    @NotEmpty
    String oriPass;

    @NotNull
    @NotEmpty
    @Pattern(regexp = "^\\S{1,72}$",message = "长度不符合！")
    String newPass;

    @NotNull
    @NotEmpty
    @Pattern(regexp = "^\\S{1,72}$",message = "长度不符合！")
    String confirmPass;
}
