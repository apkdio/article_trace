<script setup>
import {ref, onMounted} from 'vue'
import {Search, View, SwitchButton, CaretBottom, User, Refresh, Timer, Bell} from '@element-plus/icons-vue'
import {userInfoStore} from "@/stores/userInfo.js";
import avatar from "@/assets/defaultLogo.jpg";
import cover from "@/assets/defaultCover.jpg"
import {
  getMasterInfoService,
  getPublicArticlesService,
  getTopArticlesService
} from "@/api/article.js";
import router from "@/router/index.js";
import {logout as doLogout} from "@/utils/auth.js";
import {isAuthHandled, markAuthHandled} from "@/utils/session.js";
import {searchConditions} from "@/stores/searchConditions.js";
import {loginCheckPublic} from "@/utils/loginCheck.js";
import AgentChat from "@/components/AgentChat.vue";
import NotificationBell from "@/components/NotificationBell.vue";
import {formatDate} from "@/utils/date.js";
import TopArticlesList from "@/components/TopArticlesList.vue";
import AuthorCard from "@/components/AuthorCard.vue";
import {getSiteFeatures} from "@/api/site.js";

const loading = ref(true)
const topArticleLoading = ref(true)
const isLogin = ref(false)
const pageSize = ref(10)
const pageNum = ref(1)
const total = ref()
const masterInfo = ref({})
const masterLoading = ref(true)

const searchCondition = ref({
  categoryId: null,
  search: null,
  searchType: 0,
  nickName: null,
  pageNum: null,
  pageSize: null
})
// 模拟数据
const articleList = ref([])

const topArticles = ref()

// 站名与 logo 由后端开关决定：线上做个人备案时页头不露 logo、站名换成备案名，
// 避免与备案信息看起来不一致。本地不配就是原样（文迹 + 显 logo）
const siteName = ref('文迹')
const logoEnabled = ref(true)

const loadSiteFeatures = async () => {
  try {
    const features = await getSiteFeatures()
    siteName.value = features.displayName
    logoEnabled.value = features.logoEnabled
    document.title = `${siteName.value} - 在这里，每一份文章都有价值！`
  } catch (e) {
    // 站点配置只影响站名与 logo，失败时沿用默认值
  }
}

const checkLogin = async () => {
  try {
    await loginCheckPublic()
  } catch (e) {
    if (e.message === "not login") return
    if (!isAuthHandled())
      ElMessage.warning("登录已失效！")
    userInfoStore().clearUserInfo()
    markAuthHandled()
  } finally {
    if (userInfoStore().username !== "") isLogin.value = true
  }
}

onMounted(() => {
  // 四个模块各自加载、各自撤遮罩：任何一个慢或失败都不拖住其它模块
  loadSiteFeatures()
  checkLogin()
  getTopArticles()
  getArticles()
  getMasterInfo()
})

const getMasterInfo = async () => {
  try {
    const masterData = await getMasterInfoService()
    if (masterData.code === 0) {
      masterInfo.value = masterData.data
    } else {
      ElMessage.error("获取站长信息失败！")
    }
  } catch (err) {
    ElMessage.error("服务器响应失败！")
  } finally {
    setTimeout(() => masterLoading.value = false, 100)
  }

}
/** 竞态守卫：只有「最后一次」请求的响应允许落地，迟到的旧响应直接丢弃 */
let listRequestSeq = 0

const getArticles = async () => {
  const seq = ++listRequestSeq
  loading.value = true
  searchCondition.value.pageNum = pageNum.value
  searchCondition.value.pageSize = pageSize.value
  if (searchConditions().state !== 0) {
    searchCondition.value.search = searchConditions().searchCondition.search
    searchCondition.value.searchType = searchConditions().searchCondition.searchType
    searchCondition.value.nickName = searchConditions().searchCondition.nickName
    searchCondition.value.categoryId = searchConditions().searchCondition.categoryId
  }
  searchConditions().clear()
  try {
    const data = await getPublicArticlesService(searchCondition.value)
    if (seq !== listRequestSeq) return
    if (data.code === 0) {
      articleList.value = data.data.items
      total.value = data.data.total
    } else {
      articleList.value = []
      total.value = 0
    }
  } catch (e) {
    if (seq !== listRequestSeq) return
    articleList.value = []
    total.value = 0
    ElMessage.error("服务器响应失败！")
  } finally {
    if (seq === listRequestSeq) {
      setTimeout(() => loading.value = false, 100)
    }
  }
}

