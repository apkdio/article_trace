<script setup>
import {
    Crop,
    EditPen,
    Promotion,
    User,
    UserFilled
} from '@element-plus/icons-vue'
import {useRoute} from 'vue-router'
import UserLayout from '@/components/UserLayout.vue'

const route = useRoute()
</script>

<template>
    <UserLayout
        :default-active="route.path === '/reader/home' ? '/reader/info' : route.path"
        default-nickname="匿名读者"
    >
        <template #menu>
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
                <el-menu-item index="/reader/apply">
                    <el-icon>
                        <Promotion/>
                    </el-icon>
                    <span>申请成为作者</span>
                </el-menu-item>
            </el-sub-menu>
        </template>
    </UserLayout>
</template>
