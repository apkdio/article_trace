<script setup>
import {Delete, Edit, Collection, UserFilled, Timer, Plus, Refresh} from '@element-plus/icons-vue'
import {addCategory, deleteCategory, getAllCategories, updateCategory} from "@/api/category.js"
import {onMounted, ref, computed} from 'vue'
import {userInfoStore} from "@/stores/userInfo.js";
import {checkPersonInfo} from "@/api/checkPersonInfo.js";
import router from "@/router/index.js";
import {checkType} from "@/api/user.js";

const categories = ref([])
const categoryRef = ref()
const isLoading = ref(true)
const showAddCategory = ref(false)
const errorList = ref({})
const state = ref(0)
const categoryData = ref({
  categoryId: null,
  categoryName: "",
  categoryAlias: ""
})

const rules = {
  categoryName: [{required: true, message: '请输入分类名称', trigger: 'blur'},
    {min: 1, max: 20, message: "描述过长！"}]
}

const myCategoriesCount = computed(() => {
  return categories.value.filter(c => c.createUserName === userInfoStore().nickname).length
})

onMounted(() => {
  if(!checkType([0,1])) router.push({name:"ErrorPage"})
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
  } catch (error) {
    ElMessage.error("数据获取失败！")
  } finally {
    setTimeout(() => {
      isLoading.value = false
    }, 100)

  }
}

function addOrUpdate() {
  categoryRef.value.validate((valid) => {
    if (valid) {
      let resultData = state.value === 1 ? addCategory(categoryData.value) : updateCategory(categoryData.value)
      resultData.then((result) => {
        if (result.code === 0) {
          ElMessage.success("操作成功！")
          showAddCategory.value = false
          clearInfo()
          getCategories()
        } else {
          errorList.value = result.message || {}
          ElMessage.error("操作失败！")
        }
      }).catch(() => ElMessage.error("系统响应异常"))
    }
  })
}

function editCategory(data) {
  showAddCategory.value = true;
  state.value = 2
  categoryData.value = {
    categoryName: data.categoryName,
    categoryAlias: data.categoryAlias,
    categoryId: data.id
  }
}

function deleteObject(id) {
  ElMessageBox.confirm("确认删除该分类吗？删除后不可恢复。", "警告", {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    type: "warning",
    buttonSize: "default"
  }).then(() => {
    deleteCategory(id).then(res => {
      if (res.code === 0) {
        ElMessage.success("删除成功");
        getCategories();
      }
    })
  }).catch(() => {
  })
}

function clearInfo() {
  errorList.value = {}
  if (categoryRef.value) categoryRef.value.resetFields()
  categoryData.value = {categoryId: null, categoryName: "", categoryAlias: ""}
}

const handleClose = (done) => {
  clearInfo();
  done();
}
</script>

