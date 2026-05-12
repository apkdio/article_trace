<script setup>
import {
  CaretBottom,
  Crop,
  EditPen,
  HomeFilled,
  Management,
  Promotion,
  SwitchButton, Tools,
  User,
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
          :default-active="route.path === '/' || route.path === '' ? '/mainPage' : route.path"
          active-text-color="#409eff"
          background-color="transparent"
          text-color="#5c6b77"
          :router="true"
          unique-opened
          class="custom-menu"
      >
        <el-menu-item index="/home">
          <el-icon>
            <HomeFilled/>
          </el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/article/category">
          <el-icon>
            <Management/>
          </el-icon>
          <span>文章分类</span>
        </el-menu-item>
        <el-menu-item index="/article/list">
          <el-icon>
            <Promotion/>
          </el-icon>
          <span>文章管理</span>
        </el-menu-item>

        <el-sub-menu index="/user">
          <template #title>
            <el-icon>
              <UserFilled/>
            </el-icon>
            <span>个人中心</span>
          </template>
          <el-menu-item index="/user/info">
            <el-icon>
              <User/>
            </el-icon>
            <span>基本资料</span>
          </el-menu-item>
          <el-menu-item index="/user/updateLogo">
            <el-icon>
              <Crop/>
            </el-icon>
            <span>更换头像</span>
          </el-menu-item>
          <el-menu-item index="/user/updatePassword">
            <el-icon>
              <EditPen/>
            </el-icon>
            <span>修改密码</span>
          </el-menu-item>
          <el-menu-item index="/user/accountManage" v-if="userInfoStore().type === 0">
            <el-icon>
              <Tools/>
            </el-icon>
            <span>账户管理</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header>
        <div class="header-welcome">
          <span class="greet">{{ timeCheck }}</span>
          <span class="user-name">{{ userInfoStore().nickname ? userInfoStore().nickname : '匿名作者' }}</span>
          <el-tag v-if="userInfoStore().type === 0" size="small" type="primary" style="margin-left: 6px"
                  disable-transitions>站长
          </el-tag>
          <el-tag v-else-if="userInfoStore().type === 1" size="small" type="warning" style="margin-left: 6px"
                  disable-transitions>作者
          </el-tag>
          <el-tag v-else size="small" type="danger" style="margin-left: 6px" disable-transitions>读者</el-tag>
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
</style>