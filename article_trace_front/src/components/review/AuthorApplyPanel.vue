<script setup>
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {CloseBold, Select} from '@element-plus/icons-vue'
import {listApplies, reviewApply} from '@/api/apply.js'

/**
 * 审核中心 · 作者申请面板。
 *
 * 这段原先藏在「账号管理」页里——站长要审申请得先想到去那儿翻。
 * 接口不变：`/applyAuthor/manage/list` 与 `PATCH /applyAuthor/manage/review/{id}`。
 */
const STATUS_PENDING = 0

const loading = ref(false)
// 处置完通知外层刷新待办数
const emit = defineEmits(['handled'])
const items = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const load = async () => {
  loading.value = true
  try {
    const res = await listApplies(STATUS_PENDING, pageNum.value, pageSize.value)
    if (res.code === 0) {
      items.value = res.data?.items || []
      total.value = res.data?.total || 0
    } else {
      items.value = []
      total.value = 0
    }
  } catch (err) {
    ElMessage.error('数据获取失败！')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const onCurrentChange = (num) => {
  pageNum.value = num
  load()
}
const onSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
  load()
}

const who = (row) => row.nickname || row.username || ('用户#' + row.userId)

const approve = (row) => {
  ElMessageBox.confirm(
      `通过「${who(row)}」的作者申请？通过后对方立即获得发文权限，并被要求重新登录。`,
      '通过申请',
      {type: 'success', confirmButtonText: '通过', cancelButtonText: '取消', center: true}
  ).then(async () => {
    const res = await reviewApply(row.id, true, null)
    if (res.code === 0) {
      ElMessage.success('已通过！')
      await load()
      emit('handled')
    } else {
      ElMessage.error(res.message || '操作失败！')
    }
  }).catch((action) => {
    if (action === 'cancel') ElMessage.info('已取消！')
  })
}

const reject = (row) => {
  ElMessageBox.prompt('拒绝理由会随站内信发给申请人，请写清楚。', `拒绝「${who(row)}」的申请`, {
    confirmButtonText: '提交拒绝',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '例如：申请理由过于简略，请补充你想写哪类文章',
    inputValidator: (v) => (v && v.trim().length > 0) || '拒绝理由不能为空',
    center: true,
  }).then(async ({value}) => {
    const res = await reviewApply(row.id, false, value.trim())
    if (res.code === 0) {
      ElMessage.success('已拒绝！')
      await load()
      emit('handled')
    } else {
      ElMessage.error(res.message || '操作失败！')
    }
  }).catch((action) => {
    // 校验不通过时 action 不是 'cancel'，别误报「已取消」
    if (action === 'cancel') ElMessage.info('已取消！')
  })
}

defineExpose({load})
</script>

<template>
  <div class="review-panel">
    <div class="panel-toolbar">
      <span class="panel-count">待审 {{ total }} 人</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="items" row-key="id" style="width: 100%">
      <el-table-column label="申请人" width="220">
        <template #default="{ row }">
          <div class="user-cell">
            <span class="nick">{{ row.nickname || '未设置昵称' }}</span>
            <span class="username">@{{ row.username }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="申请理由" min-width="260">
        <template #default="{ row }">
          <span class="reason">{{ row.reason || '（未填写）' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="提交时间" width="180"/>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="success" size="small" :icon="Select" @click="approve(row)">通过</el-button>
          <el-button type="danger" size="small" :icon="CloseBold" @click="reject(row)">拒绝</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :image-size="90" description="没有待审的作者申请"/>
      </template>
    </el-table>

    <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        @size-change="onSizeChange"
        @current-change="onCurrentChange"
    />
  </div>
</template>

<style lang="scss" scoped>
.review-panel {
  .panel-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .panel-count {
      font-size: 13px;
      color: #909399;
    }
  }

  .user-cell {
    display: flex;
    flex-direction: column;

    .nick {
      font-weight: 600;
    }

    .username {
      font-size: 12px;
      color: #94a3b8;
    }
  }

  .reason {
    word-break: break-all;
  }

  .pager {
    margin-top: 16px;
    justify-content: flex-end;
  }
}
</style>
