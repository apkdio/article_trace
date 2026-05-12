<script setup>

import {Delete, Edit, Plus, Picture, Search, User, Calendar, Timer, UserFilled} from '@element-plus/icons-vue'
import cover from '@/assets/defaultCover.jpg'
import {nextTick, onMounted, ref} from 'vue'
import {getAllCategories} from "@/api/category.js";
import {tokenStorage} from "@/stores/tokenStorage.js";
import {
  addArticleService, assessArticleService, deleteArticleService,
  getArticleWithConditions, getArticleWithConditionsMaster,
  removeCover,
  updateArticleService
} from "@/api/article.js";
import {QuillEditor} from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import {userInfoStore} from "@/stores/userInfo.js";
import {checkPersonInfo} from "@/api/checkPersonInfo.js";
import router from "@/router/index.js";
import {checkType} from "@/api/user.js";


const categories = ref([])
const articles = ref([])
const isLoading = ref(true)
const conditionRef = ref({})
const errorList = ref({})
const pageNum = ref(1)
const total = ref(0)
const pageSize = ref(5)
const quillEditorRef = ref()

const previewDrawer = ref(false)
const visibleDrawer = ref(false)
const drawerTitle = ref('')
const coverRef = ref(false)
const articleModelRef = ref()
const articleModel = ref({
  id: '',
  title: '',
  categoryId: '',
  coverImgSrc: '',
  coverImg: '',
  content: '',
  state: ''
})
const previewData = ref({})

const validateContent = (rule, value, callback) => {
  if (!value || value.trim() === '' || value === '<p><br></p>' || value === '<p></p>') {
    callback(new Error('文章内容不能为空'))
  } else {
    const text = value.replace(/<[^>]+>/g, '').trim()
    if (text === '') {
      callback(new Error('文章内容不能为空'))
    } else {
      callback()
    }
  }
}

const articleModelRule = {
  title: [
    {required: true, message: '请输入标题', trigger: 'blur'},
    {min: 1, max: 30, message: '长度在 1 到 30 个字符', trigger: 'blur'}
  ],
  content: [{validator: validateContent}],
  categoryId: [{required: true, message: '请选择分类！'}],
}

const conditions = ref({
  pageNum: null,
  pageSize: null,
  categoryId: null,
  state: null,
  search: null,
  searchType: 0
})

onMounted(() => {
  if (!checkType([0, 1])) router.push({name: "ErrorPage"})
  if (checkPersonInfo()) getCategories()
  else {
    ElMessageBox.confirm("请先完善个人信息！", "提示", {
      type: "warning",
      confirmButtonText: "确认",
      showCancelButton: false,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      showClose: false,
      center: true,
      customStyle: {textAlign: "center"}
    }).then(() => {
      router.push({name: "UserInfo"})
    })
  }
})

const getCategories = async () => {
  try {
    const data = await getAllCategories()
    categories.value = data.data
    await getArticles()
  } catch (error) {
    ElMessage.error("数据获取失败！")
  }
}

const onSizeChange = (size) => {
  pageSize.value = size
  getArticles()
}
const onCurrentChange = (num) => {
  pageNum.value = num
  getArticles()
}

const getArticles = async () => {
  isLoading.value = true
  conditions.value.pageNum = pageNum.value
  conditions.value.pageSize = pageSize.value
  try {
    const userType = userInfoStore().type
    let data;
    if (userType === 0) data = await getArticleWithConditionsMaster(conditions.value)
    else data = await getArticleWithConditions(conditions.value)
    if (data.code === 0) {
      articles.value = data.data.items
      total.value = data.data.total
    } else {
      articles.value = []
    }
  } catch (error) {
    ElMessage.error("数据获取失败！")
  } finally {
    setTimeout(() => {
      isLoading.value = false
    }, 100)
  }
}

const reset = () => {
  conditions.value = {pageNum: null, pageSize: null, categoryId: null, state: null, search: null, searchType: 0}
  pageNum.value = 1
  pageSize.value = 5
  if (conditionRef.value) conditionRef.value.resetFields()
  getArticles()
}

