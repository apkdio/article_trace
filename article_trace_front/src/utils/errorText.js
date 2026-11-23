/**
 * 把后端返回的错误信息取成可展示字符串：message 常是对象（{error} / {captcha,needCaptcha} / {blocked}），
 * 直接丢给 ElMessage 会渲染成空白弹窗。
 * ⚠️ 不要改后端把 message 拍平：登录流程要读 message.needCaptcha / message.blocked。
 */
export function errorText(message, fallback = '操作失败！') {
    if (!message) return fallback
    if (typeof message === 'string') return message
    if (typeof message !== 'object') return fallback
    // 字段式错误（{title} / {categoryName} / {nickname}…）没有通用 key 时，取第一个非空字符串
    return message.error || message.message
        || Object.values(message).find(value => typeof value === 'string' && value.trim())
        || fallback
}
