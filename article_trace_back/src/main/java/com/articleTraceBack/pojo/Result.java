package com.articleTraceBack.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Result<T> {
    private Integer code; //业务状态码  0:成功  1:失败
    private Object message; //提示信息
    private T data; //响应数据

    /** 成功响应（带数据） */
    public static <E> Result<E> success(E data) {
        return new Result<>(0, "操作成功", data);
    }

    /** 成功响应（无数据） */
    public static Result<String> success() {
        return new Result<>(0, "操作成功", null);
    }
    /** 失败响应 */
    public static <E> Result<E> error(Object message) {
        return new Result<>(1, message, null);
    }
}
