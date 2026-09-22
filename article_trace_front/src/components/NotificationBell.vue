<script setup>
/**
 * 站内通知铃铛 + 抽屉，后台与门户两处布局共用（避免轮询逻辑复制两份后不一致）。
 * 铃铛外观由父组件通过作用域插槽（open / unread）决定，不传则用默认图标样式。
 */
import {ref, onMounted, onUnmounted} from 'vue'
import {Bell} from '@element-plus/icons-vue'
import {
    deleteNotification,
    listNotifications,
    markAllRead,
    markRead as markReadApi,
    unreadCount
} from "@/api/notification.js";

const notifyDrawer = ref(false)
const notifyList = ref([])
const notifyTotal = ref(0)
const notifyPage = ref(1)
const notifyPageSize = ref(10)
const unread = ref(0)
const notifyLoading = ref(false)
// 类型筛选：all / system / apply / avatar / report
const notifyType = ref('all')

// 实时提醒：定时轮询未读数，出现新增时轻提示（首次加载不提示）
const POLL_INTERVAL_MS = 60 * 1000
let pollTimer = null
let unreadInitialized = false

const loadUnread = async ({notifyOnIncrease = false} = {}) => {
    try {
        const res = await unreadCount()
        if (res.code !== 0) return
        const next = res.data || 0
        if (notifyOnIncrease && unreadInitialized && next > unread.value) {
            ElMessage({
                message: `你有 ${next - unread.value} 条新的站内通知`,
                type: 'info',
                duration: 4000
            })
        }
        unreadInitialized = true
        unread.value = next
    } catch (e) {
        // 未读数获取失败不影响主流程
    }
}

const loadNotify = async () => {
    notifyLoading.value = true
    try {
        const res = await listNotifications(notifyType.value, notifyPage.value, notifyPageSize.value)
        if (res.code === 0) {
            notifyList.value = res.data?.items || []
            notifyTotal.value = res.data?.total || 0
        }
    } catch (e) {
        ElMessage.error('加载通知失败！')
    } finally {
        notifyLoading.value = false
    }
}

const openNotify = () => {
    notifyDrawer.value = true
    notifyPage.value = 1
    loadNotify()
    // 打开抽屉时同步一次未读数，避免角标与列表不一致
    loadUnread()
}

const switchType = () => {
    notifyPage.value = 1
    loadNotify()
}

const removeNotify = async (item) => {
    try {
        const res = await deleteNotification(item.id)
        if (res.code !== 0) {
            ElMessage.error(res.message || '删除失败！')
            return
        }
        if (item.isRead === 0) unread.value = Math.max(0, unread.value - 1)
        // 删掉当前页最后一条时回退一页，避免停在空页
        if (notifyList.value.length === 1 && notifyPage.value > 1) {
            notifyPage.value -= 1
        }
        await loadNotify()
    } catch (e) {
        ElMessage.error('删除失败！')
    }
}

const readOne = async (item) => {
    if (item.isRead === 1) return
    try {
        const res = await markReadApi(item.id)
        if (res.code === 0) {
            item.isRead = 1
            unread.value = Math.max(0, unread.value - 1)
        }
    } catch (e) {
        // 忽略
    }
}

const readAll = async () => {
    try {
        const res = await markAllRead()
        if (res.code === 0) {
            notifyList.value.forEach(i => i.isRead = 1)
            unread.value = 0
            ElMessage.success('已全部标记为已读')
        }
    } catch (e) {
        ElMessage.error('操作失败！')
    }
}

onMounted(() => {
    loadUnread()
    pollTimer = setInterval(() => loadUnread({notifyOnIncrease: true}), POLL_INTERVAL_MS)
})

onUnmounted(() => {
    if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
    }
})

defineExpose({openNotify, unread})
</script>

<template>
    <el-badge :value="unread" :max="99" :hidden="unread === 0" class="notify-badge">
        <slot :open="openNotify" :unread="unread">
            <el-icon class="notify-bell" :size="20" @click="openNotify">
                <Bell/>
            </el-icon>
        </slot>
    </el-badge>

    <el-drawer v-model="notifyDrawer" title="站内通知" size="380px" class="notify-drawer">
        <div class="notify-toolbar">
            <el-radio-group v-model="notifyType" size="small" @change="switchType">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="system">系统</el-radio-button>
                <el-radio-button value="apply">作者申请</el-radio-button>
                <el-radio-button value="avatar">头像审核</el-radio-button>
                <el-radio-button value="report">举报</el-radio-button>
            </el-radio-group>
            <el-button size="small" text type="primary" :disabled="unread === 0" @click="readAll">
                全部已读
            </el-button>
        </div>
        <div v-loading="notifyLoading" class="notify-list">
            <div v-if="!notifyLoading && notifyList.length === 0" class="notify-empty">暂无通知</div>
            <div v-for="item in notifyList" :key="item.id"
                 :class="['notify-item', {unread: item.isRead === 0}]"
                 @click="readOne(item)">
                <div class="notify-title">
                    <span v-if="item.isRead === 0" class="notify-dot"></span>{{ item.title }}
                </div>
                <div class="notify-content">{{ item.content }}</div>
                <div class="notify-time">
                    <span>{{ item.createTime }}</span>
                    <el-button size="small" text type="danger" @click.stop="removeNotify(item)">
                        删除
                    </el-button>
                </div>
            </div>
        </div>
        <div v-if="notifyTotal > notifyPageSize" class="notify-pager">
            <el-pagination
                v-model:current-page="notifyPage"
                :page-size="notifyPageSize"
                :total="notifyTotal"
                layout="prev, pager, next"
                small
                @current-change="loadNotify"
            />
        </div>
    </el-drawer>
</template>

<style lang="scss" scoped>
/* 注意：这里不给 .notify-badge 加外边距——间距由各页面按自己的布局决定 */
.notify-badge {
    cursor: pointer;

    .notify-bell {
        color: #5c6b77;
        transition: color 0.2s;
        display: block;

        &:hover {
            color: #409eff;
        }
    }
}
</style>

<!-- 抽屉内容会 teleport 到 body，scoped 样式无法命中，故用独立 class 限定 -->
<style lang="scss">
.notify-drawer {
    .notify-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 8px;
    }

    .notify-list {
        min-height: 120px;
    }

    .notify-empty {
        padding: 40px 0;
        text-align: center;
        font-size: 13px;
        color: #909399;
    }

    .notify-item {
        padding: 10px 12px;
        border-radius: 8px;
        border-bottom: 1px solid #f2f3f5;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
            background: #f5f7fa;
        }

        &.unread .notify-title {
            font-weight: 600;
        }

        .notify-title {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 14px;
            color: #303133;
        }

        .notify-dot {
            flex-shrink: 0;
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #f56c6c;
        }

        .notify-content {
            margin-top: 4px;
            font-size: 13px;
            line-height: 1.5;
            color: #606266;
            word-break: break-word;
        }

        .notify-time {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 4px;
            font-size: 12px;
            color: #a8abb2;
        }
    }

    .notify-pager {
        display: flex;
        justify-content: center;
        margin-top: 12px;
    }
}
</style>