<template>
  <div class="category-manage-main">
    <div class="page-header">
      <div class="title-group">
        <h2 class="main-title">文章分类管理</h2>
        <p class="sub-tip">维护文章的分类体系，方便内容归档与检索</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" circle @click="getCategories" title="刷新数据"/>
        <el-button type="primary" :icon="Plus" size="large" @click="showAddCategory=true;state=1">新增分类</el-button>
      </div>
    </div>


    <div v-loading="isLoading" element-loading-text="正在为您加载文章类型列表...">
      <template v-if="!isLoading">
        <el-row :gutter="20" class="stat-section">
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-icon blue">
                <el-icon>
                  <Collection/>
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-label">全站分类</span>
                <span class="stat-value">{{ categories.length }}</span>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-icon green">
                <el-icon>
                  <UserFilled/>
                </el-icon>
              </div>
              <div class="stat-content">
                <span class="stat-label">我创建的</span>
                <span class="stat-value">{{ myCategoriesCount }}</span>
              </div>
            </div>
          </el-col>
        </el-row>

        <div class="table-container">
          <el-table :data="categories" style="width: 100%" class="modern-table">
            <el-table-column label="序号" width="100" type="index" align="center"/>

            <el-table-column label="分类名称 / 描述" width=260>
              <template #default="{ row }">
                <div class="category-info">
                  <span class="category-name">{{ row.categoryName }}</span>
                  <span class="category-alias">{{ row.categoryAlias || '暂无详细描述...' }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="创建者" width="240">
              <template #default="{ row }">
                <div class="creator-tag" :class="{ 'is-me': row.createUserName === userInfoStore().nickname }">
                  <el-icon v-if="row.createUserName === userInfoStore().nickname">
                    <UserFilled/>
                  </el-icon>
                  <span>{{ row.createUserName }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="时间轨迹">
              <template #default="{ row }">
                <div class="time-track">
                  <div class="create-time">
                    <el-icon>
                      <Timer/>
                    </el-icon>
                    <span>{{ row.createTime }}</span>
                  </div>
                  <div class="update-info">
                    更新于: {{ row.updateTime || '暂无修改' }}
                    <span v-if="row.lastUpdateUserName">({{ row.lastUpdateUserName }})</span>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-tooltip content="修改分类信息" placement="top" :enterable="false">
                    <el-button :icon="Edit" circle plain type="primary" @click="editCategory(row)"></el-button>
                  </el-tooltip>
                  <el-tooltip v-if="row.createUserName === userInfoStore().nickname || userInfoStore().type === 0"
                              content="永久删除" placement="top" :enterable="false">
                    <el-button :icon="Delete" circle plain type="danger" @click="deleteObject(row.id)"></el-button>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="还没有分类，点击右上方新增一个吧" :image-size="120"/>
            </template>
          </el-table>
        </div>
      </template>
    </div>

    <el-dialog v-model="showAddCategory" :title="state === 1 ? '新增文章分类' : '编辑分类信息'" width="450px"
               class="custom-dialog" :before-close="handleClose">
      <el-form :model="categoryData" :rules="rules" label-position="top" ref="categoryRef">
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="categoryData.categoryName" placeholder="输入名称（如：技术分享、生活随笔）" maxlength="20"
                    show-word-limit></el-input>
        </el-form-item>
        <el-form-item label="分类别名 / 描述" prop="categoryAlias">
          <el-input v-model="categoryData.categoryAlias" type="textarea" :rows="3"
                    placeholder="输入该分类的详细描述，有助于清晰管理内容" maxlength="30" show-word-limit></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showAddCategory = false;clearInfo()" round>取消返回</el-button>
          <el-button type="primary" @click="addOrUpdate()" round px-4>确认提交</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.category-manage-main {
  display: flex;
  flex-direction: column;
  animation: fadeIn 0.5s ease-out;
}

/* 标题栏对齐 articleManage */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding-bottom: 12px;
  border-bottom: 1px solid #f2f2f2;
  margin-bottom: 24px;

  .title-group {
    .main-title {
      font-size: 24px;
      font-weight: bold;
      color: #1a1a1a;
      margin: 0;
    }

    .sub-tip {
      font-size: 13px;
      color: #999;
      margin: 4px 0 0;
    }
  }

  .header-actions {
    display: flex;
    gap: 12px;
  }
}

/* 统计卡片：扁平化中层设计 */
.stat-section {
  margin-bottom: 30px;
}

.stat-item {
  background: #f8fafc; /* 浅色背景替代阴影卡片 */
  border: 1px solid #edf2f7;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 15px;
  transition: all 0.3s;

  &:hover {
    background: #ffffff;
    border-color: #409eff;
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1);
  }

  .stat-icon {
    width: 44px;
    height: 44px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;

    &.blue {
      background: #e0e7ff;
      color: #4f46e5;
    }

    &.green {
      background: #dcfce7;
      color: #16a34a;
    }
  }

  .stat-content {
    display: flex;
    flex-direction: column;

    .stat-label {
      font-size: 12px;
      color: #64748b;
    }

    .stat-value {
      font-size: 20px;
      font-weight: 700;
      color: #1e293b;
    }
  }
}

/* 表格内部样式保留并美化 */
.category-info {
  display: flex;
  flex-direction: column;

  .category-name {
    font-weight: 600;
    color: #2c3e50;
    font-size: 15px;
  }

  .category-alias {
    font-size: 12px;
    color: #a0aec0;
    margin-top: 2px;
  }
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

.time-track {
  .create-time {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: #4a5568;
  }

  .update-info {
    font-size: 11px;
    color: #b2becd;
    margin-top: 4px;
  }
}

.action-btns {
  display: flex;
  gap: 8px;
}

/* 对齐之前的表格样式 */
:deep(.modern-table) {
  .el-table__header th {
    background-color: #fcfcfc;
    color: #606266;
    font-weight: bold;
    border-bottom: 1px solid #f0f0f0;
  }
}

/* 弹窗圆角与样式 */
:deep(.custom-dialog) {
  border-radius: 12px;

  .el-dialog__header {
    margin-right: 0;
    padding-bottom: 10px;
    border-bottom: 1px solid #f2f2f2;
  }

  .el-form-item__label {
    font-weight: 600;
    color: #444;
  }
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
</style>