const uploadSuccess = (result) => {
  removeCover(articleModel.value.coverImg)
  if (result.code === 0) {
    ElMessage.success("上传成功！")
    articleModel.value.coverImgSrc = result.data.src
    articleModel.value.coverImg = result.data.key
  } else {
    ElMessage.error("上传失败！")
    if (coverRef.value) coverRef.value.clearFiles()
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

const cleanCover = () => {
  ElMessageBox.confirm("确认删除该封面? 此操作不可恢复！", "警告", {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    type: "warning",
    buttonSize: "default"
  }).then(async () => {
    const result = await removeCover(articleModel.value.coverImg)
    if (result.code === 0) {
      ElMessage.success("删除成功！")
      articleModel.value.coverImgSrc = ''
      articleModel.value.coverImg = ''
      coverRef.value.clearFiles()
    } else {
      ElMessage.error("删除失败！")
    }
  })
}

const clearModel = () => {
  errorList.value = {}
  reset()
  articleModel.value = {title: '', categoryId: '', coverImgSrc: '', coverImg: '', content: '', state: ''}
  visibleDrawer.value = false
  if (articleModelRef.value) articleModelRef.value.resetFields()
  if (coverRef.value) coverRef.value.clearFiles()
  if (quillEditorRef.value) {
    const quill = quillEditorRef.value.getQuill()
    if (quill) quill.setContents([])
  }
}

const handleEditorBlur = () => {
  nextTick(() => {
    if (articleModelRef.value) articleModelRef.value.validateField('content')
  })
}

const addOrUpdateArticle = async (state) => {
  errorList.value = {}
  if (state !== 0) {
    if (userInfoStore().type !== 0) articleModel.value.state = 2
    else articleModel.value.state = 1
  } else articleModel.value.state = state
  if (articleModelRef.value.validate()) {
    if (drawerTitle.value === '添加文章') {
      try {
        const resultData = await addArticleService(articleModel.value)
        if (resultData.code === 0) {
          ElMessage.success("添加成功！")
          clearModel()
          await getArticles()
        } else {
          if (resultData.message.content) {
            ElMessage.error(resultData.message.content)
          } else if (resultData.message.error) {
            ElMessage.error("修改失败！")
          }
          else{errorList.value = resultData.message || {}}
        }
      } catch (error) {
        ElMessage.error("添加失败！服务端响应失败！")
      }
    } else if (drawerTitle.value === '修改文章') {
      try {
        const resultData = await updateArticleService(articleModel.value)
        if (resultData.code === 0) {
          ElMessage.success("修改成功！")
          clearModel()
          await getArticles()
        } else {
          if (resultData.message.content) {
            ElMessage.error(resultData.message.content)
          } else if (resultData.message.error) {
            ElMessage.error("修改失败！")
          }
          else{errorList.value = resultData.message || {}}
        }
      } catch (error) {
        ElMessage.error("修改失败！服务端响应失败！")
      }
    }
  } else {
    ElMessage.error("未知操作！")
  }
}

const openAddDrawer = () => {
  clearModel()
  drawerTitle.value = '添加文章'
  visibleDrawer.value = true
}
const openEditDrawer = async (row) => {
  clearModel()
  drawerTitle.value = '修改文章'
  articleModel.value = JSON.parse(JSON.stringify(row))
  visibleDrawer.value = true
}

const beforeCloseDrawer = () => {
  ElMessageBox.confirm("这会清空所有待提交数据！", "确定关闭窗口吗？", {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    type: "warning",
    buttonSize: "default"
  }).then(async () => {
    try {
      if (drawerTitle.value === '添加文章') {
        const result = await removeCover(articleModel.value.coverImg)
        if (result.code !== 0) ElMessage.info("服务端图片数据未删除，但您可继续使用！")
      }
      clearModel()
      ElMessage.primary("数据已清空！")
    } catch (error) {
      ElMessage.error("服务端未响应！")
    }
  }).catch(() => {
    ElMessage.info("取消关闭！")
  })
}

const deleteArticle = (id, createUser) => {
  ElMessageBox.confirm("确认删除该文章吗？", "警告", {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    type: "warning",
    buttonSize: "default"
  }).then(async () => {
    try {
      if (createUser !== userInfoStore().nickname) {
        ElMessageBox.prompt("请输入站长密码！", "提示", {
          confirmButtonText: "确认",
          cancelButtonText: "取消",
          type: "warning",
          buttonSize: "default",
          inputType:"password"
        }).then(async ({value}) => {
          const result = await deleteArticleService(id, `${value}`)
          if (result.code === 0) {
            ElMessage.success("删除成功！")
            await reset()
          } else {
            ElMessage.error(result.message.error ? result.message.error : "删除失败！")
          }
        })
            .catch(() => {
              ElMessage.info("取消删除！")
            })
      }
      const result = await deleteArticleService(id, "")
      if (result.code === 0) {
        ElMessage.success("删除成功！")
      } else ElMessage.error("删除失败！")
      await getArticles()
    } catch (error) {
      ElMessage.error("服务端响应失败！")
    }
  }).catch(() => {
    ElMessage.info("取消删除！")
  })
}
const assessArticle = async (id, state) => {
  try {
    const result = await assessArticleService(id, state)
    if (result.code === 0) {
      ElMessage.success("已审核！")
      previewDrawer.value = false
      setTimeout(() => {
        getArticles(conditions.value)
      }, 100)
    } else {
      ElMessage.error("审核失败！")
    }
  } catch (err) {
    ElMessage.error("服务器响应失败！")
  }
}
</script>

<template>
  <div class="article-manage-container">
    <div class="page-header">
      <div class="title-group">
        <h2 class="main-title">文章管理</h2>
        <p class="sub-tip">在这里发布和管理您的所有创作内容</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" size="large" @click="openAddDrawer">添加文章</el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-form inline ref="conditionRef" :model="conditions">
        <el-form-item label="文章分类">
          <el-select style="width: 140px" placeholder="请选择" v-model="conditions.categoryId" clearable>
            <el-option :value="-1" label="未分类" style="color: #f56c6c"></el-option>
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="发布状态">
          <el-select style="width:120px" placeholder="请选择" v-model="conditions.state" clearable>
            <el-option label="草稿" value="0"></el-option>
            <el-option label="已发布" value="1"></el-option>
            <el-option label="待审核" value="2"></el-option>
            <el-option label="已驳回" value="3"></el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="搜索">
          <el-switch
              v-model="conditions.searchType"
              v-if="userInfoStore().type===0"
              size="default"
              active-value="1"
              inactive-value="0"
              active-text="作者"
              inactive-text="标题"
              style="padding-right: 10px"
          />
          <el-input style="width:220px" :prefix-icon="Search" placeholder="关键词"
                    v-model="conditions.search"
                    clearable/>
        </el-form-item>


        <el-form-item>
          <el-button type="primary" @click="getArticles()">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="table-wrapper" v-loading="isLoading" element-loading-text="正在为您加载文章列表...">
      <template v-if="!isLoading">
        <el-table :data="articles" style="width: 100%" class="modern-table">
          <el-table-column label="封面图" width="200">
            <template #default="scope">
              <div class="pic_box">
                <el-image
                    class="full-img"
                    :src="scope.row.coverImgSrc == null ? cover : scope.row.coverImgSrc"
                    fit="contain"
                >
                  <template #error>
                    <div class="image-slot">
                      <el-icon>
                        <Picture/>
                      </el-icon>
                    </div>
                  </template>
                </el-image>
              </div>
            </template>
          </el-table-column>

          <el-table-column :label="userInfoStore().type === 0?'文章标题（点击审核）':'文章标题（点击预览）'"
                           :width="userInfoStore().type === 0?200:360">
            <template #default="{row}">
              <span class="table-article-title" @click="previewDrawer=true;previewData = row">{{ row.title }}</span>
            </template>

          </el-table-column>

          <el-table-column label="分类" width="140">
            <template #default="{row}">
              <el-tag v-if="row.categoryName !== '未分类'" effect="plain" disable-transitions>{{
                  row.categoryName
                }}
              </el-tag>
              <el-tag v-else type="warning" effect="plain" disable-transitions>{{ row.categoryName }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column v-if="userInfoStore().type === 0" label="作者" width="220">
            <template #default="{row}">
              <div class="creator-tag" :class="{ 'is-me': row.createUserName === userInfoStore().nickname }">
                <el-icon v-if="row.createUserName === userInfoStore().nickname">
                  <UserFilled/>
                </el-icon>
                <span>{{ row.createUserName }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="发布时间" width="260">
            <template #default="scope">
              <div class="time-column">
                <div class="main-time">
                  <el-icon>
                    <Timer style="padding-bottom: 2px"/>
                  </el-icon>
                  <span>{{ scope.row.createTime }}</span>
                </div>
                <div class="sub-time">
                  最后更新: {{ scope.row.updateTime || '暂无' }}
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag v-if="scope.row.state === 0" type="primary" disable-transitions>草稿</el-tag>
              <el-tag v-else-if="scope.row.state === 1" type="success" disable-transitions>发布</el-tag>
              <el-tag v-else-if="scope.row.state === 2" type="warning" disable-transitions>待审核</el-tag>
              <el-tag v-else-if="scope.row.state === 3" type="danger" disable-transitions>已驳回</el-tag>
              <el-tag v-else type="info">未知状态</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="操作" fixed="right" min-width="100">
            <template #default="{ row }">
              <div class="action-cell">
                <el-tooltip content="编辑文章" placement="top" :enterable="false">
                  <el-button :icon="Edit" v-if="userInfoStore().nickname === row.createUserName" circle plain
                             type="primary" @click="openEditDrawer(row)"></el-button>
                </el-tooltip>
                <el-tooltip content="删除该文章" placement="top" :enterable="false">
                  <el-button :icon="Delete" circle plain type="danger"
                             @click="deleteArticle(row.id,row.createUserName)"></el-button>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂未发现符合条件的文章" :image-size="100"/>
          </template>
        </el-table>

        <div class="pagination-footer">
          <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="[5, 10, 15, 20]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              :total="total"
              @size-change="onSizeChange"
              @current-change="onCurrentChange"
          />
        </div>
      </template>
    </div>

    <el-drawer v-model="visibleDrawer" :title="drawerTitle" direction="rtl" size="55%"
               :before-close="beforeCloseDrawer">
      <el-form :model="articleModel" ref="articleModelRef" :rules="articleModelRule" label-position="top">
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="文章标题" prop="title" :error="errorList.title">
              <el-input v-model="articleModel.title" maxlength="30" show-word-limit
                        placeholder="一个好标题是文迹之旅的第一步"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="文章分类" prop="categoryId">
              <el-select placeholder="请选择" v-model="articleModel.categoryId" style="width: 100%"
                         popper-class="category-select-dropdown">
                <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="封面管理（上传成功即修改）">
          <div class="cover-upload-wrapper">
            <el-upload
                ref="coverRef"
                class="avatar-uploader"
                :auto-upload="true"
                :show-file-list="false"
                action="/api/article/uploadCover"
                name="cover"
                :headers="{'Authorization':tokenStorage().token}"
                :before-upload="uploadCheck"
                :on-success="uploadSuccess"
                method="PATCH"
            >
              <div v-if="articleModel.coverImgSrc" class="cover-preview">
                <img :src="articleModel.coverImgSrc" class="avatar"/>
                <div class="overlay">
                  <el-icon>
                    <Edit/>
                  </el-icon>
                  <span>更换封面</span></div>
              </div>
              <el-icon v-else class="avatar-uploader-icon">
                <Plus/>
              </el-icon>
            </el-upload>
            <el-button v-if="articleModel.coverImgSrc" type="danger" plain @click="cleanCover">删除封面</el-button>
          </div>
        </el-form-item>

        <el-form-item label="文章正文" prop="content" required :error="errorList.content">
          <div class="editor-container">
            <quill-editor
                ref="quillEditorRef"
                theme="snow"
                v-model:content="articleModel.content"
                contentType="html"
                @blur="handleEditorBlur"
            />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button type="info" @click="addOrUpdateArticle(0)" round size="large">存为草稿</el-button>
          <el-button type="primary" @click="addOrUpdateArticle(1)" round size="large">立即发布</el-button>
        </div>
      </template>
    </el-drawer>
    <el-drawer
        v-model="previewDrawer"
        title="文章预览"
        direction="rtl"
        size="55%"
        custom-class="modern-preview-drawer"
    >
      <div class="preview-container">
        <h1 class="article-title">{{ previewData.title }}</h1>

        <div class="article-meta">
          <div class="meta-left">
        <span class="meta-item author">
          <el-icon><User/></el-icon> {{ previewData.createUserName }}
        </span>
            <span class="meta-item time">
          <el-icon><Calendar/></el-icon> {{ previewData.createTime }}
        </span>
          </div>
          <div class="meta-right">
            <el-tag size="small" effect="plain" round>{{ previewData.categoryName }}</el-tag>
            <el-tag v-if="previewData.state === 0" type="primary" size="small" round effect="dark">草稿</el-tag>
            <el-tag v-else-if="previewData.state === 1" type="success" size="small" round effect="dark">发布</el-tag>
            <el-tag v-else-if="previewData.state === 2" type="warning" size="small" round effect="dark">待审核</el-tag>
            <el-tag v-else-if="previewData.state === 3" type="danger" size="small" round effect="dark">已驳回</el-tag>
            <el-tag v-else type="info" size="small" round effect="dark">未知状态</el-tag>
          </div>
        </div>

        <el-divider/>
        <div class="article-content ql-editor" v-html="previewData.content"></div>
      </div>

      <template #footer v-if="userInfoStore().type === 0 && previewData.state !==0">
        <div class="drawer-footer">
          <el-button type="success" v-if="previewData.state !==1" @click="assessArticle(previewData.id,1)" round
                     size="large">通过
          </el-button>
          <el-button type="danger" v-if="previewData.state !==3" @click="assessArticle(previewData.id,3)" round
                     size="large">驳回
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>
<style>
.category-select-dropdown .el-select-dropdown__list {
  max-height: 200px;
}
</style>
<style lang="scss" scoped>
.article-manage-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
  animation: fadeIn 0.5s ease-out;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding-bottom: 12px;
  border-bottom: 1px solid #f2f2f2;

  .title-group {
    .main-title {
      font-size: 24px;
      font-weight: bold;
      color: #1a1a1a; // 稍微加深一点颜色更显质感
      margin: 0; // 极其关键：重置 h2 默认外边距，否则无法与分类页面对齐
    }

    .sub-tip {
      font-size: 13px;
      color: #999;
      margin: 4px 0 0; // 设置固定的上边距，确保副标题位置统一
      padding: 0;
    }
  }
}

/* 搜索栏背景微调，确保各页面色值统一 */
.search-bar {
  background: #f8fafc; // 使用更清爽的蓝灰色调，替代较暗的 #f4f4f4
  padding: 10px 20px 2px;
  border-radius: 8px;
  margin-bottom: 10px;
}

.pic_box {
  width: 130px;
  height: 75px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #eee;

  .full-img {
    width: 100%;
    height: 100%;
    transition: transform 0.3s ease;

    &:hover {
      transform: scale(1.08);
    }
  }

  .image-slot {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
    background: #f5f7fa;
    color: #909399;
  }
}

.table-article-title {
  font-weight: 600;
  color: #2c3e50;
  font-size: 15px;

  &:hover {
    color: #409eff;
    cursor: pointer;
  }
}

.time-column {
  .main-time {
    font-size: 14px;
    display: flex;
    align-items: center;
    gap: 5px;
    color: #444;
    font-weight: 500;
  }

  .sub-time {
    font-size: 11px;
    color: #a5a5a5;
    margin-top: 4px;
  }
}

.status-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  border: #e1e1e1 1px solid;

  &.is-active {
    background: #f0f9eb;
    color: #74c34d;
  }

  &.is-draft {
    background: #fdf6ec;
    color: #eda434;
  }
}

.action-cell {
  display: flex;
  gap: 8px;
}


.editor-container {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;

  :deep(.ql-toolbar) {
    border: none;
    border-bottom: 1px solid #dcdfe6;
    background: #fcfcfc;
  }

  :deep(.ql-container) {
    border: none;
    min-height: 350px;
    font-size: 15px;
  }
}

.cover-upload-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 15px;
}

.cover-preview {
  width: 178px;
  height: 178px;
  position: relative;
  border-radius: 6px;
  overflow: hidden;

  .avatar {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    opacity: 0;
    transition: 0.3s;

    .el-icon {
      font-size: 24px;
      margin-bottom: 4px;
    }
  }

  &:hover .overlay {
    opacity: 1;
  }
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 178px;
  height: 178px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  justify-content: center;
  align-items: center;

  &:hover {
    border-color: #409eff;
  }
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding-top: 10px;
}

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

:deep(.modern-table) {
  .el-table__header th {
    background-color: #fcfcfc;
    color: #606266;
    font-weight: bold;
  }
}

.table-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 10px;
}

