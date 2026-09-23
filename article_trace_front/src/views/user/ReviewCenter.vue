<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {Bell, EditPen, Picture, Refresh, UserFilled, Warning} from '@element-plus/icons-vue'
import {reviewSummaryStore} from '@/stores/reviewSummary.js'
import PageHeader from '@/components/PageHeader.vue'
import ArticleReviewPanel from '@/components/review/ArticleReviewPanel.vue'
import AuthorApplyPanel from '@/components/review/AuthorApplyPanel.vue'
import ProfileReviewPanel from '@/components/review/ProfileReviewPanel.vue'
import AvatarReview from '@/views/user/AvatarReview.vue'
import ReportManage from '@/views/user/ReportManage.vue'

/*
 * 审核中心
 */
const loading = ref(false)
const activeType = ref('article')
// 与侧边菜单角标**共用一份**计数：面板处置完 emit('handled') 刷新它，两处同时掉
const reviewSummary = reviewSummaryStore()

const TYPES = [
  {key: 'article', label: '文章审核', hint: '送达的稿件，通过后立即对外发布', icon: Bell},
  {key: 'avatar', label: '头像审核', hint: '通过后立即对用户生效', icon: Picture},
  {key: 'authorApply', label: '作者申请', hint: '通过后对方立即获得发文权限', icon: UserFilled},
  {key: 'profile', label: '资料审核', hint: '昵称命中内容规则才会进这里，通过后写回用户', icon: EditPen},
  {key: 'report', label: '举报处理', hint: '举报只记「谁报了谁」，内容处置走各自的按钮', icon: Warning}
]

const totalPending = computed(() => reviewSummary.total())

const countOf = (key) => reviewSummary.counts[key] || 0

/** 拉一次计数。面板处置完也调它，所以菜单角标与左侧列表同时更新 */
const loadSummary = async () => {
  loading.value = true
  try {
    await reviewSummary.refresh()
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
    <PageHeader title="审核中心" subtitle="集成站内的所有审核模块"/>

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
        <!-- 四个面板的过渡交给 .panel-area > * 的 CSS 入场动画（见下）：
             用 <transition mode="out-in"> 包 v-if 链时，leave 会卡住、新旧面板同时留在 DOM 里 -->
        <ArticleReviewPanel v-if="activeType === 'article'" @handled="loadSummary"/>
        <AvatarReview v-else-if="activeType === 'avatar'" :embedded="true" @handled="loadSummary"/>
        <AuthorApplyPanel v-else-if="activeType === 'authorApply'" @handled="loadSummary"/>
        <ProfileReviewPanel v-else-if="activeType === 'profile'" @handled="loadSummary"/>
        <ReportManage v-else :embedded="true" @handled="loadSummary"/>
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

  // 四个子面板统一的入场过渡。刻意不用 <transition>：
  // 它在 v-if 链上会卡住 leave，导致新旧面板同时留在 DOM 里（已踩过）；
  // 而 CSS 动画只作用于刚挂载的那个元素，切换时必定是一份。
  .panel-area > * {
    animation: panel-fade-in 0.18s ease;
  }

  @keyframes panel-fade-in {
    from {
      opacity: 0;
      transform: translateY(4px);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
}
</style>
