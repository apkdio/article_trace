<script setup>
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {CloseBold, Delete, Refresh, Select, View} from '@element-plus/icons-vue'
import {handleReport, listReports} from '@/api/report.js'
import {assessArticleService, deleteCommentService} from '@/api/article.js'
import PageHeader from '@/components/PageHeader.vue'
import {errorText} from '@/utils/errorText.js'
import router from '@/router/index.js'

/** 筛选值 → 后端 status 参数；all 传 undefined，axios 会省略该参数（即查全部） */
const FILTER_TO_STATUS = {pending: 0, handled: 1, rejected: 2, all: undefined}

/** 举报对象类型的中文名 */
const TARGET_LABEL = {article: '文章', comment: '评论', user: '用户'}

const loading = ref(false)
// 处置完通知外层刷新待办数（菜单角标与左侧列表共用一份计数）
const emit = defineEmits(['handled'])
const items = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const filter = ref('pending')

const load = async () => {
  loading.value = true
  try {
    const res = await listReports(FILTER_TO_STATUS[filter.value], pageNum.value, pageSize.value)
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

const who = (row) => row.reporterNickname || row.reporterUsername || ('用户#' + row.reporterId)

const targetText = (row) => `${TARGET_LABEL[row.targetType] || '内容'}：${row.targetSummary || '—'}`

/** 打开被举报的对象：文章直达详情页；评论没有独立页面，跳到它所属的文章 */
const openTarget = (row) => {
  const id = row.targetType === 'comment' ? row.targetParentId : row.targetId
  if (!id) return ElMessage.warning('原内容已不存在，无法查看')
  const href = router.resolve({name: 'ArticleInfo', params: {id}}).href
  window.open(href, '_blank')
}

/** 删除被举报的评论——复用评论原有的删除接口，不另造一套处置逻辑 */
const removeComment = (row) => {
  ElMessageBox.confirm(
      `确定删除这条评论吗？删除后不可恢复。\n${row.targetSummary || ''}`,
      '删除评论',
      {type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消', center: true}
  ).then(async () => {
    const res = await deleteCommentService(row.targetId, row.targetParentId)
    if (res.code === 0) {
      ElMessage.success('评论已删除！')
      await load()
      emit('handled')
    } else {
      ElMessage.error(errorText(res.message, '删除失败！'))
    }
  }).catch((action) => {
    if (action === 'cancel') ElMessage.info('已取消！')
  })
}

/** 下架被举报的文章——复用文章审核接口，置为「已驳回」，前台不再可见 */
const takeDownArticle = (row) => {
  ElMessageBox.confirm(
      `确定下架「${row.targetSummary || ''}」吗？文章会被置为「已驳回」，前台不再可见。`,
      '下架文章',
      {type: 'warning', confirmButtonText: '下架', cancelButtonText: '取消', center: true}
  ).then(async () => {
    const res = await assessArticleService(row.targetId, 3)
    if (res.code === 0) {
      ElMessage.success('文章已下架！')
      await load()
      emit('handled')
    } else {
      ElMessage.error(errorText(res.message, '操作失败！'))
    }
  }).catch((action) => {
    if (action === 'cancel') ElMessage.info('已取消！')
  })
}

const dispose = (row) => {
  ElMessageBox.confirm(
      '认定这条举报成立并标记已处置？\n注意：本操作只改举报记录，违规内容请先用左侧的按钮处理。',
      '处置举报',
      {type: 'success', confirmButtonText: '认定违规', cancelButtonText: '取消', center: true}
  ).then(async () => {
    const res = await handleReport(row.id, true)
    if (res.code === 0) {
      ElMessage.success('已处置！')
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
  ElMessageBox.confirm(
      '驳回这条举报（未认定违规）？举报人会收到「举报未成立」的通知。',
      '驳回举报',
      {type: 'info', confirmButtonText: '驳回', cancelButtonText: '取消', center: true}
  ).then(async () => {
    const res = await handleReport(row.id, false)
    if (res.code === 0) {
      ElMessage.success('已驳回！')
      await load()
      emit('handled')
    } else {
      ElMessage.error(errorText(res.message, '操作失败！'))
    }
  }).catch((action) => {
    if (action === 'cancel') ElMessage.info('已取消！')
  })
}
</script>

<template>
  <div class="report-manage-container">
    <PageHeader title="举报处理" subtitle="处理用户提交的举报。内容本身的处置走原有按钮，本页只记「谁报了谁、处理了没有」"/>

    <div class="toolbar">
      <el-radio-group v-model="filter" @change="onFilterChange">
        <el-radio-button value="pending">待处理</el-radio-button>
        <el-radio-button value="handled">已处置</el-radio-button>
        <el-radio-button value="rejected">已驳回</el-radio-button>
        <el-radio-button value="all">全部</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table
        v-loading="loading"
        element-loading-text="加载举报数据..."
        :data="items"
        row-key="id"
        style="width: 100%"
    >
      <el-table-column label="举报人" width="180">
        <template #default="{ row }">
          <div class="user-cell">
            <span class="nick">{{ row.reporterNickname || '未设置昵称' }}</span>
            <span class="username">@{{ row.reporterUsername }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="被举报内容" min-width="260">
        <template #default="{ row }">
          <span class="target">{{ targetText(row) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="举报理由" min-width="200">
        <template #default="{ row }">
          <span class="reason">{{ row.reason }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="createTime" label="举报时间" width="170"/>

      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning" disable-transitions>待处理</el-tag>
          <el-tag v-else-if="row.status === 1" type="success" disable-transitions>已处置</el-tag>
          <el-tag v-else type="info" disable-transitions>已驳回</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="处置人" width="120">
        <template #default="{ row }">
          <span v-if="row.handleUsername">{{ row.handleUsername }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>

      <!-- 4 个按钮（查看 + 删评论/下架 + 处置 + 驳回）在 290px 里会换行，放宽到 340 -->
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button size="small" :icon="View" @click="openTarget(row)">查看</el-button>
            <el-button v-if="row.targetType === 'comment' && row.targetParentId" size="small" type="danger"
                       :icon="Delete" @click="removeComment(row)">删评论
            </el-button>
            <el-button v-else-if="row.targetType === 'article'" size="small" type="danger"
                       :icon="CloseBold" @click="takeDownArticle(row)">下架
            </el-button>
            <el-button type="success" size="small" :icon="Select" @click="dispose(row)">处置</el-button>
            <el-button type="info" size="small" @click="reject(row)">驳回</el-button>
          </template>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty :image-size="90" :description="filter === 'pending' ? '没有待处理的举报' : '暂无记录'"/>
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
.report-manage-container {
  padding: 0 4px;

  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
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

  .target,
  .reason {
    word-break: break-all;
  }

  .muted {
    color: #c0c4cc;
  }

  .pager {
    margin-top: 16px;
    justify-content: flex-end;
  }
}
</style>
