<script setup>
defineProps({
    title: {type: String, default: ''},
    subtitle: {type: String, default: ''}
})
</script>

<template>
    <div class="user-info-container">
        <div class="info-header">
            <div class="title-section">
                <h2 class="main-title">{{ title }}</h2>
                <p class="sub-title">{{ subtitle }}</p>
            </div>
        </div>

        <div class="info-content">
            <slot></slot>
        </div>
    </div>
</template>

<style lang="scss" scoped>
.user-info-container {
    padding: 10px;
    animation: fadeIn 0.5s ease-out forwards;
}

.info-header {
    margin-bottom: 30px;
    padding-bottom: 15px;
    border-bottom: 1px solid var(--at-divider-warm);

    .main-title {
        margin: 0;
        font-size: 24px;
        font-weight: bold;
        color: var(--at-text-main);
    }

    .sub-title {
        margin: 8px 0 0;
        font-size: 14px;
        color: var(--at-text-secondary);
    }
}

.info-content {
    background: transparent;
    padding: 20px 0;
}

@media (max-width: 768px) {
    .info-header {
        .main-title {
            font-size: 20px;
        }
    }
}
</style>
