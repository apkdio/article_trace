import {defineStore} from "pinia";
import {ref} from "vue";
import {getReviewSummary} from "@/api/review.js";

/**
 * 审核中心待办数（四类合计 + 分类型）。
 *
 * 菜单角标与审核中心页左侧列表读的是**同一份**计数：处置动作发生在页面内部，
 * 不共享状态的话，菜单那一层只能靠轮询去追（早期就是拉一次 + 60 秒轮询，
 * 结果处置完红点还挂着）。现在面板处置成功 → 各自 emit('handled') → 调 refresh()，
 * 两处同时掉。
 *
 * 刻意**不做持久化**：它是服务端状态的快照，刷新后重新拉即可；
 * 持久化只会让下次打开先显示一个过期的数字。
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

    /** 四类合计，菜单角标用 */
    function total() {
        return Object.values(counts.value).reduce((sum, n) => sum + (n || 0), 0)
    }

    return {counts, refresh, total}
})
