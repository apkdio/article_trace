/**
 * 站点功能开关。
 *
 * 后端 `/site/features` 免登录，值由配置决定：本地开发全开（多用户态），
 * 线上做个人备案时全关（单用户态——不开放注册、不能新增评论、不能申请作者）。
 * 界面按它隐藏入口；接口那边还有一道拒绝，两层都在才对。
 *
 * 用原生 fetch 而不是 axios 实例：登录页和文章页在未登录时也会加载，
 * axios 拦截器碰到 401 会触发全局登出跳转，那不该发生在这两个场景。
 */
let cached = null

/** 探测失败或字段缺失时的兜底：当作全开 */
const ALL_ON = {
    registerEnabled: true,
    commentEnabled: true,
    authorApplyEnabled: true,
    logoEnabled: true,
    displayName: '文迹',
}

export async function getSiteFeatures() {
    if (cached) {
        return cached
    }
    try {
        const resp = await fetch('/api/site/features')
        if (!resp.ok) {
            // 不缓存失败结果：下次进页面还能再试一次
            return ALL_ON
        }
        const body = await resp.json()
        cached = (body && body.code === 0 && body.data) ? {...ALL_ON, ...body.data} : ALL_ON
    } catch (e) {
        // 探测不了就按全开处理：宁可多显示一个入口（后端还会拒），
        // 也不要因为一次网络抖动把本地开发的功能全藏起来
        return ALL_ON
    }
    return cached
}