const getTopArticles = async () => {
  try {
    const data = await getTopArticlesService()
    if (data.code === 0) {
      topArticles.value = data.data
    } else {
      ElMessage.error("获取热门文章失败！")
    }
  } catch (e) {
    ElMessage.error("服务器响应失败！")
  } finally {
    setTimeout(() => topArticleLoading.value = false, 100)
  }
}


const onCurrentChange = (num) => {
  pageNum.value = num
  getArticles()

  setTimeout(() => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  }, 100)
}

const switchCommand = (command) => {
  switch (command) {
    case "profile":
      if (userInfoStore().type !== 0 && userInfoStore().type !== 1) router.push({name: "ReaderHome"})
      else router.push({name: "MainPage"})
      break;
    case "logout":
      doLogout()
      break;
  }
}
const clearInf = () => {
  searchCondition.value = {
    categoryId: null,
    search: null,
    searchType: 0,
    nickName: null,
    pageNum: null,
    pageSize: null
  }
  pageNum.value = 1
  getArticles()
}
const searchArticleInCategory = (categoryId) => {
  if (categoryId == null) categoryId = -1
  searchCondition.value = {
    categoryId: categoryId,
    search: null,
    searchType: 0,
    nickName: null,
    pageNum: null,
    pageSize: null
  }
  pageNum.value = 1
  getArticles()
  setTimeout(() => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  }, 100)
}
const searchArticleInWriter = (nickName) => {
  searchCondition.value = {
    categoryId: null,
    search: null,
    searchType: 0,
    nickName: nickName,
    pageNum: null,
    pageSize: null
  }
  pageNum.value = 1
  getArticles()
  setTimeout(() => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  }, 100)
}


