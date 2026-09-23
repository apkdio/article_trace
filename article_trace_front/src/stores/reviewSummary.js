import {defineStore} from "pinia";
import {ref} from "vue";
import {getReviewSummary} from "@/api/review.js";

/**
 * 审核中心待办数（五类合计 + 分类型），菜单角标与页面左侧列表共用同一份。
 * 处置成功后 emit('handled') → refresh()，两处同步刷新；刻意不做持久化（服务端状态快照）。
 */
export const reviewSummaryStore = defineStore("reviewSummary", () => {
    const counts = ref({article: 0, avatar: 0, authorApply: 0, report: 0})

    /** 拉一次最新计数；失败不抛，保持上一次的值（角标不该因为一次网络抖动清空） */
    async function refresh() {
        try {
            const res = await getReviewSummary()
            if (res.code === 0 && res.data) {
                counts.value = res.data
            }
        } catch (err) {
            // 拿不到就维持原值
        }
    }

    /** 五类合计，菜单角标用 */
    function total() {
        return Object.values(counts.value).reduce((sum, n) => sum + (n || 0), 0)
    }

    return {counts, refresh, total}
})
