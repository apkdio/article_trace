<script setup>
import axios from "axios";
import {onMounted, ref} from "vue";
import {Calendar, CollectionTag, Document, Message, Postcard, User} from "@element-plus/icons-vue";
import {userInfoStore} from "@/stores/userInfo.js";
import router from "@/router/index.js";
import {checkType} from "@/api/user.js";

const isLoading = ref(true);

const hitokoto = ref({
  hitokoto: "",
  from: ""
})

const getHitokoto = async () => {
  isLoading.value = true;
  try {
    // 3 秒超时：axios 默认永不超时，而这里在 getUserInfo 的 finally 里被 await，
    // 悬挂时整个首页陪它白等——兜底文案要等浏览器自己断开才轮到
    const {data} = await axios.get('https://v1.hitokoto.cn', {timeout: 3000});
    hitokoto.value = data;
  } catch (error) {
    hitokoto.value = {
      hitokoto: "与其感慨路难行，不如现在就出发。",
      from: "文迹 @2026"
    };
  } finally {
    isLoading.value = false;
  }
}
const getUserInfo = async () => {
  if (!checkType([0, 1])) router.push({name: "ReaderHome"})
  try {
    if (userInfoStore().type === 0) {
      await userInfoStore().fetchArticlesCount()
    }
    await userInfoStore().fetchCategories()
  } catch (error) {
    ElMessage.error("获取用户信息失败！")
  } finally {
    await getHitokoto()
  }
}
onMounted(() => {
  getUserInfo()
})
</script>

<template>
  <div
      class="dashboard-container"
      v-loading="isLoading"
      element-loading-text="文迹数据加载中..."
      element-loading-background="rgba(245, 242, 235, 0.7)"
  >
    <template v-if="!isLoading">
      <!-- 保持原有结构，动画是通过 CSS animation 实现的 -->
      <div class="top-section">
        <h2 class="welcome-title">欢迎回来，追寻文字之迹！</h2>
        <div class="hitokoto-box">
          <span class="quote-mark left">"</span>
          <p class="hitokoto-text">{{ hitokoto.hitokoto }}</p>
          <span class="quote-mark right">"</span>
          <div class="hitokoto-from">— 「 {{ hitokoto.from }} 」</div>
        </div>
      </div>

      <div class="bottom-section">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="8">
            <div class="grid-content card admin-stat-card" v-if="userInfoStore().type === 0">
              <div class="card-header">
                <div class="header-left">
                  <el-icon>
                    <Document/>
                  </el-icon>
                  <el-tag :type="userInfoStore().waitTotal > 0 ?'danger':'success'" effect="dark" size="small" round>
                    待审核：{{ userInfoStore().waitTotal }}
                  </el-tag>

                </div>
                <span>文章统计</span>
              </div>

              <div class="card-body admin-body">
                <div class="main-data">
                  <div class="main-num">{{ userInfoStore().allArticles }}</div>
                  <div class="main-label">全站文章</div>
                </div>

                <div class="stat-grid">
                  <div class="grid-item">
                    <span class="item-num">{{ userInfoStore().articlesTotal }}</span>
                    <span class="item-label">我的发布</span>
                  </div>
                  <div class="grid-item">
                    <span class="item-num">{{ userInfoStore().accessTotal || 0 }}</span>
                    <span class="item-label">审核通过</span>
                  </div>
                  <div class="grid-item">
                    <span class="item-num">{{ userInfoStore().rejectTotal || 0 }}</span>
                    <span class="item-label">驳回</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="grid-content card" v-else>
              <div class="card-header">
                <el-icon>
                  <Document/>
                </el-icon>
                <span>文章统计</span>
              </div>
              <div class="card-body stat-body">
                <p class="data-num">{{ userInfoStore().articlesTotal }}</p>
                <p class="data-label">发布文章</p>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="8">
            <div class="grid-content card">
              <div class="card-header">
                <el-icon>
                  <CollectionTag/>
                </el-icon>
                <span>分类概览</span>
              </div>
              <div class="card-body stat-body">
                <p class="data-num">{{ userInfoStore().categories }}</p>
                <p class="data-label">文章分类总数</p>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :sm="8">
            <div class="grid-content card author-card">
              <div class="card-header">
                <el-icon>
                  <User/>
                </el-icon>
                <span>个人信息</span>
              </div>
              <div class="card-body author-info-list">
                <div class="info-item">
                  <el-icon>
                    <Postcard/>
                  </el-icon>
                  <span class="label">账号：</span>
                  <span class="value">{{ userInfoStore().username }}</span>
                </div>
                <div class="info-item">
                  <el-icon>
                    <User/>
                  </el-icon>
                  <span class="label">昵称：</span>
                  <span class="value" :class="{ 'is-empty': !userInfoStore().nickname }">
                    {{ userInfoStore().nickname || '未设置昵称' }}
                  </span>
                </div>
                <div class="info-item">
                  <el-icon>
                    <Message/>
                  </el-icon>
                  <span class="label">邮箱：</span>
                  <span class="value" :class="{ 'is-empty': !userInfoStore().email }">
                    {{ userInfoStore().email || '未绑定邮箱' }}
                  </span>
                </div>
                <div class="info-item">
                  <el-icon>
                    <Calendar/>
                  </el-icon>
                  <span class="label">加入：</span>
                  <span class="value">{{ userInfoStore().createTime }}</span>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