</script>
<template>
  <div class="front-layout">
    <el-header class="custom-header">
      <div class="logo-area" v-if="logoEnabled" @click="router.push({name: 'PublicHome'})">
        <div class="logo-img"></div>
      </div>
      <div class="spacer-div"></div>
      <div class="search-section">
        <div class="search-input-composite">
          <div class="search-type-segmented">
            <span :class="{ active: String(searchCondition.searchType) !== '1' }"
                  @click="searchCondition.searchType = 0">标题</span>
            <span :class="{ active: String(searchCondition.searchType) === '1' }"
                  @click="searchCondition.searchType = 1">作者</span>
          </div>
          <el-input
              v-model="searchCondition.search"
              placeholder="搜索感兴趣的内容..."
              :prefix-icon="Search"
              class="custom-search"
              @keyup.enter="getArticles"
          />
        </div>
        <el-tooltip
            class="box-item"
            effect="light"
            content="搜索"
            placement="bottom"
        >
          <el-button :icon="Search" circle @click="getArticles"/>
        </el-tooltip>
        <el-tooltip
            class="box-item"
            effect="light"
            content="重置"
            placement="bottom"
        >
          <el-button :icon="Refresh" circle @click="clearInf"/>
        </el-tooltip>
        <!--
          站内通知：放在按钮组里、复用圆形按钮样式。
          不放在用户名旁边，是为了避免昵称过长时把铃铛挤错位。
        -->
        <el-tooltip
            v-if="isLogin"
            class="box-item"
            effect="light"
            content="通知"
            placement="bottom"
        >
          <NotificationBell v-slot="{ open }">
            <el-button :icon="Bell" circle @click="open"/>
          </NotificationBell>
        </el-tooltip>
      </div>

      <div class="spacer-div"></div>

      <div class="user-area-wrapper">
        <div class="user-action">
          <el-button v-if="!isLogin" type="primary" plain round @click="router.push({name: 'Login'})">登录</el-button>
          <el-dropdown placement="bottom-end" v-if="isLogin" @command="switchCommand">
            <span class="el-dropdown__box">
              <span class="welcome-text">欢迎回来！
                <span class="username">{{ userInfoStore().nickname ? userInfoStore().nickname : "匿名用户" }}</span>
                <el-tag size="small" v-if="userInfoStore().type === 0" round style="margin-bottom: 2px"
                        type="primary" disable-transitions>站长</el-tag>
                <el-tag size="small" v-if="userInfoStore().type === 1" round style="margin-bottom: 2px"
                        type="warning" disable-transitions>作者</el-tag>
                <el-tag size="small" v-if="userInfoStore().type === 2" round style="margin-bottom: 2px"
                        type="info" disable-transitions>读者</el-tag>
              </span>
              <el-avatar :size="36" :src="userInfoStore().userPicThumbSrc || avatar"/>
              <el-icon class="arrow"><CaretBottom/></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </el-header>
    <el-container class="outer-container">
      <el-container class="main-wrapper">
        <el-main class="article-container" v-loading="loading" element-loading-text="正在加载文章...">
          <div class="content-card-wrapper">
            <span v-if="searchCondition.categoryId"
                  class="category-info">当前分类：{{
                articleList[0]?.categoryName ? articleList[0].categoryName : "未分类"
              }}</span>
            <span v-if="searchCondition.nickName"
                  class="category-info">作者：{{
                articleList[0]?.createUserName ? articleList[0].createUserName : "unknown"
              }}</span>
            <el-empty v-if="articleList.length === 0" description="暂未发现符合条件的文章" :image-size="100"/>
            <div v-for="item in articleList" :key="item.id" class="article-card">
              <div class="article-cover">
                <router-link style="text-decoration: none;" target="_blank"
                             :to="{name: 'ArticleInfo', params: {id: item.id}}">
                  <el-image :src="item.coverThumbSrc!==null?item.coverThumbSrc:cover" fit="cover"/>
                </router-link>
              </div>
              <div class="article-info">
                <router-link style="text-decoration: none" target="_blank"
                             :to="{name: 'ArticleInfo', params: {id: item.id}}">
                  <h2 class="title">{{ item.title }}</h2>
                </router-link>
                <p class="summary">{{ item.content }}</p>
                <div class="meta">
                  <span v-if="item.categoryName !== null" class="category-chip"
                        @click="searchArticleInCategory(item.categoryId)">{{ item.categoryName }}</span>
                  <span v-else class="category-chip is-uncategorized">未分类</span>
                  <span @click="searchArticleInWriter(item.createUserName)" class="article-writer">
                    <el-icon><User/></el-icon> {{ item.createUserName }}</span>
                  <span><el-icon><View/></el-icon> {{ item.views ? item.views : 0 }}</span>
                  <span>
                    <el-icon><Timer/></el-icon>
                    <!-- 与列表排序一致（按发布时间）：显示 updateTime 会让顺序看着是坏的 -->
                    {{ formatDate(item.createTime) }}</span>
                </div>
              </div>
            </div>

            <div class="pagination-box">
              <el-pagination
                  background
                  v-model:current-page="pageNum"
                  v-model:page-size="pageSize"
                  layout="prev, pager, next"
                  :total="total"
                  @current-change="onCurrentChange"
              />
            </div>
          </div>
        </el-main>


        <el-aside width="320px" class="right-sidebar">
          <AuthorCard :info="masterInfo" :loading="masterLoading" default-name="文迹站长" contact-prefix="邮箱："
                      header-bg="linear-gradient(135deg, #2b7a78 0%, #3a6073 100%)">
            <template #role>
              <el-tag size="small" type="danger" disable-transitions>本站负责人</el-tag>
            </template>
          </AuthorCard>
          <TopArticlesList :articles="topArticles" :loading="topArticleLoading"/>
        </el-aside>
      </el-container>

      <el-footer class="custom-footer">
        © {{ new Date().getFullYear() }} {{ siteName }} · MIT License
      </el-footer>
    </el-container>
  </div>

  <AgentChat/>
</template>

