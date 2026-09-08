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
    background: #ffffff;
    border-radius: 12px;
    padding: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03);

    .card-header {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: bold;
        font-size: 16px;
        color: #333;
        padding-bottom: 15px;
        border-bottom: 2px solid #f4f6f9;
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
            background: #f0f2f5;
            color: #909399;
            text-align: center;
            line-height: 20px;
            border-radius: 4px;
            margin-right: 12px;
            font-size: 12px;

            &.top-three {
                background: #409eff;
                color: #fff;
            }
        }

        .top-title {
            text-decoration: none;
            flex: 1;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            color: #555;
            cursor: pointer;

            &:hover {
                color: #409eff;
            }
        }

        .top-views {
            color: #acacac;
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