.ql-editor {
  padding: 0;
}

:deep(.modern-table) {
  .el-table__header th {
    background-color: #fcfcfc;
    color: #606266;
    font-weight: bold;
  }


  .el-table__row {
    height: 90px;
  }
}


.pagination-footer {
  margin-top: 25px;
  display: flex;
  justify-content: flex-end;
}


.creator-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: #f1f5f9;
  border-radius: 6px;
  font-size: 13px;
  color: #475569;

  &.is-me {
    background: #ecf5ff;
    color: #409eff;
    font-weight: 500;
  }
}

/* 抽屉整体背景色调优 */
:deep(.el-drawer__header) {
  margin-bottom: 0 !important;
}

:deep(.modern-preview-drawer) {
  background-color: #fdfdfd;
}

.el-drawer__header {
  margin-bottom: 0 !important;
  padding-bottom: 0 !important;
  border-bottom: 1px solid #f2f2f2;
  color: #333;
  font-weight: bold;
}

.preview-container {
  padding: 0 40px 40px;
  max-width: 800px;
  margin: 0 auto;

  /* 标题样式 */
  .article-title {
    font-size: 28px;
    font-weight: 700;
    color: #1a1a1a;
    line-height: 1.4;
    margin-bottom: 20px;
  }

  /* 元数据栏 */
  .article-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .meta-item {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 13px;
      color: #888;
      margin-right: 20px;

      .el-icon {
        font-size: 15px;
      }
    }

    .meta-right {
      display: flex;
      gap: 10px;
    }
  }

  /* 封面图美化 */
  .article-cover {
    width: 250px;
    margin-bottom: 30px;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);

    .el-image {
      width: 100%;
      display: block;
      transition: transform 0.3s;

      &:hover {
        transform: scale(1.02);
      }
    }

    .image-placeholder {
      height: 200px;
      background: #f5f7fa;
      display: flex;
      justify-content: center;
      align-items: center;
      color: #909399;
    }
  }

  /* 正文样式排版 */
  .article-content {
    font-size: 16px;
    line-height: 1.8;
    color: #3f3f3f;
    letter-spacing: 0.5px;

    /* 处理 v-html 内部的图片 */
    :deep(img) {
      max-width: 100%;
      height: auto;
      border-radius: 4px;
      display: block;
      margin: 20px auto;
    }

    /* 段落间距 */
    :deep(p) {
      margin-bottom: 1.5em;
    }

    /* 引用块样式 */
    :deep(blockquote) {
      border-left: 4px solid #e2e8f0;
      padding-left: 16px;
      color: #64748b;
      font-style: italic;
      margin: 20px 0;
    }
  }
}

.drawer-footer {
  border-top: 1px solid #f2f2f2;
  padding: 15px 20px;
  text-align: right;
}

</style>