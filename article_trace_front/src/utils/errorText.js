/**
 * 把后端返回的错误信息取成可展示字符串：message 常是对象（{error} / {captcha,needCaptcha} / {blocked}），
 * 直接丢给 ElMessage 会渲染成空白弹窗。
 * ⚠️ 不要改后端把 message 拍平：登录流程要读 message.needCaptcha / message.blocked。
 */
export function errorText(message, fallback = '操作失败！') {
    if (!message) return fallback
    if (typeof message === 'string') return message
    return message.error || message.message || fallback
}
