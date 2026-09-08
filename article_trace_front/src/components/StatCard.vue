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
    background: #f8fafc;
    border: 1px solid #edf2f7;
    border-radius: 12px;
    padding: 16px 20px;
    display: flex;
    align-items: center;
    gap: 15px;
    transition: all 0.3s;

    &:hover {
        background: #fff;
        border-color: #409eff;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
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
            background: #f5f3ff;
            color: #7c3aed;
        }

        &.blue {
            background: #eff6ff;
            color: #2563eb;
        }

        &.indigo {
            background: #e0e7ff;
            color: #4f46e5;
        }

        &.green {
            background: #dcfce7;
            color: #16a34a;
        }
    }

    .stat-content {
        display: flex;
        flex-direction: column;

        .stat-label {
            font-size: 12px;
            color: #64748b;
        }

        .stat-value {
            font-size: 20px;
            font-weight: 700;
            color: #1e293b;
        }
    }
}
</style>
