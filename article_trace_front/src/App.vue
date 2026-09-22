<script setup>
import {computed, onMounted} from 'vue'
import {useRoute} from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import {userInfoStore} from "@/stores/userInfo.js";

const route = useRoute()
const transitionName = computed(() => {
  return route.meta.noTransition ? '' : 'page-slide-right'
})
// 顶层路由作为 key：跨顶层路由切换时强制重建，子路由切换时不重建
const viewKey = computed(() => {
  return route.matched.length > 0 ? (route.matched[0].path || route.path) : route.path
})

// 顶栏、门户页与文章页的头像与昵称都读这份 store，而它是持久化到 localStorage 的：
// 不在页面加载时回写一次，换过头像（或被站长审核通过）、改过昵称之后刷新页面仍是旧值。
// 这里拉**整份**用户信息而不是只拉头像：昵称还被用来判断「哪些文章 / 分类 / 评论是自己的」，
// 停在旧值会让归属判断错位（例如看不到自己文章的编辑按钮）。
// 未登录或后端 401 时静默失败即可——真正失效与否由路由与拦截器管。
onMounted(() => {
  if (userInfoStore().username) {
    userInfoStore().fetchUserInfo().catch(() => {
    })
  }
})
</script>
<template>
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component }">
      <transition :name="transitionName">
        <component :is="Component" :key="viewKey"/>
      </transition>
    </router-view>
  </el-config-provider>
</template>


<style>
.page-slide-right-enter-active,
.page-slide-right-leave-active {
  transition: all 0.5s ease-in-out;
}


.page-slide-right-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.page-slide-right-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* 辅助样式：确保切换时不会因为布局闪烁 */
#app {
  overflow-x: hidden; /* 防止横向位移产生的临时滚动条 */
}
</style>