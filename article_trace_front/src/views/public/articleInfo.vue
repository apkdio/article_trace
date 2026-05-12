<script setup>
import {ref, onMounted, computed} from 'vue'
import {
  Search, SwitchButton, CaretBottom, User, Timer, Calendar,
  ChatLineRound, View, Delete
} from '@element-plus/icons-vue'
import {userInfoStore} from "@/stores/userInfo.js";
import avatar from "@/assets/defaultLogo.jpg";
import {
  addCommentService,
  addViewService, deleteCommentService,
  getArticleDetail,
  getCommentsService,
  getTopArticlesService,
  getWriterInfoService
} from "@/api/article.js";
import router from "@/router/index.js";
import {logoutService} from "@/api/user.js";
import {tokenStorage} from "@/stores/tokenStorage.js";
import logo from "@/assets/defaultLogo.jpg";
import {searchConditions} from "@/stores/searchConditions.js";

const props = defineProps(["id"])
const loading = ref(true)
const topArticleLoading = ref(true)
const commentTotal = ref(0)
const comments = ref([])
const commentConditions = ref({
  articleId: props.id,
  pageNum: 1,
  pageSize: 10
})

const articleInfo = ref({})
const writerInfo = ref({})
const writerLoading = ref(true)
const isLogin = ref(false)
const topArticles = ref()

const canComment = computed(() => {
  return isLogin.value && userInfoStore().nickname && userInfoStore().nickname.trim() !== ""
})

const searchCondition = ref({
  categoryId: null,
  search: null,
  searchType: 0,
  nickName: null
})
const commentModel = ref({
  articleId: props.id,
  content: "",
  userId: null
})
onMounted(async () => {
  addViewService(props.id)
  await getData()
  await getTopArticles()
  if (articleInfo.value) {
    document.title = articleInfo.value.title ? articleInfo.value.title : "无标题"
  }
  if (userInfoStore().username !== "") isLogin.value = true
  await getComments()
})

const getData = async () => {
  try {
    const data = await getArticleDetail(props.id)
    if (data.code === 0) {
      articleInfo.value = data.data
      const writerData = await getWriterInfoService(data.data.createUserName)
      if (writerData.code === 0) {
        writerInfo.value = writerData.data
      }
    }
  } catch (e) {
    ElMessage.error("服务器响应失败！")
  } finally {
    loading.value = false
    writerLoading.value = false
  }
}

const getComments = async () => {
  try {
    const data = await getCommentsService(commentConditions.value)
    if (data.code === 0) {
      comments.value = data.data.items
      commentTotal.value = data.data.total
    } else {
      comments.value = []
      commentTotal.value = 0
    }
  } catch (e) {
    ElMessage.error("服务器响应失败！")
  }
}

const AddComment = async () => {
  if (!commentModel.value.content.trim()) return ElMessage.warning("评论内容不能为空")
  try {
    commentModel.value.userId = userInfoStore().id
    const res = await addCommentService(commentModel.value)
    if (res.code === 0) {
      ElMessage.success("发表成功")
      commentModel.value.content = ""
      commentConditions.value.pageNum = 1
      await getComments()
    } else {
      if (res.message.error) {
        ElMessage.error("发表失败! nn" + res.message.error)
      }
    }
  } catch (e) {
    ElMessage.error("服务器响应失败！")
  }
}

const DeleteComment = (commentId) => {
  ElMessageBox.confirm('确定要删除这条评论吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    const res = await deleteCommentService(commentId, props.id)
    if (res.code === 0) {
      ElMessage.success("已删除")
      await getComments()
    } else {
      ElMessage.error("删除失败!")
    }
  }).catch(() => {
    ElMessage.info("取消删除！")
  })
}

const onCurrentChange = (num) => {
  commentConditions.value.pageNum = num
  getComments()
}

const getTopArticles = async () => {
  try {
    const data = await getTopArticlesService()
    if (data.code === 0) topArticles.value = data.data
  } catch (e) {
    console.error(e)
  } finally {
    topArticleLoading.value = false
  }
}

const switchCommand = (command) => {
  if (command === "profile") router.push({name: "Home"})
  else if (command === "logout") {
    logoutService().finally(() => {
      userInfoStore().clearUserInfo()
      tokenStorage().clearToken()
      window.location.reload()
    })
  }
}