/* 容器布局 - 确保内容不撑出滚动条 */
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%; // 确保占满父容器
}

/* 保留原有的动画效果 */


/* 将动画应用到相应的元素上 */
.top-section {
  background: var(--at-bg-card);
  border-radius: 12px;
  padding: 35px 20px;
  text-align: center;
  border: 1px solid var(--at-border-warm);
  box-shadow: 0 2px 12px 0 rgba(44, 42, 38, 0.03);
  animation: fadeIn 0.6s ease-out; /* 保留动画 */

  .welcome-title {
    font-size: 26px;
    font-weight: 600;
    color: var(--at-text-main);
    margin-bottom: 35px;
    letter-spacing: 1px;
  }

  .hitokoto-box {
    position: relative;
    max-width: 600px;
    margin: 0 auto;
    padding: 0 50px;

    .hitokoto-text {
      font-size: 20px;
      line-height: 1.8;
      color: var(--at-text-regular);
      font-family: "PingFang SC", "Microsoft YaHei", serif;
      margin: 0;
    }

    .hitokoto-from {
      margin-top: 20px;
      font-size: 15px;
      color: var(--at-text-secondary);
      font-style: italic;
    }

    .quote-mark {
      position: absolute;
      font-size: 55px;
      color: #d5cfc2;
      font-family: "Georgia", serif;
      user-select: none;

      &.left {
        top: -25px;
        left: 0;
      }

      &.right {
        bottom: -15px;
        right: 0;
      }
    }
  }
}

/* 站长特有统计卡片样式 */
.admin-body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  padding-bottom: 10px;

  .main-data {
    text-align: center;
    margin-bottom: 20px;

    .main-num {
      font-size: 38px;
      font-weight: 800;

      line-height: 1;
    }

    .main-label {
      font-size: 13px;
      color: var(--at-text-secondary);
      margin-top: 8px;
    }
  }

  .stat-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
    background: var(--at-bg-stat);
    border-radius: 8px;
    padding: 12px 5px;

    .grid-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      position: relative;

      &:not(:last-child)::after {
        content: "";
        position: absolute;
        right: 0;
        top: 20%;
        height: 60%;
        width: 1px;
        background: var(--at-border-warm);
      }

      .item-num {
        font-size: 18px;
        font-weight: 600;
        color: var(--at-text-main);
      }

      .item-label {
        font-size: 11px;
        color: var(--at-text-secondary);
        margin-top: 4px;
      }
    }
  }
}

/* 修正 header 布局，让 Tag 靠右 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.bottom-section {
  animation: fadeIn 0.8s ease-out; /* 保留动画 */

  .grid-content {
    background: var(--at-bg-card);
    border-radius: 12px;
    height: 100%;
    min-height: 200px;
    padding: 18px;
    border: 1px solid var(--at-border-warm);
    position: relative;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);

    &::before {
      content: "";
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 4px;
      background: linear-gradient(to bottom, #2b7a78, #57a09d);
      opacity: 0.85;
      transition: opacity 0.3s;
    }

    .card-header {
      display: flex;
      align-items: center;
      gap: 10px;
      color: var(--at-celadon);
      font-weight: 600;
      font-size: 16px;
      border-bottom: 1px solid var(--at-divider-warm);
      padding-bottom: 15px;
      margin-bottom: 20px;
    }
  }
}

.stat-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;

  .data-num {
    font-size: 42px;
    font-weight: 700;
    color: var(--at-text-main);
    margin: 0;
    font-family: 'Arial', sans-serif;
  }

  .data-label {
    font-size: 14px;
    color: var(--at-text-secondary);
    margin-top: 10px;
  }
}

.author-info-list {
  display: flex;
  flex-direction: column;
  gap: 14px;

  .info-item {
    display: flex;
    align-items: center;
    font-size: 14px;

    .el-icon {
      margin-right: 12px;
      color: var(--at-text-secondary);
      font-size: 17px;
    }

    .label {
      color: var(--at-text-secondary);
      width: 55px;
      flex-shrink: 0;
    }

    .value {
      color: var(--at-text-main);
      font-weight: 500;

      &.is-empty {
        color: #c0bbb0;
        font-style: italic;
        font-weight: normal;
      }
    }
  }
}

.card {
  cursor: pointer;

  &:hover {
    transform: translateY(-6px);
    border-color: var(--at-celadon);
    box-shadow: 0 15px 30px rgba(43, 122, 120, 0.10);

    &::before {
      opacity: 1;
    }
  }
}

/* 响应式适配 */
@media (max-width: 768px) {
  .top-section {
    padding: 30px 15px;
  }
  .bottom-section .el-col {
    margin-bottom: 20px;
  }
}
</style>