<style lang="scss" scoped>
.front-layout {
  min-height: 100vh;
  background-color: var(--at-bg-page);

  .outer-container {
    margin: 0 auto;
    padding: 0 20px;
    max-width: 1450px;
  }

  // Header 样式
  .custom-header {
    background: var(--at-bg-header);
    height: 80px !important;
    display: flex;
    align-items: center;
    box-sizing: border-box;
    border-bottom: 1px solid var(--at-border-warm);
    box-shadow: 0 2px 12px rgba(44, 42, 38, 0.04);
    margin-bottom: 20px;
    z-index: 100;
    position: fixed;
    width: 100%;

    .logo-area {
      display: flex;
      height: 100%;

      &:hover {
        cursor: pointer
      }

      .logo-img {
        width: 120px;
        background: url('@/assets/logo.png') no-repeat center / 120px auto;
        margin-bottom: 5px;
      }

      .logo-text {
        font-size: 20px;
        font-weight: bold;
        color: var(--at-celadon);
      }
    }

    .spacer-div {
      flex: 1;
    }

    .welcome-text {
      font-size: 15px;
      text-align: right;
      color: var(--at-text-regular);
      white-space: nowrap;
    }

    .search-input-composite {
      display: flex;
      align-items: center;
      background: var(--at-bg-tint);
      border: 1px solid var(--at-border-warm);
      border-radius: 20px;
      padding: 3px 10px 3px 4px;
      height: 36px;
      width: 350px;
      transition: all 0.2s;

      &:focus-within {
        background: var(--at-bg-card);
        border-color: var(--at-celadon);
        box-shadow: 0 0 0 2px rgba(43, 122, 120, 0.12);
      }
    }

    .search-type-segmented {
      display: inline-flex;
      background: rgba(44, 42, 38, 0.055);
      border-radius: 16px;
      padding: 2px;
      margin-right: 4px;
      flex-shrink: 0;

      span {
        font-size: 12px;
        padding: 3px 10px;
        border-radius: 14px;
        color: var(--at-text-regular);
        cursor: pointer;
        user-select: none;
        transition: all 0.18s;
        line-height: 1.25;

        &.active {
          background: var(--at-bg-card);
          color: var(--at-celadon);
          font-weight: 600;
          box-shadow: 0 1px 2px rgba(44, 42, 38, 0.08);
        }
      }
    }

    .custom-search {
      flex: 1;

      :deep(.el-input__wrapper) {
        border-radius: 20px;
        background-color: transparent !important;
        box-shadow: none !important;
        padding: 0 6px;
      }
    }

    .user-profile {
      display: flex;
      align-items: center;
      gap: 12px;

      .welcome-text {
        font-size: 14px;
        color: var(--at-text-regular);
      }
    }
  }

  .main-wrapper {
    margin-top: 100px;
    gap: 20px;
    align-items: flex-start;
  }

  // 左侧文章区
  .article-container {
    padding: 0;

    .content-card-wrapper {
      background: var(--at-bg-card);
      border-radius: 12px;
      padding: 24px;
      min-height: 656px;
      display: flex;
      flex-direction: column;
      border: 1px solid var(--at-border-warm);
      box-shadow: 0 2px 12px rgba(44, 42, 38, 0.03);
    }

    .article-card {
      display: flex;
      padding: 20px 0;
      border-bottom: 1px solid var(--at-divider-warm);
      transition: all 0.3s;


      .article-info {
        flex: 1;

        .title {
          margin: 0 0 10px 0;
          font-size: 20px;
          color: var(--at-text-main);
          cursor: pointer;
          text-decoration: none;
          transition: color 0.2s;

          &:hover {
            color: var(--at-celadon);
          }
        }

        .summary {
          color: var(--at-text-regular);
          font-size: 14px;
          line-height: 1.6;
          margin-bottom: 15px;
          min-height: 45px;
        }

        .meta {
          display: flex;
          align-items: center;
          gap: 15px;
          color: var(--at-text-secondary);
          font-size: 13px;

          .el-icon {
            vertical-align: middle;
            margin-right: 4px;
          }
        }
      }

      .article-cover {
        width: 180px;
        height: 120px;
        border: 1px solid var(--at-border-warm);
        border-radius: 8px;
        margin-right: 20px;
        padding: 3px;
        background: var(--at-bg-card);

        .el-image {
          border-radius: 6px;
          width: 100%;
          height: 100%;

          &:hover {
            transform: scale(1.03);
            transition: 0.3s ease;
          }
        }
      }
    }
  }

  // 分页右对齐
  .pagination-box {
    margin-top: auto;
    padding-top: 30px;
    display: flex;
    justify-content: flex-end;
  }

  .article-writer {
    transition: color 0.18s;

    &:hover {
      color: var(--at-celadon);
      cursor: pointer;
    }
  }

  :deep(.el-button+.el-button) {
    margin-left: 0;
  }

  // 右侧排行榜
  .right-sidebar {
    display: flex;
    gap: 20px;
    flex-direction: column;


  }

  .username {
    color: var(--at-text-main);
    text-align: center;
    font-weight: 600;
    padding: 0 5px 0 2px;
  }

  .custom-footer {
    text-align: center;
    color: var(--at-text-secondary);
    font-size: 13px;
    padding-top: 10px;
    padding-bottom: 5px;
  }

  .el-footer {
    --el-footer-height: null;
  }
}

