<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {Bell, Picture, Refresh, UserFilled, Warning} from '@element-plus/icons-vue'
import {getReviewSummary} from '@/api/review.js'
import {errorText} from '@/utils/errorText.js'
import PageHeader from '@/components/PageHeader.vue'
import ArticleReviewPanel from '@/components/review/ArticleReviewPanel.vue'
import AuthorApplyPanel from '@/components/review/AuthorApplyPanel.vue'
import AvatarReview from '@/views/user/AvatarReview.vue'
import ReportManage from '@/views/user/ReportManage.vue'

/**
 * 审核中心（T19 第二期）。
 *
 * 四类审核原先散在三个页面：文章审在「文章管理页」里筛「待审核」、头像审有独立页、
 * 作者申请藏在「账号管理页」里，举报（第一期）又有自己的页。站长要满站找待办。
 * 这里只做**收拢与呈现**——不新建统一审核表、不改任何业务逻辑：
 *
 * - 待办数来自 `/review/summary`（给菜单角标与左侧计数用）
 * - 列表与处置全部沿用各类型原有的接口与组件：文章面板与申请面板是本目录新拆的，
 *   头像与举报直接复用原有页面组件（它们的代码本来就自成一体，不必复制一份）
 */
const loading = ref(false)
const activeType = ref('article')
const summary = ref({article: 0, avatar: 0, authorApply: 0, report: 0})

const TYPES = [
  {key: 'article', label: '文章审核', hint: '送达的稿件，通过后立即对外发布', icon: Bell},
  {key: 'avatar', label: '头像审核', hint: '通过后立即对用户生效', icon: Picture},
  {key: 'authorApply', label: '作者申请', hint: '通过后对方立即获得发文权限', icon: UserFilled},
  {key: 'report', label: '举报处理', hint: '举报只记「谁报了谁」，内容处置走各自的按钮', icon: Warning}
]

// 面板实例：处置完当前面板后能顺手刷新一下左侧计数
const panelRef = ref()

const totalPending = computed(() =>
    Object.values(summary.value).reduce((sum, n) => sum + (n || 0), 0))

const countOf = (key) => summary.value[key] || 0

const loadSummary = async () => {
  loading.value = true
  try {
    const res = await getReviewSummary()
    if (res.code === 0) {
      summary.value = res.data || summary.value
    } else {
      ElMessage.error(errorText(res.message, "待办数获取失败！"))
    }
  } catch (err) {
    ElMessage.error("待办数获取失败！")
  } finally {
    loading.value = false
  }
}

onMounted(loadSummary)

// 切换类型时重新取一次计数：上一步刚处置掉的东西不该还挂在角标上
watch(activeType, () => {
  loadSummary()
})
</script>

<template>
  <div class="review-center-container">
    <PageHeader title="审核中心" subtitle="四种待办收在一处：文章 / 头像 / 作者申请 / 举报"/>

    <div class="review-body">
      <aside class="type-list" v-loading="loading">
        <div v-for="type in TYPES" :key="type.key" class="type-item"
             :class="{active: activeType === type.key}" @click="activeType = type.key">
          <el-icon class="type-icon">
            <component :is="type.icon"/>
          </el-icon>
          <div class="type-text">
            <span class="type-label">{{ type.label }}</span>
            <span class="type-hint">{{ type.hint }}</span>
          </div>
          <span v-if="countOf(type.key) > 0" class="type-badge">{{ countOf(type.key) > 99 ? '99+' : countOf(type.key) }}</span>
        </div>
        <div class="type-total">合计待办 {{ totalPending }}</div>
        <el-button class="type-refresh" size="small" :icon="Refresh" @click="loadSummary">刷新计数</el-button>
      </aside>

      <section class="panel-area">
        <ArticleReviewPanel v-if="activeType === 'article'" ref="panelRef" @handled="loadSummary"/>
        <AvatarReview v-else-if="activeType === 'avatar'"/>
        <AuthorApplyPanel v-else-if="activeType === 'authorApply'" ref="panelRef" @handled="loadSummary"/>
        <ReportManage v-else ref="panelRef"/>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.review-center-container {
  .review-body {
    display: flex;
    gap: 20px;
    align-items: flex-start;
  }

  .type-list {
    flex: 0 0 260px;
    background: #fff;
    border-radius: 12px;
    padding: 12px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);

    .type-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      border-radius: 8px;
      cursor: pointer;
      transition: background 0.2s;

      &:hover {
        background: #f5f7fa;
      }

      &.active {
        background: #ecf5ff;
      }

      .type-icon {
        font-size: 18px;
        color: #409eff;
      }

      .type-text {
        display: flex;
        flex-direction: column;
        flex: 1;

        .type-label {
          font-weight: 600;
        }

        .type-hint {
          font-size: 12px;
          color: #94a3b8;
        }
      }

      // 同理不用 el-badge：绝对定位的角标在 flex 行里会错位。
      // 尺寸照 el-badge 的度量：单位数恰好是圆，两位数自然变宽。
      .type-badge {
        align-self: center;
        box-sizing: border-box;
        min-width: 18px;
        height: 18px;
        text-align: center;
        background: #f56c6c;
        color: #fff;
        border-radius: 9px;
        padding: 0 6px;
        font-size: 12px;
        line-height: 18px;
      }
    }

    .type-total {
      margin: 12px 12px 6px;
      font-size: 12px;
      color: #909399;
    }

    .type-refresh {
      width: 100%;
    }
  }

  .panel-area {
    flex: 1;
    min-width: 0;
    background: #fff;
    border-radius: 12px;
    padding: 16px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);
  }
}
</style>