function formatDate(date) {
  const d = new Date(date);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const pushSearch = (type) => {
  searchConditions().state = 1
  if (type === 0) {
    searchConditions().searchCondition.search = searchCondition.value.search
    searchConditions().searchCondition.searchType = searchCondition.value.searchType
  } else if (type === 1) {
    searchConditions().searchCondition.nickName = articleInfo.value.createUserName || "unknown"
  } else if (type === 2) {
    searchConditions().searchCondition.categoryId = articleInfo.value.categoryId || -1
  }
  router.push({name: "PublicHome"})
}
</script>

<template>
  <div class="front-layout">
    <el-header class="custom-header">
      <div class="logo-area" @click="router.push({name: 'PublicHome'})">
        <div class="logo-img"></div>
      </div>
      <div class="spacer-div"></div>
      <div class="search-section">
        <el-switch v-model="searchCondition.searchType" active-value="1" inactive-value="0" active-text="作者"
                   inactive-text="标题"/>
        <el-input v-model="searchCondition.search" placeholder="搜索感兴趣的内容..." :prefix-icon="Search"
                  class="custom-search"/>
        <el-button :icon="Search" circle @click="pushSearch(0)"/>
      </div>
      <div class="spacer-div"></div>
      <div class="user-area-wrapper">
        <div v-if="isLogin" class="welcome-box">
          <span class="welcome-text">欢迎回来！
            <span class="username">{{ userInfoStore().nickname || "匿名用户" }}</span>
            <el-tag size="small" v-if="userInfoStore().type === 0" round type="primary"
                    style="margin-bottom: 2px" disable-transitions>站长</el-tag>
            <el-tag size="small" v-if="userInfoStore().type === 1" round type="warning"
                    style="margin-bottom: 2px" disable-transitions>作者</el-tag>
            <el-tag size="small" v-if="userInfoStore().type === 2" round type="info"
                    style="margin-bottom: 2px" disable-transitions>读者</el-tag>
          </span>
        </div>
        <div class="user-action">
          <el-button v-if="!isLogin" type="primary" plain round @click="router.push({name: 'Login'})">登录</el-button>
          <el-dropdown v-else @command="switchCommand">
            <span class="el-dropdown__box">
              <el-avatar :src="userInfoStore().userPicSrc || avatar"/>
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
        <el-main class="article-container" v-loading="loading">
          <div class="content-card-wrapper" v-if="articleInfo">
            <div class="article-detail-header">
              <h1 class="main-title">{{ articleInfo.title }}</h1>
              <div class="main-meta">
                <div class="meta-left">
                  <span class="item-nickname" @click="pushSearch(1)"><el-icon><User/></el-icon> {{
                      articleInfo.createUserName
                    }}</span>
                  <span class="item"><el-icon><Calendar/></el-icon> {{
                      formatDate(articleInfo.updateTime || articleInfo.createTime)
                    }}</span>
                  <span class="item"><el-icon><Timer/></el-icon> 阅读 {{ articleInfo.views }}</span>
                </div>
                <el-check-tag :checked="true" @click="pushSearch(2)">{{
                    articleInfo.categoryName || "未分类"
                  }}
                </el-check-tag>
              </div>
            </div>

            <div class="article-detail-cover" v-if="articleInfo.coverImgSrc">
              <el-image :src="articleInfo.coverImgSrc" fit="contain" :preview-src-list="[articleInfo.coverImgSrc]"/>
            </div>

            <div class="article-detail-content ql-editor" v-html="articleInfo.content"></div>

            <div class="comment-section">
              <div class="comment-divider">
                <span><el-icon><ChatLineRound/></el-icon> 评论交流 ({{ commentTotal }})</span>
              </div>

              <div class="comment-input-area">
                <div v-if="!canComment" class="comment-mask">
                  <router-link v-if="!isLogin" target="_blank" :to="{name:'Login'}" class="mask-link login-type">
                    去登录
                  </router-link>
                  <router-link v-else target="_blank" :to="{name:'ReaderInfo'}" class="mask-link info-type">去完善资料
                  </router-link>
                  <span class="mask-text">后发表评论</span>
                </div>

                <el-input v-model="commentModel.content" type="textarea" :rows="3" :disabled="!canComment"
                          placeholder="友善发言，记录思想..." maxlength="200" show-word-limit/>
                <div class="comment-btn-row">
                  <el-button type="primary" :disabled="!canComment" @click="AddComment">发表评论</el-button>
                </div>
              </div>

              <div class="comment-pagination" v-if="commentTotal > 0">
                <el-pagination small background layout="prev, pager, next" :total="commentTotal"
                               :page-size="commentConditions.pageSize" @current-change="onCurrentChange"/>
              </div>

              <div class="comment-list" v-if="comments.length > 0">
                <div v-for="item in comments" :key="item.id" class="comment-item">
                  <el-avatar :size="40" :src="item.userPicSrc || avatar"/>
                  <div class="comment-main">
                    <div class="comment-user-info">
                      <span class="comment-nickname">{{ item.nickName }}</span>
                      <span class="comment-time">{{ item.createTime }}</span>
                      <span class="comment-email">< {{ item.email }} ></span>
                    </div>
                    <div class="comment-content">{{ item.content }}</div>
                  </div>
                  <div class="comment-action"
                       v-if="isLogin && (userInfoStore().id === item.userId || userInfoStore().type === 0 || userInfoStore().nickname === articleInfo.createUserName)">
                    <el-button :icon="Delete" type="danger" link @click="DeleteComment(item.id)">删除</el-button>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无评论，快来抢沙发吧~" :image-size="80"/>
            </div>
          </div>
        </el-main>

        <el-aside width="320px" class="right-sidebar">
          <div class="sidebar-author-widget" v-loading="writerLoading">
            <div class="author-card-header">
              <div class="header-bg"></div>
              <el-avatar :size="80" :src="writerInfo.writerPicSrc || logo" class="author-avatar-main"/>
            </div>
            <div class="author-card-body">
              <div class="writer-nick">{{ writerInfo.nickName || '文迹作者' }}<p class="author-username">
                @{{ writerInfo.username }}</p></div>
              <div class="writer-stats-grid">
                <div class="stat-item"><span class="val">{{ writerInfo.publishCount || 0 }}</span><span
                    class="lab">发布文章</span></div>
                <div class="stat-item">
                  <span class="val">
                    <el-tag size="small" v-if="writerInfo.type === 0" type="danger" disable-transitions>站长</el-tag>
                    <el-tag size="small" v-else type="warning" disable-transitions>作者</el-tag>
                  </span>
                  <span class="lab">身份</span>
                </div>
              </div>
              <div class="writer-contact"><p>联系作者：{{ writerInfo.email || '未公开' }}</p></div>
            </div>
          </div>

          <div class="sidebar-list-card">
            <div class="card-header">
              <span>热门文章</span>
            </div>
            <div class="top-list" v-loading="topArticleLoading">
              <div v-if="topArticles && topArticles.length > 0" v-for="(post, index) in topArticles" :key="post.id"
                   class="top-item">
                <span :class="['rank-num', index < 3 ? 'top-three' : '']">{{ index + 1 }}</span>
                <router-link class="top-title" target="_blank" :to="{name: 'ArticleInfo', params: {id: post.id}}">
                  {{ post.title }}
                </router-link>
                <span class="top-views"><el-icon><View/></el-icon>{{ post.views }}</span>
              </div>
              <el-empty v-else description="暂无热门文章"></el-empty>
            </div>
          </div>
        </el-aside>
      </el-container>

      <el-footer class="custom-footer">文迹 2026 @Apkdio</el-footer>
    </el-container>
  </div>
</template>

<style lang="scss" scoped>
.front-layout {
  min-height: 100vh;
  background-color: #f0f5ff; // 沿用后台背景色
  scrollbar-gutter: stable;

  .outer-container {
    margin: 0 auto;
    padding: 0 20px;
    max-width: 1450px;
  }

  // Header 样式补全
  .custom-header {
    background: #f8fffe;
    height: 80px !important;
    display: flex;
    align-items: center;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
    z-index: 100;
    position: fixed;
    width: 100%;

    .logo-area {
      display: flex;
      align-items: center;
      height: 100%;
      cursor: pointer;

      .logo-img {
        width: 120px;
        height: 100%;
        background: url('@/assets/logo.png') no-repeat center / 120px auto;
        margin-bottom: 5px;
      }
    }

    .spacer-div {
      flex: 1;
    }

    .custom-search {
      width: 300px;

      :deep(.el-input__wrapper) {
        border-radius: 20px;
        background-color: #f5f7fa;
      }
    }

    .search-section {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-shrink: 0;
    }

    .user-area-wrapper {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      min-width: 260px;
      flex-shrink: 0;

      .welcome-box {
        margin-right: 10px;
        white-space: nowrap;

        .welcome-text {
          font-size: 15px;
          margin-left: auto;
          text-align: right;
          color: #666;

          .username {
            color: #000;
            font-weight: 500;
            padding-right: 5px;
            text-align: center;
          }
        }
      }
    }
  }

  .main-wrapper {
    margin-top: 100px;
    gap: 20px;
    align-items: flex-start;
  }

  // 左侧内容区
  .article-container {
    padding: 0;

    .content-card-wrapper {
      background: #ffffff;
      border-radius: 12px;
      padding: 10px 40px;
      min-height: 686px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);

      .main-title {
        font-size: 30px;
        font-weight: 700;
        margin-bottom: 20px;
        color: #303133;
      }

      .main-meta {
        display: flex;
        justify-content: space-between;
        border-bottom: 1px solid #eee;
        padding-bottom: 15px;
        margin-bottom: 30px;
        color: #909399;
        font-size: 13px;

        .meta-left {
          display: flex;
          gap: 20px;

          .item-nickname {
            display: inline-flex;
            align-items: center;
            gap: 4px;

            &:hover {
              color: #409eff;
              cursor: pointer;
            }
          }

          .item {
            display: inline-flex;
            align-items: center;
            gap: 4px;
          }
        }
      }

      .article-detail-cover {
        border-radius: 8px;
        overflow: hidden;
        margin-bottom: 30px;

        .el-image {
          width: 100%;
          height: 480px;
        }
      }

      .article-detail-content {
        font-size: 17px;
        line-height: 1.9;

        :deep(p) {
          margin-bottom: 1.5em;
        }
      }
    }
  }

  // 右侧侧边栏补全
  .right-sidebar {
    display: flex;
    flex-direction: column;
    gap: 20px;

    .sidebar-author-widget {
      background: #ffffff;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);

      .author-card-header {
        position: relative;

        .header-bg {
          height: 75px;
          background: linear-gradient(135deg, #4ca1af 0%, #3973ac 100%);
        }

        .author-avatar-main {
          position: absolute;
          left: 50%;
          transform: translateX(-50%);
          bottom: -40px;
          border: 4px solid #fff;
        }
      }

      .author-card-body {
        padding: 50px 20px 10px;
        text-align: center;

        .writer-nick {
          font-size: 18px;
          font-weight: 700;

          .author-username {
            font-size: 13px;
            color: #94a3b8;
            margin-top: 5px;
          }
        }

        .writer-stats-grid {
          display: grid;
          grid-template-columns: 1fr 1fr;
          background: #f8fafc;
          border-radius: 8px;
          padding: 12px 0;
          margin: 15px 0;

          .stat-item {
            display: flex;
            flex-direction: column;

            .val {
              font-weight: 600;
            }

            .lab {
              font-size: 11px;
              color: #94a3b8;
            }
          }
        }
      }

      .writer-contact {
        font-size: 12px;
        color: #94a3b8;
      }
    }

    .sidebar-list-card {
      background: #ffffff;
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);

      .card-header {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: bold;
        font-size: 16px;
        color: #333;
        padding-bottom: 15px;
        border-bottom: 2px solid #f4f6f9;
        margin-bottom: 15px;

        .el-icon {
          color: #e6a23c;
        }
      }

      .top-item {
        display: flex;
        align-items: center;
        padding: 10px 0;
        font-size: 14px;

        .rank-num {
          width: 20px;
          height: 20px;
          background: #f0f2f5;
          color: #909399;
          text-align: center;
          line-height: 20px;
          border-radius: 4px;
          margin-right: 12px;
          font-size: 12px;

          &.top-three {
            background: #409eff;
            color: #fff;
          }
        }

        .top-title {
          text-decoration: none;
          flex: 1;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          color: #555;

          &:hover {
            color: #409eff;
          }
        }

        .top-views {
          color: #acacac;
          font-size: 12px;
          display: inline-flex;
          align-items: center;
          gap: 4px;
          margin-left: 8px;
          line-height: 10px;
        }
      }
    }
  }

  // 评论区样式优化
  .comment-section {
    margin-top: 60px;

    .comment-divider {
      font-size: 18px;
      font-weight: 600;
      border-bottom: 2px solid #f2f2f2;
      padding-bottom: 10px;
      margin-bottom: 20px;
    }

    .comment-input-area {
      position: relative;

      .comment-mask {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 80px;
        z-index: 10;
        background: rgba(255, 255, 255, 0.7);
        backdrop-filter: blur(4px);
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 4px;
        border: 1px solid rgba(255, 255, 255, 0.3);

        .mask-link {
          font-size: 14px;
          font-weight: bold;
          text-decoration: none;

          &.login-type {
            color: #409eff;
          }

          &.info-type {
            color: #e6a23c;
          }
        }
      }

      .comment-btn-row {
        display: flex;
        justify-content: flex-end;
        margin-top: 10px;
      }
    }

    .comment-list .comment-item {
      display: flex;
      gap: 15px;
      padding: 20px 0;
      border-bottom: 1px solid #f2f2f2;

      .comment-main {
        flex: 1;

        .comment-user-info {
          margin-bottom: 8px;

          .comment-nickname {
            font-weight: bold;
            margin-right: 10px;
          }

          .comment-time {
            font-size: 12px;
            color: #999;
            margin-right: 20px;
          }

          .comment-email {
            font-size: 12px;
            color: #b1b1b1;
          }
        }

        .comment-content {
          font-size: 15px;
          color: #333;
          line-height: 1.6;
        }
      }
    }
  }

  .custom-footer {
    text-align: center;
    color: #999;
    font-size: 13px;
    padding: 30px 0;
  }
}

