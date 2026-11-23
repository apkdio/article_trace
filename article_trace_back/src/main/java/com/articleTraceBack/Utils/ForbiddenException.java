package com.articleTraceBack.Utils;

/** 已登录但当前角色不允许访问该接口：回 403，前端只提示，不登出也不清草稿。 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException() {
        super("无权访问！");
    }
}
