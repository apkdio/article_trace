<script setup>
import {Avatar, Lock, User} from '@element-plus/icons-vue'
import {computed, onMounted, ref, watch} from 'vue'
import {forgetPassService, loginService, registerService} from "@/api/user.js";
import {showResetPassword} from "@/api/registerSuccess.js";
import router from "@/router/index.js";
import {tokenStorage} from "@/stores/tokenStorage.js";
import {userInfoStore} from "@/stores/userInfo.js";
import {loginCheck} from "@/utils/loginCheck.js";


let isRegister = ref(true)
let resetPass = ref(false)
const FormRef = ref()
const isLoading = ref(false)
const FormData = ref({
  username: '',
  password: '',
  confirmPassword: '',
  registerPassword: "",
  resetPassword: "",
  rememberMe: 0,
  type: 2
})
const errorsList = ref({})

// 动画标志位
const isAnimating = ref(false)
// 定义是否首次载入
const isFirstLoad = ref(true)

const confirmPasswordValid = (rule, value, callback) => {
  if (!value) return callback(new Error('请再次确认密码'))
  if (FormData.value.password !== value) return callback(new Error('两次输入密码不一致!'))
  callback()
}

const FormDataRules = computed(() => {
  if (resetPass.value) {
    return {
      username: [{required: true, message: '请输入用户名！'}],
      password: [{required: true, message: '请输入新密码！'}, {max: 60, message: "密码过长！"}],
      confirmPassword: [{validator: confirmPasswordValid}],
      resetPassword: [{required: true, message: '请输入重置码！'}]
    }
  }
  if (isRegister.value) {
    return {
      username: [{required: true, message: "请输入用户名！"}],
      password: [{required: true, message: "请输入密码！"}]
    }
  } else {
    return {
      username: [{required: true, message: '请输入用户名！'}, {max: 20, message: "用户名过长！"}],
      password: [{required: true, message: '请输入密码！'}, {max: 60, message: "密码过长！"}],
      confirmPassword: [{validator: confirmPasswordValid}]
    }
  }
})

onMounted(() => {
  loginCheck()
  // 首次加载完成后，将首次加载标志设为false
  setTimeout(() => {
    isFirstLoad.value = false
  }, 100)
})

watch([isRegister, resetPass], () => {
  if (!isFirstLoad.value) {
    // 触发左侧联动
    isAnimating.value = true
    setTimeout(() => isAnimating.value = false, 1200)
  }

  if (resetPass.value) document.title = "重置密码"
  else document.title = isRegister.value ? "登录" : "注册"
}, {immediate: true})


function register() {
  errorsList.value = {}
  FormRef.value.validate((valid) => {
    if (valid) {
      if (FormData.value.registerPassword !== "") FormData.value.type = 1
      registerService(FormData.value).then((result) => {
        if (result.code === 0) {
          showResetPassword(result.data?.success)
          isRegister.value = true
          FormRef.value.resetFields()
        } else {
          errorsList.value = result.message || {}
        }
      })
          .catch(() => ElMessage.warning("服务器响应失败！"))
    }
  })
}

function login() {
  errorsList.value = {}
  isLoading.value = true
  FormRef.value.validate((valid) => {
    if (valid) {
      loginService(FormData.value).then(async (result) => {
        if (result.code === 0) {
          ElMessage.success("登录成功！")
          tokenStorage().processed = false
          tokenStorage().setToken(result.data.token)
          await userInfoStore().fetchUserInfo()
          userInfoStore().setLastLogin(result.data.lastLogin)
          setTimeout(() => {
            FormRef.value.resetFields()
            router.push({name: "PublicHome"})
            isLoading.value = false
          }, 800)
        } else {
          isLoading.value = false
          errorsList.value = result.message || {}
          if (result.message.error) {
            ElMessage.error(result.message.error)
          }
        }
      })
          .catch(() => {
            ElMessage.error("服务器响应失败！")
            isLoading.value = false
          })
    }
    isLoading.value = false
  })
}

function resetPassword() {
  errorsList.value = {}
  FormRef.value.validate((valid) => {
    if (valid) {
      forgetPassService(FormData.value).then((result) => {
        if (result.code === 0) {
          ElMessage.success("修改成功！")
          resetPass.value = false
          FormRef.value.resetFields()
        } else {
          errorsList.value = result.message || {}
        }
      })
          .catch(() => ElMessage.error("服务器响应失败！"))
    }
  })
}

function clearInf() {
  errorsList.value = {}
  if (FormRef.value) FormRef.value.resetFields()
}


</script>

