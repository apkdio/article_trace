<script setup>
import {useRouter} from 'vue-router'
document.title = "404 - 未找到文迹"
const router = useRouter()

const goHome = () => {
  router.push("/")
}
</script>
<template>
  <div class="not-found-container">
    <div class="content-box">
      <h1 class="error-code">404</h1>
      <div class="message-section">
        <h2 class="error-title">Page Not Found</h2>
        <p class="error-tip">
          您寻找的“文迹”也许已隐入墨色之中，或者从未存在。
        </p>
      </div>

      <div class="action-area">
        <el-button
            type="primary"
            size="large"
            round
            @click="goHome"
            class="home-btn"
        >
          返回首页
        </el-button>
      </div>
    </div>

    <div class="ink-blob"></div>
  </div>
</template>



<style lang="scss" scoped>
.not-found-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f8fafc;
  overflow: hidden;
  position: relative;
}

.content-box {
  text-align: center;
  z-index: 2;
  animation: slideUp 0.8s ease-out;
}

.error-code {
  font-size: 180px;
  font-weight: 900;
  margin: 0;
  line-height: 1;
  background: linear-gradient(135deg, #409eff 0%, #a0cfff 100%);
  background-clip: text;
  -webkit-text-fill-color: transparent;
  opacity: 0.15;
  letter-spacing: -5px;
}

.message-section {
  margin-top: -40px;

  .error-title {
    font-size: 28px;
    color: #2c3e50;
    margin-bottom: 12px;
    font-weight: 600;
  }

  .error-tip {
    font-size: 16px;
    color: #94a3b8;
    max-width: 400px;
    margin: 0 auto 40px;
    line-height: 1.6;
  }
}

.home-btn {
  padding: 12px 40px;
  font-size: 16px;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
  }
}

/* 装饰性墨迹动画 */
.ink-blob {
  position: absolute;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.05) 0%, rgba(248, 250, 252, 0) 70%);
  border-radius: 50%;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 1;
  animation: pulse 4s infinite alternate ease-in-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  from {
    transform: translate(-50%, -50%) scale(1);
  }
  to {
    transform: translate(-50%, -50%) scale(1.2);
  }
}

/* 手机端适配 */
@media (max-width: 768px) {
  .error-code {
    font-size: 120px;
  }
  .error-title {
    font-size: 22px;
  }
  .error-tip {
    font-size: 14px;
    padding: 0 20px;
  }
}
</style>