.el-dropdown__box {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  outline: none !important;

  &:hover {
    background: #ededed;
    cursor: pointer;
  }
}

// ========== 移动端适配 (宽度 ≤ 768px) ==========
@media (max-width: 768px) {
  .front-layout {
    .custom-header {
      height: auto !important;
      min-height: 70px;
      padding: 10px 12px;
      flex-wrap: wrap;
      margin-bottom: 0;

      .logo-area {
        flex-shrink: 0;
        min-width: 80px;
        margin-right: auto;

        .logo-img {
          width: 60px;
          height: 30px;
          background: url('@/assets/logo.png') no-repeat center / cover;
        }
      }

      .spacer-div {
        display: none;
      }

      .search-section {
        order: 3;
        width: 100%;
        margin-top: 12px;
        gap: 8px;

        .el-switch {
          transform: scale(0.85);
          transform-origin: left;
        }

        .custom-search {
          flex: 1;
          width: auto;
        }

        .el-button {
          padding: 8px;
        }
      }

      .user-area-wrapper {
        min-width: auto;
        flex-shrink: 1;
        gap: 8px;

        .welcome-box {
          .welcome-text {
            display: inline-flex;
            font-size: 13px;

            .username {
              max-width: 80px;
              overflow: hidden;
              text-overflow: ellipsis;
              vertical-align: bottom;
            }
          }
        }
      }
    }

    .main-wrapper {
      margin-top: 130px; // 减小头部固定间距
      flex-direction: column; // 右侧边栏下移
      gap: 16px;

      .article-container {
        width: 100%;
        padding: 0;

        .content-card-wrapper {
          padding: 20px 16px; // 减小内边距
          min-height: auto;

          .main-title {
            font-size: 24px; // 标题缩小
            margin-bottom: 12px;
          }

          .main-meta {
            flex-direction: column; // 元信息纵向排列
            gap: 10px;
            padding-bottom: 12px;

            .meta-left {
              flex-wrap: wrap;
              gap: 12px;
              font-size: 12px;
            }
          }

          .article-detail-cover .el-image {
            height: auto;
            max-height: 240px; // 封面图高度限制
          }

          .article-detail-content {
            font-size: 16px; // 正文略缩小
            line-height: 1.7;
          }
        }
      }

      .right-sidebar {
        width: 100%;
        gap: 16px;

        .sidebar-author-widget .author-card-body {
          padding: 50px 12px 16px;
        }

        .sidebar-list-card {
          padding: 16px;
        }
      }
    }

    // 评论区适配
    .comment-section {
      margin-top: 40px;

      .comment-divider {
        font-size: 16px;
      }

      .comment-input-area {
        .comment-mask {
          height: 100px; // 遮罩区域高度适应文本域
        }
      }

      .comment-list .comment-item {
        gap: 12px;

        .comment-main {
          .comment-user-info {
            flex-wrap: wrap;

            .comment-time,
            .comment-email {
              display: inline-block;
              margin-top: 4px;
            }
          }

          .comment-content {
            font-size: 14px;
          }
        }
      }
    }

    .custom-footer {
      padding: 20px 0;
    }
  }
}
</style>