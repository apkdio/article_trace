<script setup>
import {
  CaretBottom,
  Crop,
  EditPen,
  HomeFilled,
  SwitchButton, User,
  UserFilled
} from '@element-plus/icons-vue'
import avatar from '@/assets/defaultLogo.jpg'
import {onMounted, ref} from "vue";
import {checkTime} from '@/utils/timeCheck.js'
import {useRoute} from 'vue-router'
import {userInfoStore} from "@/stores/userInfo.js";
import {tokenStorage} from "@/stores/tokenStorage.js";
import router from "@/router/index.js";
import {logoutService} from "@/api/user.js";

document.title = "文迹 - 个人中心"
const timeCheck = ref()
const route = useRoute()

onMounted(() => {
  timeCheck.value = checkTime()
})

const logout = (command) => {
  switch (command) {
    case "back":
      router.push({name: "PublicHome"})
      break;
    case "logout":
      logoutService().then(() => {
        ElMessage.success("用户已登出！")
      }).catch(() => {
        ElMessage.warning("服务端未响应！执行本地登出！")
      }).finally(async () => {
        await userInfoStore().clearUserInfo()
        await tokenStorage().clearToken()
        router.push({name: "PublicHome"})
      })
  }
}
</script>

<template>
  <el-container class="layout-container">
    <el-aside width="200px">
      <div class="el-aside__logo"></div>
      <el-menu
          :default-active="route.path === '/reader/home' ? '/reader/info' : route.path"
          active-text-color="#409eff"
          background-color="transparent"
          text-color="#5c6b77"
          :router="true"
          unique-opened
          class="custom-menu"
      >
        <el-sub-menu index="/reader">
          <template #title>
            <el-icon>
              <UserFilled/>
            </el-icon>
            <span>个人中心</span>
          </template>
          <el-menu-item index="/reader/info">
            <el-icon>
              <User/>
            </el-icon>
            <span>基本资料</span>
          </el-menu-item>
          <el-menu-item index="/reader/updateLogo">
            <el-icon>
              <Crop/>
            </el-icon>
            <span>更换头像</span>
          </el-menu-item>
          <el-menu-item index="/reader/updatePassword">
            <el-icon>
              <EditPen/>
            </el-icon>
            <span>修改密码</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header>
        <div class="header-welcome">
          <span class="greet">{{ timeCheck }}</span>
          <span class="user-name">{{ userInfoStore().nickname ? userInfoStore().nickname : '匿名读者' }}</span>
          <el-tag size="small" type="info" style="margin-left: 6px" disable-transitions>读者</el-tag>
        </div>
        <div class="last-time-container">
          <p class="label">上次登录时间</p>
          <p class="value">{{ userInfoStore().lastLogin }}</p>
        </div>
        <el-dropdown placement="bottom-end" @command="logout">
          <span class="el-dropdown__box">
            <el-avatar :src="userInfoStore().userPicSrc==='' ? avatar:userInfoStore().userPicSrc"/>
            <el-icon class="arrow"><CaretBottom/></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="back" :icon="HomeFilled">返回</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main>
        <div class="router-content">
          <router-view></router-view>
        </div>
      </el-main>
      <el-footer>文迹 2026 @Apkdio</el-footer>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
  background-color: #f4f6f9;
  display: flex; // 关键：使用flex布局

  .el-aside {
    background-color: #ebedf0;
    border-right: 1px solid #dcdfe6;
    display: flex;
    flex-direction: column;

    &__logo {
      height: 80px;
      margin: 10px 0;
      background: url('@/assets/logo.png') no-repeat center / 90px auto;
      opacity: 0.85;
    }

    .custom-menu {
      border-right: none;

      /* 统一高度，防止展开时因为高度计算导致文字跳动 */
      :deep(.el-menu-item), :deep(.el-sub-menu__title) {
        margin: 2px 10px;
        border-radius: 6px;
        height: 48px !important; /* 固定高度 */
        line-height: 48px !important;
        display: flex;
        align-items: center;
        color: #5c6b77 !important;

        span {
          display: inline-block;
          height: 48px;
          line-height: 48px;
          vertical-align: middle;
        }
      }

      /* 子菜单容器优化 */
      :deep(.el-menu--inline) {
        background-color: rgba(0, 0, 0, 0.03) !important;
        border-radius: 6px;
        margin: 0 10px;
        padding: 0;
        overflow: hidden;

        .el-menu-item {
          padding-left: 48px !important;
          margin: 0; /* 内部项不要 margin，防止撑开瞬间的跳动 */
        }
      }

      :deep(.el-menu-item) {
        &.is-active {
          background-color: #ffffff !important;
          color: #409eff !important;
          font-weight: 600;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
        }
      }
    }
  }

  :deep(.el-sub-menu) {
    &.is-opened {
      > .el-menu--inline {
        display: block !important;
      }
    }
  }

  // 修改容器结构
  > .el-container {
    flex: 1; // 占据剩余空间
    display: flex;
    flex-direction: column;
    min-height: 0; // 关键：解决flex容器溢出问题
  }

  .el-header {
    background: #ffffff;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    height: 60px;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.05);
    z-index: 10;
    flex-shrink: 0; // 防止header被压缩

    .header-welcome {
      .greet {
        color: #777777;
        font-weight: bold;
        font-size: 16px;
        font-family: "Microsoft YaHei", serif;
      }

      .user-name {
        color: #000000;
        font-weight: bold;
        font-family: 等线, serif;
        margin-left: 5px;
        font-size: 18px;
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
  }

  .el-main {
    padding: 10px;
    flex: 1; // 占据剩余空间
    display: flex;
    flex-direction: column;
    min-height: 0; // 关键：解决flex容器溢出问题

    .router-content {
      background: #ffffff;
      flex: 1; // 使用flex填充剩余空间
      border-radius: 8px;
      padding: 24px;
      overflow: auto; // 只在内容过多时内部滚动
      position: relative; // 为加载动画准备
    }
  }

  .el-footer {
    display: flex;
    justify-content: center;
    font-size: 12px;
    color: #999;
    background: transparent;
    height: 22px !important;
  }
}

.last-time-container {
  margin-left: auto;
  margin-right: 10px;
  text-align: right;

  .label {
    font-size: 11px;
    color: #9f9f9f;
    margin: 0;
  }

  .value {
    font-size: 12px;
    color: #555555;
    margin: 0;
  }
}

@media (max-width: 768px) {
  .layout-container {
    /* 1. 侧边栏强制窄化 */
    .el-aside {
      width: 64px !important; // 统一宽度
      transition: width 0.3s;

      &__logo {
        background-size: 60px auto !important; // 缩小Logo
        height: 50px;
        margin: 5px 0;
      }
    }

    /* 2. 菜单项图标对齐核心逻辑 */
    :deep(.custom-menu) {
      // 清除所有菜单项和子菜单标题的默认缩进
      .el-menu-item,
      .el-sub-menu__title {
        padding: 0 !important;      // 关键：必须强制归零，否则图标会偏右
        margin: 4px 0 !important;   // 只有上下间距，左右归零
        display: flex !important;
        justify-content: center !important; // 水平居中
        align-items: center !important;     // 垂直居中
        width: 100% !important;

        span {
          display: none !important; // 隐藏文字
        }

        /* 移除子菜单的展开小箭头 */
        .el-sub-menu__icon-arrow {
          display: none !important;
        }

        /* 图标样式修正 */
        .el-icon {
          margin: 0 !important;     // 关键：移除图标右侧用于隔开文字的 margin
          font-size: 20px;
          color: #5c6b77;
        }

        &.is-active {
          .el-icon {
            color: #409eff; // 激活状态图标颜色
          }
        }
      }

      /* 3. 子菜单容器（展开后的垂直列表） */
      .el-menu--inline {
        margin: 0 !important;
        padding: 0 !important;
        background-color: rgba(0, 0, 0, 0.02) !important; // 轻微背景色区分

        .el-menu-item {
          padding-left: 0 !important; // 强制覆盖子项层级缩进
          background-color: transparent !important;

          &:hover {
            background-color: rgba(64, 158, 255, 0.1) !important;
          }
        }
      }
    }

    /* 4. 头部 Header 响应式优化 */
    .el-header {
      padding: 0 12px;

      .header-welcome {
        .greet { display: none; } // 隐藏“下午好”等问候
        .user-name {
          font-size: 14px;
          max-width: 80px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .el-tag { display: none; } // 隐藏读者标签节省空间
      }

      .last-time-container {
        display: none; // 隐藏登录时间
      }

      .el-dropdown__box {
        padding: 0;
        .arrow { display: none; } // 隐藏下拉箭头
      }
    }

    /* 5. 内容区与页脚适配 */
    .el-main {
      padding: 8px;
      .router-content {
        padding: 16px; // 缩小内边距
        border-radius: 4px;
      }
    }

    .el-footer {
      font-size: 10px;
      height: 20px !important;
      color: #ccc;
    }
  }
}
</style>