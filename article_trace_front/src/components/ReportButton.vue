<script setup>
import {ref} from 'vue'
import {ElMessage} from 'element-plus'
import {Warning} from '@element-plus/icons-vue'
import {submitReport} from "@/api/report.js"
import {userInfoStore} from "@/stores/userInfo.js";
import router from "@/router/index.js";

/**
 * 举报入口（按钮 + 理由弹窗）。
 *
 * 三处调用方（文章、评论、作者卡）的目标类型不同、其余全一样，所以收在一个组件里——
 * 复制三份的话，理由上限、未登录跳转这些规则迟早各写各的。
 */
const props = defineProps({
    // article | comment | user
    targetType: {type: String, required: true},
    targetId: {type: [Number, String], required: true},
    label: {type: String, default: '举报'},
    // 对象归属人；调用方知道时传进来，自己报自己的按钮直接不渲染
    ownerId: {type: [Number, String], default: null}
})

const REASON_MAX = 200
const REASON_PRESETS = ['广告或引流', '辱骂或人身攻击', '涉嫌违法违规', '抄袭或侵权']

const dialogVisible = ref(false)
const reason = ref('')
const submitting = ref(false)

// 自己的内容不显示举报入口；ownerId 未知（拿不到归属人）时照常显示
const reportable = () => props.ownerId === null || String(props.ownerId) !== String(userInfoStore().id)

const openDialog = () => {
    if (!userInfoStore().id) {
        ElMessage.warning("请先登录后再举报")
        router.push({name: 'Login'})
        return
    }
    reason.value = ''
    dialogVisible.value = true
}

const usePreset = (text) => {
    reason.value = text
}

const submit = async () => {
    const text = reason.value.trim()
    if (!text) {
        ElMessage.warning("请填写举报理由")
        return
    }
    submitting.value = true
    try {
        const res = await submitReport(props.targetType, Number(props.targetId), text)
        if (res.code === 0) {
            ElMessage.success("举报已提交，站长会尽快处理")
            dialogVisible.value = false
        } else {
            // 「已经举报过了」这类判定在后端（有唯一索引兜底），这里如实显示它的原因
            ElMessage.error(res.message)
        }
    } finally {
        submitting.value = false
    }
}
</script>

<template>
    <el-button v-if="reportable()" :icon="Warning" link type="info" class="report-btn"
               @click.stop="openDialog">{{ label }}
    </el-button>

    <el-dialog v-model="dialogVisible" title="举报" width="420px" append-to-body>
        <div class="report-presets">
            <el-tag v-for="item in REASON_PRESETS" :key="item" class="preset-tag" effect="plain"
                    @click="usePreset(item)">{{ item }}
            </el-tag>
        </div>
        <el-input v-model="reason" type="textarea" :rows="3" :maxlength="REASON_MAX" show-word-limit
                  placeholder="请说明举报理由，站长会据此处理"/>
        <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="submit">提交举报</el-button>
        </template>
    </el-dialog>
</template>

<style lang="scss" scoped>
.report-btn {
    font-size: 12px;
}

.report-presets {
    margin-bottom: 10px;

    .preset-tag {
        margin: 0 6px 6px 0;
        cursor: pointer;
    }
}
</style>
