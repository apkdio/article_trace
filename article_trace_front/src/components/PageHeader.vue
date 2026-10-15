<script setup>
defineProps({
    title: {type: String, default: ''},
    subtitle: {type: String, default: ''}
})
</script>

<template>
    <div class="page-header">
        <div class="title-group">
            <h2 class="main-title">{{ title }}</h2>
            <p class="sub-tip">{{ subtitle }}</p>
        </div>
        <div v-if="$slots.actions" class="header-actions">
            <slot name="actions"></slot>
        </div>
    </div>
</template>

<style lang="scss" scoped>
.page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--at-divider-warm);
    margin-bottom: 24px;

    .title-group {
        .main-title {
            font-size: 24px;
            font-weight: bold;
            color: var(--at-text-main);
            margin: 0;
        }

        .sub-tip {
            font-size: 13px;
            color: var(--at-text-secondary);
            margin: 4px 0 0;
        }
    }

    .header-actions {
        display: flex;
        gap: 12px;
    }
}
</style>
