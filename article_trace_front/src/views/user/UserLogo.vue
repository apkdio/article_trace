<script setup>
import {Upload, Camera, InfoFilled, WarningFilled, Delete} from '@element-plus/icons-vue'
import {onMounted, ref} from 'vue'
import logo from '@/assets/defaultLogo.jpg'
import {userInfoStore} from "@/stores/userInfo.js";
import {removeUserLogoService} from "@/api/user.js";
import {tokenStorage} from "@/stores/tokenStorage.js";

const uploadRef = ref()
const imgSrc = ref()
const imgKey = ref()

onMounted(() => {
  loadValue()
})
const loadValue = () => {
  imgSrc.value = userInfoStore().userPicSrc
  imgKey.value = userInfoStore().userPic
}

const removeUserLogo = () => {
  if (imgKey.value === "" && imgSrc.value === "") {
    ElMessage.warning("已经是默认头像了！")
  } else {
    ElMessageBox.confirm("确定要设置为默认头像吗？", "提示", {
      type: "warning",
      confirmButtonText: "确定",
      cancelButtonText: "取消",
    }).then(() => {
      removeUserLogoService().then(async (result) => {
        if (result.code === 0) {
          ElMessage.success("重置成功！")
          uploadRef.value.clearFiles()
          await userInfoStore().fetchUserInfo()
          setTimeout(() => {
            loadValue()
          }, 100)
        } else {
          ElMessage.error("重置失败！")
        }
      }).catch((err) => {
        ElMessage.error("服务器响应失败！")
      })
    }).catch(() => {
      ElMessage.info("已取消重置！")
    })
  }
}
const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'];

function uploadCheck(file) {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error("上传图片不能大于5MB!")
    return false
  }
  if (file.type.startsWith('image/')) {
    if (allowedTypes.includes(file.type)) return true
    else {
      ElMessage.error("只支持 JPG、PNG、GIF、BMP、WEBP 格式的图片！")
      return false
    }
  } else {
    ElMessage.error("请上传图片类型文件！")
    return false
  }
}

function changeSetSrc(file, fileList) {
  if (fileList.length > 0) {
    const currentFile = fileList[fileList.length - 1]
    if (currentFile.raw && allowedTypes.includes(currentFile.raw.type)) {
      if (currentFile.raw.size > 5 * 1024 * 1024) {
        ElMessage.error("上传图片不能大于5MB!")
        uploadRef.value.clearFiles()
        return
      }
      imgSrc.value = URL.createObjectURL(currentFile.raw)
    } else {
      if (currentFile.raw) {
        ElMessage.error("只支持 JPG、PNG、GIF、BMP、WEBP 格式的图片！")
      }
      uploadRef.value.clearFiles()
    }
  }
}

const uploadSuccess = async (result) => {
  if (result.code === 0) {
    ElMessage.success("上传成功！")
    await userInfoStore().fetchUserInfo()
    await loadValue()
  } else {
    ElMessage.error("上传失败！")
    if (uploadRef.value) uploadRef.value.clearFiles()
  }
}
const uploadSubmit = () => {
  uploadRef.value?.submit()
}
</script>

<template>
  <div class="user-avatar-container">
    <div class="page-header">
      <div class="title-group">
        <h2 class="main-title">更换头像</h2>
        <p class="sub-tip">上传个性图片，展示独特自我</p>
      </div>
    </div>

    <div class="avatar-content">
      <el-row :gutter="40">
        <el-col :xs="24" :sm="24" :md="12" :lg="10">
          <div class="section-card">
            <h3 class="section-title">上传新头像</h3>
            <div class="upload-section">
              <el-upload
                  ref="uploadRef"
                  class="avatar-uploader"
                  action="/api/user/updateUserLogo"
                  :headers="{'Authorization':tokenStorage().token}"
                  :show-file-list="false"
                  :auto-upload="false"
                  :before-upload="uploadCheck"
                  :on-success="uploadSuccess"
                  :on-change="changeSetSrc"
                  method="PATCH"
                  name="userLogo"
              >
                <div class="preview-box">
                  <img v-if="imgSrc" :src="imgSrc" class="avatar"/>
                  <img v-else :src="logo" class="avatar default-logo"/>
                  <div class="upload-overlay">
                    <el-icon><Camera/></el-icon>
                    <span>更改图片</span>
                  </div>
                </div>
              </el-upload>

              <div class="info-text">
                <p><el-icon><InfoFilled/></el-icon> 支持 JPG/PNG/WEBP 等主流格式</p>
                <p><el-icon><WarningFilled/></el-icon> 图片大小不超过 5MB</p>
              </div>

              <div class="button-group">
                <el-button type="primary" :icon="Upload" size="large" round @click="uploadSubmit" class="main-btn">
                  确认上传
                </el-button>
                <el-button type="danger" :icon="Delete" plain size="large" round @click="removeUserLogo">
                  重置
                </el-button>
              </div>
            </div>
          </div>
        </el-col>

        <el-col :xs="24" :sm="24" :md="12" :lg="14">
          <div class="preview-showcase">
            <h3 class="section-title">应用预览</h3>

            <div class="showcase-grid">
              <div class="mock-card" v-if="userInfoStore().type === 0 || userInfoStore().type === 1">
                <div class="card-bg"></div>
                <div class="card-body">
                  <el-avatar :size="80" :src="imgSrc || logo" />
                  <div class="user-meta">
                    <span class="nick">{{ userInfoStore().nickname || '未命名用户' }}</span>
                    <span class="role">文迹作者</span>
                  </div>
                </div>
                <p class="card-hint">作者名片展示效果</p>
              </div>

              <div class="size-variants">
                <div class="variant-item">
                  <el-avatar :size="64" :src="imgSrc || logo" />
                  <span>64px 大</span>
                </div>
                <div class="variant-item">
                  <el-avatar :size="40" :src="imgSrc || logo" />
                  <span>40px 中</span>
                </div>
                <div class="variant-item">
                  <el-avatar :size="28" :src="imgSrc || logo" />
                  <span>28px 小</span>
                </div>
              </div>
            </div>

            <div class="tips-box">
              <h4 class="tips-title">选图建议</h4>
              <ul>
                <li>建议使用正方形图片，以免剪裁后主体偏移。</li>
                <li v-if="userInfoStore().type === 0 || userInfoStore().type === 1">光线充足、背景简单的照片会让你的个人主页更具高级感。</li>
                <li v-if="userInfoStore().type === 0 || userInfoStore().type === 1">定期更新头像能保持你的创作动态活跃度。</li>
              </ul>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style lang="scss" scoped>
