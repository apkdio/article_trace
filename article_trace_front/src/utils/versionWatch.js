/**
 * 文章版本轮询：编辑 / 审核时提前发现「别处已改/已审」，避免到最后提交才发现冲突。
 * 页面不可见时跳过，回到前台立即补一次；回调失败静默（网络抖动不该打扰用户）。
 */
export const ARTICLE_VERSION_POLL_MS = 15000

export function startVersionWatch(task, intervalMs = ARTICLE_VERSION_POLL_MS) {
    let inFlight = false
    const tick = async () => {
        if (document.hidden || inFlight) return
        inFlight = true
        try {
            await task()
        } catch {
            // 轮询失败不提示，下一次继续
        } finally {
            inFlight = false
        }
    }
    const timer = setInterval(tick, intervalMs)
    const onVisibility = () => {
        if (!document.hidden) tick()
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
        clearInterval(timer)
        document.removeEventListener('visibilitychange', onVisibility)
    }
}
