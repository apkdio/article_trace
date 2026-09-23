<script setup>
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {CloseBold, Picture, Refresh, Select} from '@element-plus/icons-vue'
import {listAvatarApplies, reviewAvatarApply} from '@/api/avatar.js'
import PageHeader from '@/components/PageHeader.vue'
import {errorText} from '@/utils/errorText.js'

/** 筛选值 → 后端 status 参数；all 传 undefined，axios 会省略该参数（即查全部） */
const FILTER_TO_STATUS = {pending: 0, approved: 1, rejected: 2, all: undefined}

const loading = ref(false)
// 处置完通知外层刷新待办数（菜单角标与左侧列表共用一份计数）
const emit = defineEmits(['handled'])
// 嵌在审核中心子面板里时不要自己的标题（否则子面板被压得没空间）
defineProps({embedded: {type: Boolean, default: false}})
const items = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const filter = ref('pending')

const load = async () => {
  loading.value = true
  try {
    const res = await listAvatarApplies(FILTER_TO_STATUS[filter.value], pageNum.value, pageSize.value)
    if (res.code === 0) {
      items.value = res.data?.items || []
      total.value = res.data?.total || 0
    } else {
      ElMessage.error(errorText(res.message, '获取失败！'))
    }
  } catch (err) {
    ElMessage.error('数据获取失败！')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const onSizeChange = (size) => {
  pageSize.value = size
  pageNum.value = 1
  load()
}
const onCurrentChange = (num) => {
  pageNum.value = num
  load()
}
const onFilterChange = () => {
  pageNum.value = 1
  load()
}

const who = (row) => row.nickname || row.username || ('用户#' + row.userId)

const approve = (row) => {
  ElMessageBox.confirm(
      `确定通过「${who(row)}」的头像吗？通过后立即对其生效，其原头像会被删除。`,
      '通过头像',
      {type: 'success', confirmButtonText: '通过', cancelButtonText: '取消', center: true}
  ).then(async () => {
    const res = await reviewAvatarApply(row.id, true, null)
    if (res.code === 0) {
      ElMessage.success('已通过！')
      await load()
      emit('handled')
    } else {
      ElMessage.error(errorText(res.message, '操作失败！'))
    }
  }).catch((action) => {
    if (action === 'cancel') ElMessage.info('已取消！')
  })
}

const reject = (row) => {
  ElMessageBox.prompt(
      '理由会通过站内信和邮件发给用户，请写清楚。',
      `拒绝「${who(row)}」的头像`,
      {
        confirmButtonText: '提交拒绝',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：图片过于模糊，请换一张清晰的',
        inputValidator: (v) => (v && v.trim().length > 0) || '拒绝理由不能为空',
        center: true,
      }
  ).then(async ({value}) => {
    const res = await reviewAvatarApply(row.id, false, value.trim())
    if (res.code === 0) {
      ElMessage.success('已拒绝！')
      await load()
      emit('handled')
    } else {
      ElMessage.error(errorText(res.message, '操作失败！'))
    }
  }).catch((action) => {
    // 校验不通过时 action 是 'cancel' 以外的值，别误报「已取消」
    if (action === 'cancel') ElMessage.info('已取消！')
  })
}
</script>

<template>
  <div class="avatar-review-container">
    <PageHeader v-if="!embedded" title="头像审核" subtitle="审核用户提交的新头像，通过后立即生效"/>

    <div class="toolbar">
      <el-radio-group v-model="filter" @change="onFilterChange">
        <el-radio-button value="pending">待审核</el-radio-button>
        <el-radio-button value="approved">已通过</el-radio-button>
        <el-radio-button value="rejected">已拒绝</el-radio-button>
        <el-radio-button value="all">全部</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table
        v-loading="loading"
        element-loading-text="加载审核数据..."
        :data="items"
        row-key="id"
        style="width: 100%"
    >
      <el-table-column label="待审头像" width="140">
        <template #default="{ row }">
          <el-image
              v-if="row.pendingPicThumbSrc"
              class="avatar-thumb"
              :src="row.pendingPicThumbSrc"
              :preview-src-list="[row.pendingPicSrc || row.pendingPicThumbSrc]"
              :initial-index="0"
              fit="cover"
              preview-teleported
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="用户" width="220">
        <template #default="{ row }">
          <div class="user-cell">
            <span class="nick">{{ row.nickname || '未设置昵称' }}</span>
            <span class="username">@{{ row.username }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="createTime" label="提交时间" width="180"/>

      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning" disable-transitions>待审核</el-tag>
          <el-tag v-else-if="row.status === 1" type="success" disable-transitions>已通过</el-tag>
          <el-tag v-else type="danger" disable-transitions>已拒绝</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="拒绝理由" min-width="200">
        <template #default="{ row }">
          <span v-if="row.rejectReason" class="reason">{{ row.rejectReason }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button type="success" size="small" :icon="Select" @click="approve(row)">允许</el-button>
            <el-button type="danger" size="small" :icon="CloseBold" @click="reject(row)">拒绝</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty :image-size="90" :description="filter === 'pending' ? '没有待审核的头像' : '暂无记录'"/>
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
.avatar-review-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  animation: fadeIn 0.4s ease-out;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.avatar-thumb {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: 1px solid #e2e8f0;
  cursor: zoom-in;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .nick {
    color: #1e293b;
    font-weight: 500;
  }

  .username {
    font-size: 12px;
    color: #94a3b8;
  }
}

.reason {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.muted {
  color: #cbd5e1;
}

.pager {
  justify-content: flex-end;
}
</style>