.search-section {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0; /* 禁止搜索框被挤窄 */
}

.user-area-wrapper {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 260px; /* 重要：给它一个足够的最小宽度，涵盖欢迎语+头像的宽度 */
  flex-shrink: 0; /* 禁止容器被压缩 */
}



.user-action {
  display: flex;
  align-items: center;
  width: auto; /* 去掉之前的固定 70px，由 wrapper 统一控制 */
}

.category-info {
  font-weight: bold;
  color: var(--at-text-main);
  padding-bottom: 10px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--at-divider-warm);
}
// ========== 移动端适配 (宽度 ≤ 768px) ==========
@media (max-width: 768px) {
  .front-layout {
    .custom-header {
      height: auto !important;           // 取消固定高度
      min-height: 70px;
      padding: 10px 12px;
      flex-wrap: wrap;                  // 允许换行
      margin-bottom: 0;                 // 取消底部间距（因为下面有主容器的margin-top）

      .logo-area {
        margin-right: auto;
        .logo-img {
          width: 60px;
          height: 30px;
          background: url('@/assets/logo.png') no-repeat center / cover;
        }
      }

      .spacer-div {
        display: none;                  // 隐藏占位符
      }

      .search-section {
        order: 3;                       // 把搜索区域放到最下面
        width: 100%;
        margin-top: 12px;
        gap: 8px;

        .search-input-composite {
          flex: 1;
          width: auto;
        }

        .el-button {
          padding: 8px;                // 缩小按钮内边距
        }
      }

      .user-area-wrapper {
        min-width: auto;
        flex-shrink: 1;
        gap: 8px;

        .welcome-text {
          display: inline-flex;
          align-items: center;
          font-size: 13px;
          .username {
            display: inline-block;
            max-width: 80px;
            overflow: hidden;
            text-overflow: ellipsis;
            vertical-align: bottom;
          }
        }
      }
    }

    .main-wrapper {
      margin-top: 130px;                // 减小顶部间距（原为100px）
      flex-direction: column;
      gap: 16px;

      .article-container {
        width: 100%;
        padding: 0;

        .content-card-wrapper {
          padding: 16px;
          min-height: auto;

          .article-card {
            flex-direction: column;
            padding: 16px 0;

            .article-cover {
              width: 100%;
              height: auto;
              margin-right: 0;
              margin-bottom: 12px;

              .el-image {
                object-fit: cover;
              }
            }

            .article-info {
              .title {
                font-size: 18px;
                margin-bottom: 8px;
              }

              .summary {
                font-size: 13px;
                line-height: 1.4;
                min-height: auto;
                margin-bottom: 12px;
                display: -webkit-box;
                -webkit-line-clamp: 2;      // 最多显示两行摘要
                -webkit-box-orient: vertical;
                overflow: hidden;
              }

              .meta {
                flex-wrap: wrap;
                gap: 12px;
                font-size: 12px;
              }
            }
          }
        }

        .pagination-box {
          justify-content: center;
          padding-top: 20px;

          :deep(.el-pagination) {
            .btn-prev, .btn-next, .el-pager li {
              min-width: 32px;
              height: 32px;
              line-height: 32px;
            }
          }
        }
      }

      .right-sidebar {
        width: 100%;
        gap: 16px;


      }
    }

    .custom-footer {
      font-size: 12px;
      padding: 20px 0 10px;
    }
  }
}
</style>