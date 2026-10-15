<script setup>
defineProps({
    label: {type: String, default: ''},
    value: {type: [String, Number], default: ''},
    color: {type: String, default: 'blue'}
})
</script>

<template>
    <div class="stat-item">
        <div class="stat-icon" :class="color">
            <slot name="icon"></slot>
        </div>
        <div class="stat-content">
            <span class="stat-label">{{ label }}</span>
            <span class="stat-value">{{ value }}</span>
        </div>
    </div>
</template>

<style lang="scss" scoped>
.stat-item {
    background: var(--at-bg-stat);
    border: 1px solid var(--at-border-warm);
    border-radius: 12px;
    padding: 16px 20px;
    display: flex;
    align-items: center;
    gap: 15px;
    transition: all 0.3s;

    &:hover {
        background: var(--at-bg-card);
        border-color: var(--at-celadon);
        box-shadow: 0 4px 12px rgba(43, 122, 120, 0.06);
    }

    .stat-icon {
        width: 44px;
        height: 44px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 20px;

        &.purple {
            background: #f3ecf8;
            color: #6b46c1;
        }

        &.blue {
            background: #e6f0f7;
            color: #2b6cb0;
        }

        &.indigo {
            background: var(--at-celadon-tag-bg);
            color: var(--at-celadon);
        }

        &.green {
            background: #e7f4ee;
            color: #2e7d5b;
        }
    }

    .stat-content {
        display: flex;
        flex-direction: column;

        .stat-label {
            font-size: 12px;
            color: var(--at-text-secondary);
        }

        .stat-value {
            font-size: 20px;
            font-weight: 700;
            color: var(--at-text-main);
        }
    }
}
</style>