<template>
  <div class="login-container">
    <el-row class="login-page">
      <el-col :lg="12" :md="10" :sm="0" :xs="0" class="bg-section" :class="{ 'bg-lifted': isAnimating }">
        <div class="glass-overlay">
          <img src="../../assets/logo.png" class="logo-img" alt="logo"/>
        </div>
      </el-col>

      <el-col :lg="12" :md="14" :sm="24" :xs="24" class="form-section">
        <transition name="fade-slide" mode="out-in">
          <div class="form-card" :key="isRegister + String(resetPass)">

            <!--注册-->
            <el-form ref="FormRef" size="large" v-if="!isRegister && !resetPass" :model="FormData"
                     :rules="FormDataRules">
              <h1 class="gradient-title">创建账号</h1>
              <el-form-item prop="username" :error="errorsList.username">
                <el-input :prefix-icon="User" placeholder="设置用户名" maxlength="20" show-word-limit
                          v-model="FormData.username"/>
              </el-form-item>
              <el-form-item prop="password">
                <el-input :prefix-icon="Lock" type="password" placeholder="设置密码（60位以内）" show-password
                          v-model="FormData.password"/>
              </el-form-item>
              <el-form-item prop="confirmPassword" :error="errorsList.confirmPassword">
                <el-input :prefix-icon="Lock" type="password" placeholder="确认密码" show-password
                          v-model="FormData.confirmPassword"/>
              </el-form-item>
              <el-form-item prop="registerPassword" :error="errorsList.registerPassword">
                <el-input :prefix-icon="Avatar" placeholder="请输入邀请注册码（留空注册为读者）"
                          v-model="FormData.registerPassword"/>
              </el-form-item>
              <el-button class="submit-btn" type="primary" @click="register">注 册</el-button>
              <div class="footer-ops">
                <el-link :underline="'never'" @click="isRegister = true; clearInf()">← 已有账号？去登录</el-link>
              </div>
            </el-form>

            <!--登录-->
            <el-form ref="FormRef" size="large" v-else-if="isRegister && !resetPass" :model="FormData"
                     :rules="FormDataRules">

              <h1 class="gradient-title">欢迎回来</h1>
              <el-form-item prop="username" :error="errorsList.username">
                <el-input :prefix-icon="User" placeholder="用户名" v-model="FormData.username"/>
              </el-form-item>
              <el-form-item prop="password" :error="errorsList.password">
                <el-input :prefix-icon="Lock" type="password" placeholder="密码" show-password
                          v-model="FormData.password"/>
              </el-form-item>
              <el-checkbox v-model="FormData.rememberMe" :true-value="1" :false-value="0">
                当前登录时效：{{ FormData.rememberMe === 1 ? "(72小时)" : "(24小时)" }}
              </el-checkbox>

              <div class="flex-row-end">
                <el-link :underline="'never'" class="small-text" @click="resetPass = true; clearInf()">忘记密码？
                </el-link>
              </div>
              <el-button class="submit-btn login-gradient" :loading="isLoading" type="primary" @click="login">登 录
              </el-button>
              <div class="footer-ops">
                <el-link :underline="'never'" class="el-link__inner"
                         style="margin-right: 90px" @click="router.push('/')"> ← 返回首页
                </el-link>
                <span>新用户？</span>
                <el-link :underline="'never'" @click="isRegister = false; clearInf()">立即注册</el-link>
              </div>
            </el-form>

            <!--重置密码-->
            <el-form ref="FormRef" size="large" v-else :model="FormData" :rules="FormDataRules">
              <h1 class="gradient-title">重置密码</h1>
              <el-form-item prop="username" :error="errorsList.username">
                <el-input :prefix-icon="User" placeholder="用户名" v-model="FormData.username"/>
              </el-form-item>
              <el-form-item prop="resetPassword" :error="errorsList.resetPassword">
                <el-input :prefix-icon="Lock" type="password" placeholder="重置码" v-model="FormData.resetPassword"/>
              </el-form-item>
              <el-form-item prop="password" :error="errorsList.password">
                <el-input :prefix-icon="Lock" type="password" placeholder="新密码（60位以内）" show-password
                          v-model="FormData.password"/>
              </el-form-item>
              <el-form-item prop="confirmPassword" :error="errorsList.confirmPassword">
                <el-input :prefix-icon="Lock" type="password" placeholder="确认新密码" show-password
                          v-model="FormData.confirmPassword"/>
              </el-form-item>
              <el-button class="submit-btn" type="primary" @click="resetPassword">提交修改</el-button>
              <div class="footer-ops">
                <el-link :underline="'never'" @click="isRegister = true; resetPass = false; clearInf()">← 返回登录
                </el-link>
              </div>
            </el-form>
          </div>
        </transition>
      </el-col>
    </el-row>
  </div>
</template>

