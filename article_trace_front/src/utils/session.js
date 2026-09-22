/**
 * 会话失效标记：令牌在 HttpOnly Cookie 里，这里只记「401 已处理」，避免并发 401 弹一串提示并连续跳转。
 * 刻意用模块级变量而非 pinia persist：必须随刷新重置，否则刷新后首次 401 会被静默吞掉。
 */
let handled = false

export function isAuthHandled() {
    return handled
}

export function markAuthHandled() {
    handled = true
}

/** 登录成功后调用，让下一次会话失效还能正常提示 */
export function resetAuthHandled() {
    handled = false
}
