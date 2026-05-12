<script setup>
import { onMounted, ref } from 'vue'
import { userInfoStore } from "@/stores/userInfo.js";
import { updateUserInfoService } from "@/api/user.js";
import { User, Postcard, Message } from '@element-plus/icons-vue'

const userInfoRef = ref()
const errorList = ref({})
const userInfo = ref({
  id: null,
  username: '',
  nickname: '',
  email: '',
})

const rules = {
  nickname: [
    { required: true, message: '请输入用户昵称', trigger: 'blur' },
    {
      pattern: /^\S{2,15}$/,
      message: '昵称必须是2-15位的非空字符串',
      trigger: 'blur'
    }
  ],
  email: [
    { required: true, message: '请输入用户邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

onMounted(() => {
  setDefaultValue()
})

const setDefaultValue = () => {
  // 建议只解构需要的属性，避免把 store 的方法也带入
  const store = userInfoStore();
  userInfo.value = {
    id: store.id,
    username: store.username,
    nickname: store.nickname,
    email: store.email
  }
}

const freshUserInfo = async () => {
  await userInfoStore().fetchUserInfo()
  setTimeout(() => {
    setDefaultValue()
  }, 100)
}

const updateUserInfo = () => {
  if (userInfoRef.value) {
    userInfoRef.value.validate().then(async () => {
      try {
        const result = await updateUserInfoService(userInfo.value)
        if (result.code === 0) {
          ElMessage.success("修改成功！")
          await freshUserInfo()
        }
        else if(result.code === 1 && ('nickname' in result.message)){
          errorList.value = result.message
        }
        else {
          ElMessage.error("修改失败！")
        }
      } catch (err) {
        ElMessage.error("服务器响应失败！")
      }
    })
  }
}
</script>

<template>
  <div class="user-info-container">
    <div class="info-header">
      <div class="title-section">
        <h2 class="main-title">基本资料</h2>
        <p class="sub-title">管理您的账户信息，便于其他用户更好地了解您</p>
      </div>
    </div>

    <div class="info-content">
      <el-row>
        <el-col :xs="24" :sm="20" :md="16" :lg="12">
          <el-form
              ref="userInfoRef"
              :model="userInfo"
              :rules="rules"
              label-width="100px"
              label-position="top"
              size="large"
              class="custom-form"
          >
            <el-form-item label="登录名称">
              <el-input v-model="userInfo.username" disabled :prefix-icon="User">
                <template #append>不可修改</template>
              </el-input>
            </el-form-item>

            <el-form-item label="用户昵称" prop="nickname" :error="errorList.nickname">
              <el-input
                  v-model="userInfo.nickname"
                  placeholder="设置一个响亮的昵称吧"
                  :prefix-icon="Postcard"
                  maxlength="15"
                  show-word-limit
              />
            </el-form-item>

            <el-form-item label="用户邮箱" prop="email">
              <el-input
                  v-model="userInfo.email"
                  placeholder="请输入邮箱地址"
                  :prefix-icon="Message"
              />
            </el-form-item>

            <el-form-item class="action-item">
              <el-button type="primary" class="submit-btn" @click="updateUserInfo">提交修改</el-button>
              <el-button @click="setDefaultValue">重置表单</el-button>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.user-info-container {
  padding: 10px;
  /* 应用 fadeIn 动画 */
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
  /* 表单区域背景微调，提升专注感 */
  background: #ffffff;
  padding: 20px 0;
}

.custom-form {
  /* 调整 Form Item 之间的间距 */
  :deep(.el-form-item) {
    margin-bottom: 25px;
  }

  /* 标签加粗，提升可读性 */
  :deep(.el-form-item__label) {
    font-weight: 600;
    color: #444;
    padding-bottom: 8px !important;
  }

  /* 输入框聚焦效果优化 */
  :deep(.el-input__wrapper) {
    transition: all 0.3s;
    box-shadow: 0 0 0 1px #dcdfe6 inset;

    &.is-focus {
      box-shadow: 0 0 0 1px var(--el-color-primary) inset !important;
    }
  }
}

.action-item {
  margin-top: 40px;

  .submit-btn {
    padding: 12px 30px;
    font-weight: 600;
    letter-spacing: 1px;
  }
}

/* 动画定义 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 适配移动端 */
@media (max-width: 768px) {
  .info-header {
    .main-title { font-size: 20px; }
  }
}
</style>