/* 保持原有 animation 和 page-header 不变 */
.user-avatar-container {
  display: flex;
  flex-direction: column;
  animation: fadeIn 0.5s ease-out;
  overflow: hidden;
  height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding-bottom: 12px;
  border-bottom: 1px solid #f2f2f2;
  margin-bottom: 30px;
  .title-group {
    .main-title { font-size: 24px; font-weight: bold; color: #1a1a1a; margin: 0; }
    .sub-tip { font-size: 13px; color: #999; margin: 4px 0 0; }
  }
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 25px;
  position: relative;
  padding-left: 12px;
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 4px;
    height: 16px;
    background: #409eff;
    border-radius: 2px;
  }
}

.upload-section {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 20px;
  padding-left: 20px;
}

/* 头像预览框 */
.avatar-uploader {
  .preview-box {
    width: 220px;
    height: 220px;
    border-radius: 50%;
    border: 2px dashed #dcdfe6;
    overflow: hidden;
    position: relative;
    background: #f8fafc;
    transition: all 0.3s;
    &:hover {
      border-color: #409eff;
      .upload-overlay { opacity: 1; }
    }
  }
  .avatar { width: 100%; height: 100%; object-fit: cover; }
  .upload-overlay {
    position: absolute; top: 0; left: 0; width: 100%; height: 100%;
    background: rgba(0, 0, 0, 0.4); color: #fff;
    display: flex; flex-direction: column; justify-content: center; align-items: center;
    opacity: 0; transition: 0.3s; cursor: pointer;
    .el-icon { font-size: 24px; margin-bottom: 4px; }
  }
}

/* 右侧预览区展示 */
.showcase-grid {
  display: flex;
  gap: 30px;
  flex-wrap: wrap;
  margin-bottom: 30px;
}

.mock-card {
  width: 240px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
  .card-bg { height: 60px; background: linear-gradient(135deg, #4ca1af 0%, #5f88af 100%); }
  .card-body {
    padding: 0 15px 15px;
    text-align: center;
    margin-top: -40px;
    .user-meta {
      margin-top: 10px;
      display: flex; flex-direction: column;
      .nick { font-weight: bold; color: #1e293b; }
      .role { font-size: 12px; color: #64748b; margin-top: 4px; }
    }
  }
  .card-hint { font-size: 11px; color: #94a3b8; text-align: center; margin-bottom: 10px; }
}

.size-variants {
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  gap: 15px;
  .variant-item {
    display: flex; align-items: center; gap: 12px;
    span { font-size: 13px; color: #64748b; }
  }
}

.tips-box {
  background: #f0f7ff;
  border-radius: 12px;
  padding: 20px;
  max-width: 500px;
  .tips-title { margin: 0 0 10px; color: #0369a1; font-size: 14px; }
  ul {
    margin: 0; padding-left: 18px;
    li { font-size: 13px; color: #334155; margin-bottom: 6px; line-height: 1.6; }
  }
}

.button-group {
  margin-top: 10px; display: flex; gap: 15px;
  .main-btn { padding-left: 40px; padding-right: 40px; width: 110px}
}

.info-text p {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; color: #909399; margin: 4px 0;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

</style>