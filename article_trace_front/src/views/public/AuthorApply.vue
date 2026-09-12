<script setup>
import {onMounted, ref} from 'vue'
import {getMyApply, submitAuthorApply} from "@/api/apply.js";
import PageHeader from "@/components/PageHeader.vue";
import {EditPen} from '@element-plus/icons-vue'

const loading = ref(true)
const submitting = ref(false)
const apply = ref(null)
const reason = ref('')

const load = async () => {
    loading.value = true
    try {
        const res = await getMyApply()
        if (res.code === 0) {
            apply.value = res.data
        } else {
            ElMessage.error(res.message || '加载申请状态失败')
        }
    } catch (e) {
        ElMessage.error('加载申请状态失败')
    } finally {
        loading.value = false
    }
}

const submit = async () => {
    if (submitting.value) return
    submitting.value = true
    try {
        const res = await submitAuthorApply(reason.value.trim())
        if (res.code === 0) {
            ElMessage.success('申请已提交，请等待站长审核')
            reason.value = ''
            await load()
        } else {
            ElMessage.error(res.message || '提交失败')
        }
    } catch (e) {
        ElMessage.error('提交失败')
    } finally {
        submitting.value = false
    }
}

onMounted(load)
</script>

<template>
    <div class="author-apply" v-loading="loading">
        <PageHeader title="申请成为作者" subtitle="通过审核后即可发布文章"/>

        <!-- 待审核 -->
        <el-alert
            v-if="apply && apply.status === 0"
            type="info"
            :closable="false"
            show-icon
            title="申请已提交，正在等待站长审核"
            :description="'提交时间：' + (apply.createTime || '-')"
        />

        <!-- 已通过 -->
        <el-alert
            v-else-if="apply && apply.status === 1"
            type="success"
            :closable="false"
            show-icon
            title="恭喜，你的作者申请已通过"
            description="现在可以发布文章了，重新登录后身份即可生效。"
        />

        <!-- 未通过：展示原因，允许重新申请 -->
        <template v-else>
            <el-alert
                v-if="apply && apply.status === 2"
                type="warning"
                :closable="false"
                show-icon
                title="上次申请未通过"
                :description="'原因：' + (apply.rejectReason || '未说明原因')"
                class="reject-alert"
            />

            <el-form label-position="top" class="apply-form">
                <el-form-item label="申请理由（可留空）">
                    <el-input
                        v-model="reason"
                        type="textarea"
                        :rows="5"
                        maxlength="500"
                        show-word-limit
                        placeholder="简单说明你想成为作者的原因，便于站长审核"
                    />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" :icon="EditPen" :loading="submitting" @click="submit">
                        提交申请
                    </el-button>
                </el-form-item>
            </el-form>
        </template>
    </div>
</template>

<style lang="scss" scoped>
.author-apply {
    animation: fadeIn 0.5s ease-out;
    min-height: 200px;

    .reject-alert {
        margin-bottom: 16px;
    }

    .apply-form {
        max-width: 560px;
    }
}
</style>
