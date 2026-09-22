<script setup>
import {onMounted, ref} from 'vue'
import {
    Avatar,
    Crop,
    EditPen,
    HomeFilled,
    Management,
    Promotion,
    Tools,
    User,
    UserFilled
} from '@element-plus/icons-vue'
import {useRoute} from 'vue-router'
import {userInfoStore} from "@/stores/userInfo.js";
import {getReviewSummary} from "@/api/review.js";
import UserLayout from '@/components/UserLayout.vue'

const route = useRoute()

// 审核中心的总待办数（菜单角标）；非站长拿不到也不算错——后端会拒，静默即可
const reviewPending = ref(0)
onMounted(async () => {
    if (userInfoStore().type !== 0) return
    try {
        const res = await getReviewSummary()
        if (res.code === 0) {
            reviewPending.value = Object.values(res.data || {}).reduce((sum, n) => sum + (n || 0), 0)
        }
    } catch (err) {
        // 角标拿不到不影响菜单
    }
})
</script>

<template>
    <UserLayout
        :default-active="route.path === '/' || route.path === '' ? '/mainPage' : route.path"
        default-nickname="匿名作者"
    >
        <template #menu>
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
            <el-menu-item index="/review/center" v-if="userInfoStore().type === 0">
                <el-icon>
                    <Avatar/>
                </el-icon>
                <span>审核中心</span>
                <!-- 用普通 span 而不是 el-badge：后者的角标是绝对定位，塞进 flex 行里会「浮」在半空。
                     注意必须自己给 align-self + 固定高度：菜单项是 flex 容器，不加就会被拉伸到整行高（48px），
                     红底铺满就变成一条长胶囊。 -->
                <span v-if="reviewPending > 0"
                      style="margin-left:auto;align-self:center;box-sizing:border-box;min-width:18px;height:18px;text-align:center;background:#f56c6c;color:#fff;border-radius:9px;padding:0 6px;font-size:12px;line-height:18px;">{{ reviewPending > 99 ? '99+' : reviewPending }}</span>
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
        </template>
    </UserLayout>
</template>
