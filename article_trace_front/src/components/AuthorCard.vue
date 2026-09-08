<script setup>
import logo from '@/assets/defaultLogo.jpg'

defineProps({
    info: {type: Object, default: () => ({})},
    loading: {type: Boolean, default: false},
    defaultName: {type: String, default: '文迹作者'},
    contactPrefix: {type: String, default: '联系作者：'},
    headerBg: {type: String, default: 'linear-gradient(135deg, #4ca1af 0%, #3973ac 100%)'}
})
</script>

<template>
    <div class="sidebar-author-widget" v-loading="loading">
        <div class="author-card-header">
            <div class="header-bg" :style="{background: headerBg}"></div>
            <el-avatar :size="80" :src="info.writerPicSrc || logo" class="author-avatar-main"/>
        </div>
        <div class="author-card-body">
            <div class="writer-nick">{{ info.nickName || defaultName }}
                <p class="author-username">@{{ info.username }}</p>
            </div>
            <div class="writer-stats-grid">
                <div class="stat-item"><span class="val">{{ info.publishCount || 0 }}</span><span
                    class="lab">发布文章</span></div>
                <div class="stat-item">
                    <span class="val">
                        <slot name="role"></slot>
                    </span>
                    <span class="lab">身份</span>
                </div>
            </div>
            <div class="writer-contact"><p>{{ contactPrefix }}{{ info.email || '未公开' }}</p></div>
        </div>
    </div>
</template>

<style lang="scss" scoped>
.sidebar-author-widget {
    background: #ffffff;
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);

    .author-card-header {
        position: relative;

        .header-bg {
            height: 75px;
        }

        .author-avatar-main {
            position: absolute;
            left: 50%;
            transform: translateX(-50%);
            bottom: -40px;
            border: 4px solid #fff;
        }
    }

    .author-card-body {
        padding: 50px 20px 10px;
        text-align: center;

        .writer-nick {
            font-size: 18px;
            font-weight: 700;

            .author-username {
                font-size: 13px;
                color: #94a3b8;
                margin-top: 5px;
            }
        }

        .writer-stats-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            background: #f8fafc;
            border-radius: 8px;
            padding: 12px 0;
            margin: 15px 0;

            .stat-item {
                display: flex;
                flex-direction: column;

                .val {
                    font-weight: 600;
                }

                .lab {
                    font-size: 11px;
                    color: #94a3b8;
                }
            }
        }

        .writer-contact {
            font-size: 12px;
            color: #94a3b8;
        }
    }
}
</style>
