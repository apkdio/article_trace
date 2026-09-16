/**
 * 会话失效标记。
 *
 * 令牌本身放在 HttpOnly Cookie 里（前端脚本读不到也不需要读），所以这里只记
 * 「401 已经处理过了」这一个状态：并发请求会同时拿到 401，若不拦一下，用户会被
 * 弹一串「登录已失效」并连续跳转多次。
 *
 * 刻意用模块级变量而不是 pinia + persist：它必须随页面刷新重置，否则刷新后
 * 第一次 401 会因为「已处理」而静默失败。
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
