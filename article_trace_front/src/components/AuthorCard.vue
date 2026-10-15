<script setup>
import logo from '@/assets/defaultLogo.jpg'

defineProps({
    info: {type: Object, default: () => ({})},
    loading: {type: Boolean, default: false},
    defaultName: {type: String, default: '文迹作者'},
    contactPrefix: {type: String, default: '联系作者：'},
    headerBg: {type: String, default: 'linear-gradient(135deg, #35736e 0%, #2c4c5e 100%)'}
})
</script>

<template>
    <div class="sidebar-author-widget" v-loading="loading">
        <div class="author-card-header">
            <div class="header-bg" :style="{background: headerBg}"></div>
            <el-avatar :size="80" :src="info.writerPicThumbSrc || logo" class="author-avatar-main"/>
        </div>
        <div class="author-card-body">
            <div class="writer-nick">{{ info.nickName || defaultName }}
                <p class="author-username">@{{ info.username }}</p>
            </div>
            <!-- 个签：只有作者与站长有，没填就不占位 -->
            <p class="writer-signature" v-if="info.signature">{{ info.signature }}</p>
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
            <div class="writer-actions" v-if="$slots.actions">
                <slot name="actions"></slot>
            </div>
        </div>
    </div>
</template>

<style lang="scss" scoped>
.sidebar-author-widget {
    background: var(--at-bg-card);
    border-radius: 12px;
    overflow: hidden;
    border: 1px solid var(--at-border-warm);
    box-shadow: 0 2px 12px rgba(44, 42, 38, 0.03);

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
            border: 4px solid var(--at-bg-card);
        }
    }

    .author-card-body {
        padding: 50px 20px 12px;
        text-align: center;
        background: var(--at-bg-card);

        .writer-nick {
            font-size: 18px;
            font-weight: 700;
            color: var(--at-text-main);

            .author-username {
                font-size: 13px;
                color: var(--at-text-secondary);
                margin-top: 5px;
                font-weight: 400;
            }
        }

        .writer-signature {
            display: inline-block;
            margin: 10px 0 2px;
            padding: 4px 14px;
            border-radius: 6px;
            background: var(--at-bg-card);
            border: 1px solid var(--at-divider-warm);
            font-size: 13px;
            color: var(--at-text-regular);
            font-style: italic;
            line-height: 1.5;
            word-break: break-all;
        }

        .writer-stats-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            background: #f5f3ee;
            border-radius: 8px;
            padding: 12px 0;
            margin: 14px 0;

            .stat-item {
                display: flex;
                flex-direction: column;
                align-items: center;
                gap: 3px;

                .val {
                    font-weight: 600;
                    color: var(--at-text-main);
                }

                .lab {
                    font-size: 11px;
                    color: var(--at-text-secondary);
                }
            }
        }

        .writer-contact {
            font-size: 12px;
            color: var(--at-text-secondary);
        }

        .writer-actions {
            margin-top: 10px;
            padding-top: 10px;
            border-top: 1px solid var(--at-divider-warm);
        }
    }
}
</style>
