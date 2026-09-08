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
    border-bottom: 1px solid #f2f2f2;

    .main-title {
        margin: 0;
        font-size: 24px;
        font-weight: bold;
        color: #1a1a1a;
    }

    .sub-title {
        margin: 8px 0 0;
        font-size: 14px;
        color: #8c8c8c;
    }
}

.info-content {
    background: #ffffff;
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
