<script setup>
import {Key, Lock, Message, Picture, User} from '@element-plus/icons-vue'
import {computed, onMounted, ref, watch} from 'vue'
import {
  forgetPassService,
  getCaptchaService,
  loginService,
  registerService,
  sendEmailCodeService
} from "@/api/user.js";
import router from "@/router/index.js";
import {userInfoStore} from "@/stores/userInfo.js";
import {loginCheck} from "@/utils/loginCheck.js";
import {resetAuthHandled} from "@/utils/session.js";
import {confirmPasswordValid} from "@/utils/validators.js";
import {getSiteFeatures} from "@/api/site.js";


let isRegister = ref(true)
let resetPass = ref(false)
// 注册入口由站点开关决定：线上做个人备案时会关掉，入口要一起隐藏，
// 而不是等用户点了「注册」才吃一句报错
const registerEnabled = ref(true)

onMounted(async () => {
  const features = await getSiteFeatures()
  registerEnabled.value = features.registerEnabled
  // 关闭注册时强制回到登录表单（沿原代码命名：isRegister=true 即登录态）
  if (!features.registerEnabled) isRegister.value = true
})
const FormRef = ref()
const isLoading = ref(false)
const FormData = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  emailCode: '',
  captcha: '',
  rememberMe: 0
})

// 邮箱验证码发送状态
const codeSending = ref(false)
const codeCountdown = ref(0)
let countdownTimer = null
const errorsList = ref({})

// 图形验证码（人机校验，发送邮箱验证码前一道门槛）
const captchaId = ref('')
const captchaImage = ref('')

async function loadCaptcha() {
  try {
    const result = await getCaptchaService()
    if (result.code === 0) {
      captchaId.value = result.data.captchaId
      captchaImage.value = result.data.image
    }
  } catch {
    // 静默失败：用户可点击图片重试
  }
}

function refreshCaptcha() {
  FormData.value.captcha = ''
  captchaInput.value = ''
  loadCaptcha()
}

// 「发码前的人机校验」弹窗：注册 / 找回密码共用。
// 图形码只服务于取码这一步，放进弹窗后与表单校验彻底解耦——
// 用户填不填、取没取消，都不会影响后面的表单提交。
const captchaDialogVisible = ref(false)
const captchaInput = ref('')
const captchaSubmitting = ref(false)
let captchaScene = ''

function openCaptchaDialog(scene) {
  captchaScene = scene
  captchaDialogVisible.value = true
  refreshCaptcha()
}

