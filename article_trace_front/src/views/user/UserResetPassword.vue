<script setup>
import {ref} from 'vue'
import {userInfoStore} from "@/stores/userInfo.js";
import { updateUserPassService} from "@/api/user.js";
import {Lock} from '@element-plus/icons-vue'
import router from "@/router/index.js";
import {tokenStorage} from "@/stores/tokenStorage.js";

const userPassRef = ref()
const errorList = ref({})
const userPass = ref({
  id: null,
  oriPass: '',
  newPass: '',
  confirmPass: '',
})

const confirmPasswordValid = (rule, value, callback) => {
  if (!value) return callback(new Error('请再次确认密码'))
  if (userPass.value.newPass !== value) return callback(new Error('两次输入密码不一致!'))
  callback()
}

const rules = {
  oriPass: [
    {required: true, message: '请输入原密码', trigger: 'blur'},
    {
      max: 60,
      message: '原密码长度在60位以内',
      trigger: 'change'
    }
  ],
  newPass: [
    {required: true, message: '请输入新密码', trigger: 'blur'}
  ],
  confirmPass: [
    {required: true, message: '请输入确认密码！'},
    {validator: confirmPasswordValid}
  ]
}

const updateUserPass = () => {
  if (userPassRef.value) {
    userPassRef.value.validate().then(
        async () => {
          try {
            const result = await updateUserPassService(userPass.value)
            if (result.code === 0) {
              await userInfoStore().clearUserInfo()
              await tokenStorage().clearToken()
              ElMessage.success("修改成功！请重新登录！");
              router.push('/login')
            } else if (result.code === 1 && ('Redis' in result.message)) {
              ElMessage.warning("密码已更改！缓存未删除，请重新登录一次更新缓存！")
            } else if (result.code === 1 && !("error" in result.message)) {
              errorList.value = result.message
            } else {
              ElMessage.error(result.message.error)
            }
          } catch (err) {
            ElMessage.error("服务器响应失败！")
          }
        }
    )
  }
}
</script>

<template>
  <div class="user-info-container">
    <div class="info-header">
      <div class="title-section">
        <h2 class="main-title">修改密码</h2>
        <p class="sub-title">定期修改账户密码，提高账户的安全性</p>
      </div>
    </div>

    <div class="info-content">
      <el-row>
        <el-col :xs="24" :sm="20" :md="16" :lg="12">
          <el-form
              ref="userPassRef"
              :model="userPass"
              :rules="rules"
              label-width="100px"
              label-position="top"
              size="large"
              class="custom-form"
          >
            <el-form-item label="原始密码" prop="oriPass" :error="errorList.oriPass">
              <el-input type="password" show-password v-model="userPass.oriPass" placeholder="输入您的原密码"
                        :prefix-icon="Lock" >
              </el-input>
            </el-form-item>

            <el-form-item label="新密码" prop="newPass" :error="errorList.newPass">
              <el-input
                  type="password"
                  show-password
                  v-model="userPass.newPass"
                  placeholder="输入新密码（最长60位）"
                  :prefix-icon="Lock"
                  show-word-limit
              />
            </el-form-item>

            <el-form-item label="确认密码" prop="confirmPass" :error="errorList.confirmPass">
              <el-input
                  v-model="userPass.confirmPass"
                  type="password"
                  show-password
                  placeholder="再次输入新密码"
                  :prefix-icon="Lock"
              />
            </el-form-item>

            <el-form-item class="action-item">
              <el-button type="primary" class="submit-btn" @click="updateUserPass()">提交修改</el-button>
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
    .main-title {
      font-size: 20px;
    }
  }
}
</style>