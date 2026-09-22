/**
 * 站点功能开关：/site/features 免登录，界面据此隐藏入口（接口侧另有拒绝，两层都要）。
 * 用原生 fetch 而非 axios：登录页 / 文章页未登录时也会加载，不能被 401 拦截器触发全局登出跳转。
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
