/**
 * 把后端返回的错误信息取成可直接展示的字符串。
 *
 * 后端出错时 `Result.message` 常常是个**对象**：`GlobalExceptionHandler` 会把错误包成
 * `{error: '…'}`，登录场景还会带 `{captcha, needCaptcha}`、`{blocked}`。而各处写的是
 * `ElMessage.error(res.message || '兜底')` —— 对象丢进去，Element Plus 会渲染成一个
 * **空白弹窗**，出错原因全丢（2026-09-22 审核中心就踩过这个）。
 *
 * ⚠️ 不要改后端把 message 拍平成字符串：登录流程要读 `message.needCaptcha` / `message.blocked`。
 */
export function errorText(message, fallback = '操作失败！') {
    if (!message) return fallback
    if (typeof message === 'string') return message
    return message.error || message.message || fallback
}
