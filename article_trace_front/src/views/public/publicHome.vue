<script setup>
import {ref, onMounted} from 'vue'
import {Search, View, SwitchButton, CaretBottom, User, Refresh, Timer} from '@element-plus/icons-vue'
import {userInfoStore} from "@/stores/userInfo.js";
import avatar from "@/assets/defaultLogo.jpg";
import cover from "@/assets/defaultCover.jpg"
import {
  getMasterInfoService,
  getPublicArticlesService,
  getTopArticlesService
} from "@/api/article.js";
import router from "@/router/index.js";
import {logoutService} from "@/api/user.js";
import {tokenStorage} from "@/stores/tokenStorage.js";
import {searchConditions} from "@/stores/searchConditions.js";
import logo from "@/assets/defaultLogo.jpg";
import {loginCheckPublic} from "@/utils/loginCheck.js";

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

onMounted(async () => {
  document.title = "文迹 - 让每一份文章都有迹可循"
  try {
    await loginCheckPublic()
  } catch (e) {
    if(e.message === "not login") return
    if (!tokenStorage().processed)
      ElMessage.warning("登录已失效！")
    userInfoStore().clearUserInfo()
    tokenStorage().clearToken()
    tokenStorage().processed = true
  } finally {
    if (userInfoStore().username !== "") isLogin.value = true
    await getTopArticles()
    await getArticles()
    await getMasterInfo()
  }
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
const getArticles = async () => {
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
    if (data.code === 0) {
      articleList.value = data.data.items
      total.value = data.data.total
    } else {
      articleList.value = []
    }
  } catch (e) {
    articleList.value = []
    ElMessage.error("服务器响应失败！")
  } finally {
    setTimeout(() => loading.value = false, 100)
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
      logoutService().then(() => {
        ElMessage.success("用户已登出！")
      }).catch(() => {
        ElMessage.warning("服务端未响应！执行本地登出！")
      }).finally(async () => {
        setTimeout(() => {
          userInfoStore().clearUserInfo()
          tokenStorage().clearToken()
          window.location.reload()
        }, 500)
      })
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
  getArticles()
  setTimeout(() => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  }, 100)
}

function formatDate(date) {
  const d = new Date(date);
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
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
        <el-switch
            v-model="searchCondition.searchType"
            size="default"
            active-value="1"
            inactive-value="0"
            active-text="作者"
            inactive-text="标题"
        />
        <el-input
            v-model="searchCondition.search"
            placeholder="搜索感兴趣的内容..."
            :prefix-icon="Search"
            class="custom-search"
        />
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
      </div>

      <div class="spacer-div"></div>

      <div class="user-area-wrapper">
        <div v-if="isLogin" class="welcome-box">
      <span class="welcome-text">欢迎回来！
        <span class="username">{{ userInfoStore().nickname ? userInfoStore().nickname : "匿名用户" }}
        </span>
        <el-tag size="small" v-if="userInfoStore().type === 0" round style="margin-bottom: 2px"
                type="primary" disable-transitions>站长</el-tag>
        <el-tag size="small" v-if="userInfoStore().type === 1" round style="margin-bottom: 2px"
                type="warning" disable-transitions>作者</el-tag>
        <el-tag size="small" v-if="userInfoStore().type === 2" round style="margin-bottom: 2px"
                type="info" disable-transitions>读者</el-tag>
      </span>
        </div>

        <div class="user-action">
          <el-button v-if="!isLogin" type="primary" plain round @click="router.push({name: 'Login'})">登录</el-button>
          <el-dropdown placement="bottom-end" v-if="isLogin" @command="switchCommand">
        <span class="el-dropdown__box">
          <el-avatar :src="userInfoStore().userPicSrc==='' ? avatar : userInfoStore().userPicSrc"/>
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
                  <el-image :src="item.coverImgSrc!==null?item.coverImgSrc:cover" fit="cover"/>
                </router-link>
              </div>
              <div class="article-info">
                <router-link style="text-decoration: none" target="_blank"
                             :to="{name: 'ArticleInfo', params: {id: item.id}}">
                  <h2 class="title">{{ item.title }}</h2>
                </router-link>
                <p class="summary">{{ item.content }}</p>
                <div class="meta">
                  <el-check-tag v-if="item.categoryName !==null" @click="searchArticleInCategory(item.categoryId)"
                                :checked="true">{{ item.categoryName }}
                  </el-check-tag>
                  <el-check-tag v-else type="warning">未分类</el-check-tag>
                  <span @click="searchArticleInWriter(item.createUserName)" class="article-writer">
                    <el-icon><User/></el-icon> {{ item.createUserName }}</span>
                  <span><el-icon><View/></el-icon> {{ item.views ? item.views : 0 }}</span>
                  <span>
                    <el-icon><Timer/></el-icon>
                    {{ formatDate(item.updateTime ? item.updateTime : item.createTime) }}</span>
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
          <div class="sidebar-author-widget" v-loading="masterLoading">
            <div class="author-card-header">
              <div class="header-bg"></div>
              <el-avatar :size="80" :src="masterInfo.writerPicSrc || logo" class="author-avatar-main"/>
            </div>
            <div class="author-card-body">
              <div class="writer-nick">{{ masterInfo.nickName || '文迹站长' }}<p class="author-username">
                @{{ masterInfo.username }}</p></div>
              <div class="writer-stats-grid">
                <div class="stat-item"><span class="val">{{ masterInfo.publishCount || 0 }}</span><span
                    class="lab">发布文章</span></div>
                <div class="stat-item">
                  <span class="val">
                    <el-tag size="small" type="danger" disable-transitions>本站负责人</el-tag>
                  </span>
                  <span class="lab">身份</span>
                </div>
              </div>
              <div class="writer-contact"><p>邮箱：{{ masterInfo.email || '未公开' }}</p></div>
            </div>
          </div>
          <div class="sidebar-card">
            <div class="card-header">
              <span>热门文章</span>
            </div>
            <div class="top-list" v-loading="topArticleLoading" element-loading-text="正在加载热门文章列表...">
              <div v-if="topArticles && topArticles.length > 0" v-for="(post, index) in topArticles" :key="post.id"
                   class="top-item">
                <span :class="['rank-num', index < 3 ? 'top-three' : '']">{{ index + 1 }}</span>
                <router-link class="top-title" target="_blank"
                             :to="{name: 'ArticleInfo', params: {id: post.id}}" :title="post.title">{{ post.title }}
                </router-link>
                <span class="top-views"><el-icon>
                  <View/></el-icon>{{ post.views }}</span>
              </div>
              <el-empty v-else description="暂无热门文章"></el-empty>
            </div>
          </div>
        </el-aside>
      </el-container>

      <el-footer class="custom-footer">
        文迹 2026 @Apkdio
      </el-footer>
    </el-container>
  </div>
</template>

<style lang="scss" scoped>
.front-layout {
  min-height: 100vh;
  background-color: #f0f5ff; // 沿用后台背景色

  .outer-container {
    margin: 0 auto;
    padding: 0 20px;
    max-width: 1450px;
  }

  // Header 样式
  .custom-header {
    background: #f8fffe;
    height: 80px !important;
    display: flex;
    align-items: center;
    box-sizing: border-box;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
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
        color: #409eff;
      }
    }

    .spacer-div {
      flex: 1;
    }

    .welcome-text {
      font-size: 15px;
      margin-left: auto;
      text-align: right;
      color: #666;
    }

    .custom-search {
      width: 300px;

      :deep(.el-input__wrapper) {
        border-radius: 20px;
        background-color: #f5f7fa;
      }
    }

    .user-profile {
      display: flex;
      align-items: center;
      gap: 12px;

      .welcome-text {
        font-size: 14px;
        color: #666;
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
      background: #ffffff;
      border-radius: 12px;
      padding: 24px;
      min-height: 656px;
      display: flex;
      flex-direction: column;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);
    }

    .article-card {
      display: flex;
      padding: 20px 0;
      border-bottom: 1px solid #dcdcdc;
      transition: all 0.3s;


      .article-info {
        flex: 1;

        .title {
          margin: 0 0 10px 0;
          font-size: 20px;
          color: #303133;
          cursor: pointer;
          text-decoration: none;

          &:hover {
            color: #409eff;
          }
        }

        .summary {
          color: #606266;
          font-size: 14px;
          line-height: 1.6;
          margin-bottom: 15px;
          min-height: 45px;
        }

        .meta {
          display: flex;
          align-items: center;
          gap: 15px;
          color: #909399;
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
        border: 1px solid #dcdfe6;
        border-radius: 8px;
        margin-right: 20px;
        padding: 3px;

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
    &:hover {
      color: #3c3c3c;
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

    .sidebar-author-widget {
      background: #ffffff;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);

      .author-card-header {
        position: relative;

        .header-bg {
          height: 75px;
          background: linear-gradient(135deg, #6addce 0%, #7a92f6 100%);
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

    .sidebar-card {
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
          cursor: pointer;

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

  .username {
    color: #000000;
    text-align: center;
    font-weight: 500;
    padding-right: 5px;
  }

  .custom-footer {
    text-align: center;
    color: #999;
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

  .welcome-box {
    margin-right: 10px;
    white-space: nowrap; /* 禁止文字换行导致高度抖动 */
  }
}

.el-dropdown__box {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  outline: none !important;

  &:hover {
    background: #ededed;
  }
}

.user-action {
  display: flex;
  align-items: center;
  width: auto; /* 去掉之前的固定 70px，由 wrapper 统一控制 */
}

.category-info {
  font-weight: bold;
  border-bottom: 1px solid #989898;
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

        .el-switch {
          transform: scale(0.85);
          transform-origin: left;
        }

        .custom-search {
          flex: 1;
          width: auto;                  // 让输入框自动填满
          :deep(.el-input__wrapper) {
            border-radius: 20px;
          }
        }

        .el-button {
          padding: 8px;                // 缩小按钮内边距
        }
      }

      .user-area-wrapper {
        min-width: auto;
        flex-shrink: 1;
        gap: 8px;

        .welcome-box {
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

        .sidebar-author-widget {
          .author-card-body {
            padding: 50px 12px 16px;

            .writer-stats-grid {
              padding: 8px 0;
            }
          }
        }

        .sidebar-card {
          padding: 16px;

          .top-item {
            .top-title {
              font-size: 13px;
            }
          }
        }
      }
    }

    .custom-footer {
      font-size: 12px;
      padding: 20px 0 10px;
    }
  }
}
</style>