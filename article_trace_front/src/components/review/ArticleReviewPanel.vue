<script setup>
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {CloseBold, Select, View} from '@element-plus/icons-vue'
import {assessArticleService, getArticleWithConditionsMaster} from '@/api/article.js'

/**
 * 审核中心 · 待审文章面板。
 *
 * 列表与处置都用文章模块原有的接口：`/article/manageArticles`（state=2）与
 * `PATCH /article/assess`。搬过来的只有「呈现」——审核仍受状态机与条件更新保护。
 */
const STATE_PENDING = 2
const STATE_PUBLISHED = 1
const STATE_REJECTED = 3

const loading = ref(false)
const items = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const previewVisible = ref(false)
const previewRow = ref({})

const load = async () => {
  loading.value = true
  try {
    const res = await getArticleWithConditionsMaster({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      state: STATE_PENDING,
      searchType: 0
    })
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

const openPreview = (row) => {
  previewRow.value = row
  previewVisible.value = true
}

/** 通过 / 驳回共用；命中的违禁词只有站长看得到，这里照实列出来 */
const assess = (row, state) => {
  const pass = state === STATE_PUBLISHED
  ElMessageBox.confirm(
      pass ? `通过「${row.title}」并立即对外发布？` : `驳回「${row.title}」？作者可修改后重新投稿。`,
      pass ? '通过审核' : '驳回文章',
      {type: pass ? 'success' : 'warning', confirmButtonText: pass ? '通过' : '驳回', cancelButtonText: '取消', center: true}
  ).then(async () => {
    const res = await assessArticleService(row.id, state)
    if (res.code === 0) {
      ElMessage.success(pass ? '已通过！' : '已驳回！')
      await load()
    } else {
      ElMessage.error(res.message || '操作失败！')
    }
  }).catch((action) => {
    if (action === 'cancel') ElMessage.info('已取消！')
  })
}

// 容器在切回本面板时可以重新拉一次
defineExpose({load})
</script>

<template>
  <div class="review-panel">
    <div class="panel-toolbar">
      <span class="panel-count">待审 {{ total }} 篇</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="items" row-key="id" style="width: 100%">
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPreview(row)">{{ row.title }}</el-button>
          <el-tag v-if="row.sensitiveHit" size="small" type="danger" disable-transitions
                  class="hit-tag">命中违禁词
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="作者" width="160">
        <template #default="{ row }">{{ row.createUserName || '—' }}</template>
      </el-table-column>
      <el-table-column label="分类" width="140">
        <template #default="{ row }">{{ row.categoryName || '未分类' }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="最后修改" width="170">
        <template #default="{ row }">{{ row.updateTime || row.createTime }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :icon="View" @click="openPreview(row)">预览</el-button>
          <el-button type="success" size="small" :icon="Select" @click="assess(row, STATE_PUBLISHED)">通过</el-button>
          <el-button type="danger" size="small" :icon="CloseBold" @click="assess(row, STATE_REJECTED)">驳回</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :image-size="90" description="没有待审文章"/>
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

    <el-dialog v-model="previewVisible" :title="previewRow.title" width="720px" append-to-body>
      <div class="preview-meta">
        <span>作者：{{ previewRow.createUserName || '—' }}</span>
        <span>分类：{{ previewRow.categoryName || '未分类' }}</span>
      </div>
      <el-alert v-if="previewRow.sensitiveHit" type="warning" :closable="false" show-icon class="preview-alert"
                :title="'命中违禁词' + (previewRow.sensitiveWords ? '：' + previewRow.sensitiveWords : '')"/>
      <div class="preview-content ql-editor" v-html="previewRow.content"></div>
    </el-dialog>
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

  .hit-tag {
    margin-left: 6px;
  }

  .pager {
    margin-top: 16px;
    justify-content: flex-end;
  }

  .preview-meta {
    display: flex;
    gap: 20px;
    font-size: 13px;
    color: #909399;
    margin-bottom: 10px;
  }

  .preview-alert {
    margin-bottom: 10px;
  }

  .preview-content {
    max-height: 50vh;
    overflow: auto;
  }
}
</style>
