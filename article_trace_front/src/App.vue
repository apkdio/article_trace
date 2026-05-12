<script setup>
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const route = useRoute()
const transitionName = computed(() => {
  return route.meta.noTransition ? '' : 'page-slide-right'
})
</script>
<template>
  <el-config-provider :locale="zhCn">
    <router-view v-slot="{ Component }">
      <transition
          :name="transitionName"
          mode="out-in"
          appear
      >
        <component :is="Component"/>
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