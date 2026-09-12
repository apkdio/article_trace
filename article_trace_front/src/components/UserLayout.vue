<script setup>
import {ref, onMounted} from 'vue'
import {Bell, CaretBottom, HomeFilled, SwitchButton} from '@element-plus/icons-vue'
import avatar from '@/assets/defaultLogo.jpg'
import {checkTime} from '@/utils/timeCheck.js'
import {userInfoStore} from '@/stores/userInfo.js'
import router from '@/router/index.js'
import {logout as doLogout} from '@/utils/auth.js'
import UserTypeTag from '@/components/UserTypeTag.vue'
import {listNotifications, markAllRead, markRead as markReadApi, unreadCount} from "@/api/notification.js";

defineProps({
    defaultActive: {type: String, default: ''},
    defaultNickname: {type: String, default: '匿名用户'}
})

const timeCheck = ref()

// 站内通知
const notifyDrawer = ref(false)
const notifyList = ref([])
const notifyTotal = ref(0)
const notifyPage = ref(1)
const notifyPageSize = ref(10)
const unread = ref(0)
const notifyLoading = ref(false)

const loadUnread = async () => {
    try {
        const res = await unreadCount()
        if (res.code === 0) unread.value = res.data || 0
    } catch (e) {
        // 未读数获取失败不影响主流程
    }
}

const loadNotify = async () => {
    notifyLoading.value = true
    try {
        const res = await listNotifications(notifyPage.value, notifyPageSize.value)
        if (res.code === 0) {
            notifyList.value = res.data?.items || []
            notifyTotal.value = res.data?.total || 0
        }
    } catch (e) {
        ElMessage.error('加载通知失败！')
    } finally {
        notifyLoading.value = false
    }
}

const openNotify = () => {
    notifyDrawer.value = true
    notifyPage.value = 1
    loadNotify()
}

const readOne = async (item) => {
    if (item.isRead === 1) return
    try {
        const res = await markReadApi(item.id)
        if (res.code === 0) {
            item.isRead = 1
            unread.value = Math.max(0, unread.value - 1)
        }
    } catch (e) {
        // 忽略
    }
}

const readAll = async () => {
    try {
        const res = await markAllRead()
        if (res.code === 0) {
            notifyList.value.forEach(i => i.isRead = 1)
            unread.value = 0
            ElMessage.success('已全部标记为已读')
        }
    } catch (e) {
        ElMessage.error('操作失败！')
    }
}

onMounted(() => {
    timeCheck.value = checkTime()
    loadUnread()
})

const logout = (command) => {
    switch (command) {
        case 'back':
            router.push({name: 'PublicHome'})
            break
        case 'logout':
            doLogout()
    }
}
</script>

<template>
    <el-container class="layout-container">
        <el-aside width="200px">
            <div class="el-aside__logo"></div>
            <el-menu
                :default-active="defaultActive"
                active-text-color="#409eff"
                background-color="transparent"
                text-color="#5c6b77"
                :router="true"
                unique-opened
                class="custom-menu"
            >
                <slot name="menu"></slot>
            </el-menu>
        </el-aside>

        <el-container>
            <el-header>
                <div class="header-welcome">
                    <span class="greet">{{ timeCheck }}</span>
                    <span class="user-name">{{ userInfoStore().nickname ? userInfoStore().nickname : defaultNickname }}</span>
                    <UserTypeTag :type="userInfoStore().type"/>
                </div>
                <div class="last-time-container">
                    <p class="label">上次登录时间</p>
                    <p class="value">{{ userInfoStore().lastLogin }}</p>
                </div>
                <el-badge :value="unread" :max="99" :hidden="unread === 0" class="notify-badge">
                    <el-icon class="notify-bell" :size="20" @click="openNotify">
                        <Bell/>
                    </el-icon>
                </el-badge>
                <el-dropdown placement="bottom-end" @command="logout">
                    <span class="el-dropdown__box">
                        <el-avatar :src="userInfoStore().userPicThumbSrc === '' ? avatar : userInfoStore().userPicThumbSrc"/>
                        <el-icon class="arrow">
                            <CaretBottom/>
                        </el-icon>
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

        <!-- 站内通知抽屉 -->
        <el-drawer v-model="notifyDrawer" title="站内通知" size="380px" class="notify-drawer">
            <div class="notify-toolbar">
                <el-button size="small" text type="primary" :disabled="unread === 0" @click="readAll">
                    全部已读
                </el-button>
            </div>
            <div v-loading="notifyLoading" class="notify-list">
                <div v-if="!notifyLoading && notifyList.length === 0" class="notify-empty">暂无通知</div>
                <div v-for="item in notifyList" :key="item.id"
                     :class="['notify-item', {unread: item.isRead === 0}]"
                     @click="readOne(item)">
                    <div class="notify-title">
                        <span v-if="item.isRead === 0" class="notify-dot"></span>{{ item.title }}
                    </div>
                    <div class="notify-content">{{ item.content }}</div>
                    <div class="notify-time">{{ item.createTime }}</div>
                </div>
            </div>
            <div v-if="notifyTotal > notifyPageSize" class="notify-pager">
                <el-pagination
                    v-model:current-page="notifyPage"
                    :page-size="notifyPageSize"
                    :total="notifyTotal"
                    layout="prev, pager, next"
                    small
                    @current-change="loadNotify"
                />
            </div>
        </el-drawer>
    </el-container>
