<script setup>
import {
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
import UserLayout from '@/components/UserLayout.vue'

const route = useRoute()
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
