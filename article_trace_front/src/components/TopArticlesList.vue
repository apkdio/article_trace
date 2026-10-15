<script setup>
import {View} from '@element-plus/icons-vue'

defineProps({
    articles: {type: Array, default: () => []},
    loading: {type: Boolean, default: false}
})
</script>

<template>
    <div class="sidebar-card">
        <div class="card-header">
            <span>热门文章</span>
        </div>
        <div class="top-list" v-loading="loading" element-loading-text="正在加载热门文章列表...">
            <div v-if="articles && articles.length > 0" v-for="(post, index) in articles" :key="post.id"
                 class="top-item">
                <span :class="['rank-num', index < 3 ? 'top-three' : '']">{{ index + 1 }}</span>
                <router-link class="top-title" target="_blank"
                             :to="{name: 'ArticleInfo', params: {id: post.id}}" :title="post.title">
                    {{ post.title }}
                </router-link>
                <span class="top-views"><el-icon><View/></el-icon>{{ post.views }}</span>
            </div>
            <el-empty v-else description="暂无热门文章"></el-empty>
        </div>
    </div>
</template>

<style lang="scss" scoped>
.sidebar-card {
    background: var(--at-bg-card);
    border-radius: 12px;
    padding: 20px;
    border: 1px solid var(--at-border-warm);
    box-shadow: 0 2px 12px rgba(44, 42, 38, 0.03);

    .card-header {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: bold;
        font-size: 16px;
        color: var(--at-text-main);
        padding-bottom: 15px;
        border-bottom: 2px solid var(--at-bg-stat);
        margin-bottom: 15px;

        .el-icon {
            color: #e6a23c;
        }
    }

    .top-item {
        display: flex;
        align-items: center;
        padding: 10px 0;
        font-size: 14px;

        .rank-num {
            width: 20px;
            height: 20px;
            background: var(--at-bg-tint);
            color: var(--at-text-secondary);
            text-align: center;
            line-height: 20px;
            border-radius: 4px;
            margin-right: 12px;
            font-size: 12px;
            flex-shrink: 0;

            &.top-three {
                background: var(--at-celadon);
                color: #fff;
            }
        }

        .top-title {
            text-decoration: none;
            flex: 1;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            color: var(--at-text-regular);
            cursor: pointer;
            transition: color 0.18s;

            &:hover {
                color: var(--at-celadon);
            }
        }

        .top-views {
            color: var(--at-text-secondary);
            font-size: 12px;
            display: inline-flex;
            align-items: center;
            gap: 4px;
            margin-left: 8px;
            line-height: 10px;
        }
    }
}
</style>