<style lang="scss" scoped>
.login-container {
  height: 100vh;
  background: radial-gradient(circle at top right, #fdfcfb 0%, #e2d1c3 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.login-page {
  width: 100%;
  height: 100%;

  .bg-section {
    background: url('@/assets/login_bg.jpg') center / cover no-repeat;
    position: relative;
    border-radius: 0 60px 60px 0;
    box-shadow: 20px 0 40px rgba(0, 0, 0, 0.1);
    z-index: 10;

    transform-origin: left center;

    &.bg-lifted {
      animation: 1200ms sideLiftHorizontal cubic-bezier(0.33, 1, 0.68, 1);
    }

    .glass-overlay {
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.1);
      border-radius: 0 60px 60px 0;
      display: flex;
      align-items: center;
      justify-content: center;

      .logo-img {
        width: 280px;
        filter: drop-shadow(0 10px 20px rgba(0, 0, 0, 0.2));
      }
    }
  }

  @keyframes sideLiftHorizontal {
    0% {
      transform: scaleX(1) skewY(0deg);
    }
    50% {
      transform: scaleX(0.98) skewY(-2deg);
    }
    100% {
      transform: scaleX(1) skewY(0deg);
    }
  }

  .form-section {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
    background-color: transparent;
  }
}


.form-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: rgba(204, 204, 204, 0.7);
  backdrop-filter: blur(15px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 24px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.1);

  .gradient-title {
    font-size: 2.2rem;
    font-weight: 800;
    text-align: center;
    margin-bottom: 35px;
    background: linear-gradient(135deg, #2c3e50 0%, #5f88af 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

:deep(.el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.5);
  border-radius: 14px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04) !important;
  border: 1px solid transparent;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 4px 15px;

  &.is-focus {
    background-color: rgba(204, 204, 204, 0.7);
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05) !important;
  }

  &:hover {
    transform: scale(1.03);
  }

  .el-form-item.is-error &,
  &.is-error {
    background-color: rgba(245, 108, 108, 0.1);
    border-color: #f56c6c !important;
    box-shadow: 0 0 0 1px rgba(245, 108, 108, 0.2), 0 2px 6px rgba(0, 0, 0, 0.04) !important;

    &:hover {
      transform: scale(1.05);
    }

    &.is-focus {
      background-color: rgba(245, 108, 108, 0.15);
      box-shadow: 0 0 0 1px rgba(245, 108, 108, 0.3), 0 10px 15px -3px rgba(0, 0, 0, 0.05) !important;
    }
  }
}

.submit-btn {
  width: 100%;
  height: 50px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  margin-top: 15px;
  border: none;
  background: linear-gradient(135deg, #4ca1af 0%, #5f88af 100%);
  box-shadow: 0 10px 20px rgba(76, 161, 175, 0.3);
  transition: all 0.3s ease;

  &:hover {
    box-shadow: 0 15px 25px rgba(76, 161, 175, 0.4);
    filter: brightness(1.1);
  }

  &:active {
    transform: scale(0.9);
  }
}

.footer-ops {
  margin-top: 25px;
  text-align: center;
  font-size: 14px;
  color: #7a7a7a;

  .el-link {
    font-weight: 600;
    vertical-align: baseline;
  }
}

.flex-row-end {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 15px;
}

.small-text {
  font-size: 13px;
  color: #787878
}

/* 卡片向图片收缩的联动逻辑 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-200px) scale(0.8);
}

:deep(.el-input .el-input__count .el-input__count-inner) {
  background: transparent;
}

@media (max-width: 768px) {
  .login-page {
    // 调整列比例：手机端让右侧表单占满，左侧背景隐藏（或更小）
    .bg-section {
      display: none;               // 完全隐藏左侧背景图，留出更多空间给表单
    }
    .form-section {
      width: 100%;
      flex: 0 0 100%;
      max-width: 100%;
      padding: 16px;
    }
  }

  .form-card {
    max-width: 100%;
    padding: 24px 20px;
    border-radius: 20px;
    backdrop-filter: blur(20px);
    background: rgba(204, 204, 204, 0.7);



    .gradient-title {
      font-size: 1.8rem;
      margin-bottom: 24px;
    }
  }

  // 输入框高度和字体调整
  :deep(.el-input__wrapper) {
    padding: 8px 12px;            // 增大点击区域
    font-size: 16px;              // 防止 iOS 缩放
    border-radius: 12px;
  }

  // 提交按钮
  .submit-btn {
    height: 48px;
    font-size: 15px;
    letter-spacing: 2px;
    margin-top: 20px;
  }

  // 底部链接区域
  .footer-ops {
    margin-top: 20px;
    font-size: 13px;

    .el-link {
      font-size: 13px;
    }
  }

  // 忘记密码链接
  .flex-row-end .small-text {
    font-size: 12px;
  }

  // 复选框文字
  .el-checkbox {
    font-size: 13px;
  }

  // 动画简化（减少位移幅度）
  .fade-slide-enter-from {
    transform: translateX(15px);
  }
  .fade-slide-leave-to {
    transform: translateX(-100px) scale(0.9);
  }
}

// 针对极窄设备（宽度≤480px）进一步调整
@media (max-width: 480px) {
  .form-card {
    padding: 20px 16px;
    .gradient-title {
      font-size: 1.6rem;
      margin-bottom: 20px;
    }
  }
  :deep(.el-input__wrapper) {
    padding: 6px 10px;
  }
  .submit-btn {
    height: 44px;
    font-size: 14px;
  }
}
</style>