</template>

<style lang="scss" scoped>
.layout-container {
    height: 100vh;
    background-color: #f4f6f9;
    display: flex;

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

            :deep(.el-menu-item), :deep(.el-sub-menu__title) {
                margin: 2px 10px;
                border-radius: 6px;
                height: 48px !important;
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

            :deep(.el-menu--inline) {
                background-color: rgba(0, 0, 0, 0.03) !important;
                border-radius: 6px;
                margin: 0 10px;
                padding: 0;
                overflow: hidden;

                .el-menu-item {
                    padding-left: 48px !important;
                    margin: 0;
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

    > .el-container {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
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
        flex-shrink: 0;

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


    }

    .el-main {
        padding: 10px;
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;

        .router-content {
            background: #ffffff;
            flex: 1;
            border-radius: 8px;
            padding: 24px;
            overflow: auto;
            position: relative;
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

.notify-badge {
    margin-right: 16px;
    cursor: pointer;

    .notify-bell {
        color: #5c6b77;
        transition: color 0.2s;
        display: block;

        &:hover {
            color: #409eff;
        }
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
        .el-aside {
            width: 64px !important;
            transition: width 0.3s;

            &__logo {
                background-size: 60px auto !important;
                height: 50px;
                margin: 5px 0;
            }
        }

        :deep(.custom-menu) {
            .el-menu-item,
            .el-sub-menu__title {
                padding: 0 !important;
                margin: 4px 0 !important;
                display: flex !important;
                justify-content: center !important;
                align-items: center !important;
                width: 100% !important;

                span {
                    display: none !important;
                }

                .el-sub-menu__icon-arrow {
                    display: none !important;
                }

                .el-icon {
                    margin: 0 !important;
                    font-size: 20px;
                    color: #5c6b77;
                }

                &.is-active {
                    .el-icon {
                        color: #409eff;
                    }
                }
            }

            .el-menu--inline {
                margin: 0 !important;
                padding: 0 !important;
                background-color: rgba(0, 0, 0, 0.02) !important;

                .el-menu-item {
                    padding-left: 0 !important;
                    background-color: transparent !important;

                    &:hover {
                        background-color: rgba(64, 158, 255, 0.1) !important;
                    }
                }
            }
        }

        .el-header {
            padding: 0 12px;

            .header-welcome {
                .greet {
                    display: none;
                }

                .user-name {
                    font-size: 14px;
                    max-width: 80px;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }

                .el-tag {
                    display: none;
                }
            }

            .last-time-container {
                display: none;
            }

            .el-dropdown__box {
                padding: 0;

                .arrow {
                    display: none;
                }
            }
        }

        .el-main {
            padding: 8px;

            .router-content {
                padding: 16px;
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

<!-- 抽屉内容会 teleport 到 body，scoped 样式无法命中，故用独立 class 限定 -->
<style lang="scss">
.notify-drawer {
    .notify-toolbar {
        display: flex;
        justify-content: flex-end;
        margin-bottom: 8px;
    }

    .notify-list {
        min-height: 120px;
    }

    .notify-empty {
        padding: 40px 0;
        text-align: center;
        font-size: 13px;
        color: #909399;
    }

    .notify-item {
        padding: 10px 12px;
        border-radius: 8px;
        border-bottom: 1px solid #f2f3f5;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
            background: #f5f7fa;
        }

        &.unread .notify-title {
            font-weight: 600;
        }

        .notify-title {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 14px;
            color: #303133;
        }

        .notify-dot {
            flex-shrink: 0;
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #f56c6c;
        }

        .notify-content {
            margin-top: 4px;
            font-size: 13px;
            line-height: 1.5;
            color: #606266;
            word-break: break-word;
        }

        .notify-time {
            margin-top: 4px;
            font-size: 12px;
            color: #a8abb2;
        }
    }

    .notify-pager {
        display: flex;
        justify-content: center;
        margin-top: 12px;
    }
}
</style>
