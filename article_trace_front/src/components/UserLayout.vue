<script setup>
import {ref, onMounted} from 'vue'
import {CaretBottom, HomeFilled, SwitchButton} from '@element-plus/icons-vue'
import avatar from '@/assets/defaultLogo.jpg'
import {checkTime} from '@/utils/timeCheck.js'
import {userInfoStore} from '@/stores/userInfo.js'
import router from '@/router/index.js'
import {logout as doLogout} from '@/utils/auth.js'
import UserTypeTag from '@/components/UserTypeTag.vue'
import NotificationBell from '@/components/NotificationBell.vue'

defineProps({
    defaultActive: {type: String, default: ''},
    defaultNickname: {type: String, default: '匿名用户'}
})

const timeCheck = ref()

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
                active-text-color="#2b7a78"
                background-color="transparent"
                text-color="#57534c"
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
                <!-- 铃铛与通知抽屉由组件统一提供，外观沿用默认的图标样式 -->
                <NotificationBell class="header-notify"/>
                <el-dropdown placement="bottom-end" @command="logout">
                    <span class="el-dropdown__box">
                        <el-avatar :src="userInfoStore().userPicThumbSrc || avatar"/>
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
            <el-footer>© {{ new Date().getFullYear() }} 文迹 · MIT License</el-footer>
        </el-container>

    </el-container>
</template>

<style lang="scss" scoped>
.layout-container {
    height: 100vh;
    background-color: var(--at-bg-page);
    display: flex;

    .el-aside {
        background-color: var(--at-bg-aside);
        border-right: 1px solid var(--at-border-warm);
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
                color: var(--at-text-regular) !important;

                span {
                    display: inline-block;
                    height: 48px;
                    line-height: 48px;
                    vertical-align: middle;
                }
            }

            :deep(.el-menu--inline) {
                background-color: rgba(44, 42, 38, 0.035) !important;
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
                    background-color: var(--at-bg-card) !important;
                    color: var(--at-celadon) !important;
                    font-weight: 600;
                    box-shadow: 0 2px 8px rgba(44, 42, 38, 0.05);
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
        background: var(--at-bg-card);
        border-bottom: 1px solid var(--at-border-warm);
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 20px;
        height: 60px;
        box-shadow: 0 1px 4px rgba(44, 42, 38, 0.03);
        z-index: 10;
        flex-shrink: 0;

        .header-welcome {
            .greet {
                color: var(--at-text-secondary);
                font-weight: bold;
                font-size: 16px;
                font-family: "Microsoft YaHei", serif;
            }

            .user-name {
                color: var(--at-text-main);
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
            background: var(--at-bg-card);
            border: 1px solid var(--at-border-warm);
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
        color: var(--at-text-secondary);
        background: transparent;
        height: 22px !important;
    }
}

// 铃铛样式已移入 NotificationBell，这里只负责在本布局里的间距
.header-notify {
    margin-right: 16px;

    :deep(.notify-badge) {
        cursor: pointer;

        .notify-bell {
            color: var(--at-text-regular);
            transition: color 0.2s;
            display: block;

            &:hover {
                color: var(--at-celadon);
            }
        }
    }
}

.last-time-container {
    margin-left: auto;
    margin-right: 10px;
    text-align: right;

    .label {
        font-size: 11px;
        color: var(--at-text-secondary);
        margin: 0;
    }

    .value {
        font-size: 12px;
        color: var(--at-text-regular);
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
                    color: var(--at-text-regular);
                }

                &.is-active {
                    .el-icon {
                        color: var(--at-celadon);
                    }
                }
            }

            .el-menu--inline {
                margin: 0 !important;
                padding: 0 !important;
                background-color: rgba(44, 42, 38, 0.025) !important;

                .el-menu-item {
                    padding-left: 0 !important;
                    background-color: transparent !important;

                    &:hover {
                        background-color: rgba(43, 122, 120, 0.1) !important;
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
