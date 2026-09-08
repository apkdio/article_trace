<script setup>
defineProps({
    type: {type: Number, default: 2}
})
</script>

<template>
    <el-tag v-if="type === 0" size="small" type="primary" style="margin-left: 6px" disable-transitions>站长</el-tag>
    <el-tag v-else-if="type === 1" size="small" type="warning" style="margin-left: 6px" disable-transitions>作者</el-tag>
    <el-tag v-else size="small" type="info" style="margin-left: 6px" disable-transitions>读者</el-tag>
</template>