function startCodeCountdown() {
  codeCountdown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

// 登录防爆破：失败达阈值后要求图形验证码；连续失败过多会被短暂锁定
const loginNeedCaptcha = ref(false)
const blockedSeconds = ref(0)
let blockTimer = null

function startBlockCountdown(seconds) {
  blockedSeconds.value = seconds
  if (blockTimer) clearInterval(blockTimer)
  blockTimer = setInterval(() => {
    blockedSeconds.value -= 1
    if (blockedSeconds.value <= 0) {
      clearInterval(blockTimer)
      blockTimer = null
      blockedSeconds.value = 0
    }
  }, 1000)
}

// 动画标志位
const isAnimating = ref(false)
// 定义是否首次载入
const isFirstLoad = ref(true)

const confirmPasswordValidator = confirmPasswordValid(() => FormData.value.password)

const FormDataRules = computed(() => {
  if (resetPass.value) {
    return {
      email: [
        {required: true, message: '请输入邮箱！', trigger: 'blur'},
        {type: 'email', message: '邮箱格式不正确！', trigger: 'blur'}
      ],
      emailCode: [{required: true, message: '请输入验证码！', trigger: 'blur'}],
      password: [{required: true, message: '请输入新密码！'}, {max: 60, message: "密码过长！"}],
      confirmPassword: [{validator: confirmPasswordValidator}]
    }
  }
  if (isRegister.value) {
    return {
      username: [{required: true, message: "请输入用户名！"}],
      password: [{required: true, message: "请输入密码！"}],
      ...(loginNeedCaptcha.value
          ? {captcha: [{required: true, message: '请输入图形验证码！', trigger: 'blur'}]}
          : {})
    }
  } else {
    return {
      username: [{required: true, message: '请输入用户名！'}, {max: 20, message: "用户名过长！"}],
      password: [{required: true, message: '请输入密码！'}, {max: 60, message: "密码过长！"}],
      confirmPassword: [{validator: confirmPasswordValidator}],
      email: [
        {required: true, message: '请输入邮箱！', trigger: 'blur'},
        {type: 'email', message: '邮箱格式不正确！', trigger: 'blur'}
      ],
      emailCode: [{required: true, message: '请输入验证码！', trigger: 'blur'}]
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

// 登录被要求出示图形码时，立即加载一张
watch(loginNeedCaptcha, (need) => {
  if (need) refreshCaptcha()
})


function sendCode(scene) {
  const email = FormData.value.email
  if (!email) {
    ElMessage.warning('请先填写邮箱！')
    return
  }
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    ElMessage.warning('邮箱格式不正确！')
    return
  }
  if (codeCountdown.value > 0 || codeSending.value) return
  openCaptchaDialog(scene)
}

// 弹窗里确认：带图形码请求发送邮箱验证码
function confirmSendCode() {
  if (!captchaInput.value) {
    ElMessage.warning('请输入图形验证码！')
    return
  }
  if (captchaSubmitting.value) return
  captchaSubmitting.value = true
  sendEmailCodeService({
    email: FormData.value.email,
    scene: captchaScene,
    captchaId: captchaId.value,
    captchaCode: captchaInput.value
  }).then((result) => {
    if (result.code === 0) {
      ElMessage.success('验证码已发送，请查收邮箱')
      captchaDialogVisible.value = false
      startCodeCountdown()
    } else {
      ElMessage.error(result.message || '发送失败！')
      // 图形码一次性，失败也要换一张
      refreshCaptcha()
    }
  })
      .catch(() => {
        ElMessage.warning('服务器响应失败！')
        refreshCaptcha()
      })
      .finally(() => {
        captchaSubmitting.value = false
      })
}

function register() {
  errorsList.value = {}
  FormRef.value.validate((valid) => {
    if (valid) {
      registerService(FormData.value).then((result) => {
        if (result.code === 0) {
          ElMessage.success('注册成功，请登录')
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
      const payload = {...FormData.value}
      // 只有被要求时后端才校验图形码
      if (loginNeedCaptcha.value) {
        payload.captchaId = captchaId.value
        payload.captchaCode = FormData.value.captcha
      }
      loginService(payload).then(async (result) => {
        if (result.code === 0) {
          loginNeedCaptcha.value = false
          ElMessage.success("登录成功！")
          resetAuthHandled()
          await userInfoStore().fetchUserInfo()
          userInfoStore().setLastLogin(result.data.lastLogin)
          setTimeout(() => {
            FormRef.value.resetFields()
            router.push({name: "PublicHome"})
            isLoading.value = false
          }, 800)
        } else {
          isLoading.value = false
          const msg = result.message || {}
          errorsList.value = msg

          if (msg.blocked) {
            startBlockCountdown(msg.blocked * 60)
            ElMessage.error(`登录尝试过于频繁，请 ${msg.blocked} 分钟后再试！`)
          } else if (msg.needCaptcha) {
            // 再来一次就需要图形码了；本次若已出示过，图形码也已被消费，换一张
            loginNeedCaptcha.value = true
            refreshCaptcha()
            if (msg.remaining !== undefined && msg.remaining <= 2) {
              ElMessage.warning(`再失败 ${msg.remaining} 次将锁定 5 分钟`)
            }
          } else if (loginNeedCaptcha.value) {
            // 用户名/密码错了：本次图形码已通过并被消费，换一张
            refreshCaptcha()
          }
          if (msg.error) {
            ElMessage.error(msg.error)
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
              <el-form-item prop="email" :error="errorsList.email">
                <el-input :prefix-icon="Message" placeholder="请输入邮箱" v-model="FormData.email"/>
              </el-form-item>
              <el-form-item prop="emailCode" :error="errorsList.emailCode">
                <div class="code-row">
                  <el-input :prefix-icon="Key" placeholder="邮箱验证码" v-model="FormData.emailCode"/>
                  <el-button :disabled="codeCountdown > 0 || codeSending" class="email-code-button" @click="sendCode('register')">
                    {{ codeCountdown > 0 ? codeCountdown + 's' : '获取验证码' }}
                  </el-button>
                </div>
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
              <el-form-item v-if="loginNeedCaptcha" prop="captcha" :error="errorsList.captcha">
                <div class="code-row">
                  <el-input :prefix-icon="Picture" placeholder="图形验证码" v-model="FormData.captcha"/>
                  <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" class="captcha-img"
                       @click="refreshCaptcha"/>
                </div>
              </el-form-item>
              <el-alert v-if="blockedSeconds > 0" type="error" :closable="false" show-icon
                        :title="`登录已被锁定，请 ${blockedSeconds} 秒后再试`" class="block-alert"/>
              <el-checkbox v-model="FormData.rememberMe" :true-value="1" :false-value="0">
                当前登录时效：{{ FormData.rememberMe === 1 ? "(72小时)" : "(24小时)" }}
              </el-checkbox>

              <div class="flex-row-end">
                <el-link :underline="'never'" class="small-text" @click="resetPass = true; clearInf()">忘记密码？
                </el-link>
              </div>
              <el-button class="submit-btn login-gradient" :loading="isLoading" type="primary"
                         :disabled="blockedSeconds > 0" @click="login">登 录
              </el-button>
              <div class="footer-ops">
                <el-link :underline="'never'" class="el-link__inner"
                         style="margin-right: 90px" @click="router.push('/')"> ← 返回首页
                </el-link>
                <template v-if="registerEnabled">
                  <span>新用户？</span>
                  <el-link :underline="'never'" @click="isRegister = false; clearInf()">立即注册</el-link>
                </template>
              </div>
            </el-form>

            <!--重置密码-->
            <el-form ref="FormRef" size="large" v-else :model="FormData" :rules="FormDataRules">
              <h1 class="gradient-title">重置密码</h1>
              <el-form-item prop="email" :error="errorsList.email">
                <el-input :prefix-icon="Message" placeholder="注册时使用的邮箱" v-model="FormData.email"/>
              </el-form-item>
              <el-form-item prop="emailCode" :error="errorsList.emailCode">
                <div class="code-row">
                  <el-input :prefix-icon="Key" placeholder="邮箱验证码" v-model="FormData.emailCode"/>
                  <el-button :disabled="codeCountdown > 0 || codeSending" class="email-code-button" @click="sendCode('reset')">
                    {{ codeCountdown > 0 ? codeCountdown + 's' : '获取验证码' }}
                  </el-button>
                </div>
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

    <!-- 发码前的人机校验（注册 / 找回密码共用） -->
    <el-dialog v-model="captchaDialogVisible" title="安全验证" width="360px" append-to-body
               :close-on-click-modal="false">
      <p class="captcha-dialog-tip">请输入图片中的字符，验证通过后会向你的邮箱发送验证码。</p>
      <div class="code-row">
        <el-input :prefix-icon="Picture" placeholder="图形验证码" v-model="captchaInput"
                  @keyup.enter="confirmSendCode"/>
        <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" class="captcha-img"
             @click="refreshCaptcha"/>
      </div>
      <template #footer>
        <el-button @click="captchaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="captchaSubmitting" @click="confirmSendCode">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;

  .captcha-img {
    height: 40px;
    width: 130px;
    border-radius: 8px;
    cursor: pointer;
    object-fit: cover;
    flex-shrink: 0;
  }

  .el-input {
    flex: 1;
  }
}

.login-container {
  /* 用 min-height 而非固定高度：表单变长时容器跟着长高，交给页面滚动而不是裁掉 */
  min-height: 100vh;
  background: radial-gradient(circle at top right, #fdfcfb 0%, #e2d1c3 100%);
  display: flex;
  overflow-y: auto;
}

.login-page {
  width: 100%;
  min-height: 100vh;
  /* 居中但不裁切：内容高于视口时顶部依然能滚动到 */
  margin: auto;

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

.block-alert {
  margin-bottom: 12px;
  border-radius: 12px;
}

.captcha-dialog-tip {
  margin: 0 0 14px;
  font-size: 13px;
  color: #787878;
  line-height: 1.6;
}
.email-code-button{
  border-radius: 10px;
  background: rgb(255 255 255 / 0.7);
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

/* 矮屏（1366x768 之类的笔记本）：压缩纵向占用，尽量一屏放下 */
@media (max-height: 820px) {
  .form-card {
    padding: 24px 28px;

    .gradient-title {
      font-size: 1.7rem;
      margin-bottom: 18px;
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  /* 错误文案绝对定位、不占布局，出错时要额外留高，否则会压到下一个输入框 */
  :deep(.el-form-item.is-error) {
    margin-bottom: 26px;
  }

  .footer-ops {
    margin-top: 16px;
  }
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