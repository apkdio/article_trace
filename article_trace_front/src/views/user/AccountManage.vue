<script setup>
/* 逻辑部分保持不变，仅增加统计数据的 computed */
import {Delete, Timer, User, Avatar, Top, Bottom} from '@element-plus/icons-vue'
import {onMounted, ref, computed} from 'vue'
import {changeType, checkType, getAllAccountsService} from "@/api/user.js";
import {confirmDeleteAccount} from "@/api/confirmDeleteAccount.js";
import defaultAvatar from "@/assets/defaultLogo.jpg";
import {userInfoStore} from "@/stores/userInfo.js";
import router from "@/router/index.js";

const accountData = ref([])
const isLoading = ref(true)
const pageNum = ref(1)
const total = ref(0)
const pageSize = ref(10) // 默认 10 条更适合管理页

// 统计逻辑
const stats = computed(() => {
  return {
    total: total.value,
    admins: accountData.value.filter(a => a.type === 0).length,
    authors: accountData.value.filter(a => a.type === 1).length
  }
})

onMounted(async () => {
  if (!checkType([0])) router.push({name: "ErrorPage"})
  await getAccounts()
  setTimeout(() => {
    isLoading.value = false
  },100)
})

const getAccounts = async () => {
  try {
    const data = await getAllAccountsService(pageNum.value, pageSize.value)
    if (data.code === 0) {
      accountData.value = data.data.items
      total.value = data.data.total
    } else {
      ElMessage.error(data.message)
    }
  } catch (error) {
    ElMessage.error("数据获取失败！")
  }
}

const deleteAccount = async (id, username) => {
  await confirmDeleteAccount(id, username)
  getAccounts()
}

const onSizeChange = (size) => {
  pageSize.value = size
  getAccounts()
}
const onCurrentChange = (num) => {
  pageNum.value = num
  getAccounts()
}

const changeUserType = (id, type) => {
  let title = ""
  if (type === 0) title = "提升为站长"
  if (type === 1) title = "降职为作者"
  ElMessageBox.confirm(`确定将该用户${title}吗？`, "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    showCancelButton: true,
    closeOnClickModal: false,
    closeOnPressEscape: false,
    center: true,
  }).then(() => {
        try {
          ElMessageBox.prompt("请输入站长密码！", "提示", {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            type: "warning",
            showCancelButton: true,
            buttonSize: "default",
            inputType:"password"
          }).then(async ({value}) => {
            const result = await changeType(id, type, `${value}`)
            if (result.code === 0) {
              ElMessage.success("操作成功！")
              await getAccounts()
            } else ElMessage.error(result.message.error ? result.message.error : "操作失败！")
          })
              .catch(() => {
                ElMessage.info("取消操作！")
              })
        } catch (error) {
          ElMessage.error("服务器响应失败！")
        }
      }
  ).catch(() => {
    ElMessage.info("取消操作！")
  })
}
</script>
<template>

  <div class="account-manage-container" v-loading="isLoading" element-loading-text="加载账号数据...">
    <div class="page-header">
      <div class="title-group">
        <h2 class="main-title">站内账号管理</h2>
        <p class="sub-tip">监控系统用户活跃度，管理权限与账户安全</p>
      </div>
    </div>

    <template v-if="!isLoading">
      <el-row :gutter="20" class="stat-section">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-icon purple">
              <el-icon>
                <User/>
              </el-icon>
            </div>
            <div class="stat-content">
              <span class="stat-label">总注册用户</span>
              <span class="stat-value">{{ total }}</span>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-icon blue">
              <el-icon>
                <Avatar/>
              </el-icon>
            </div>
            <div class="stat-content">
              <span class="stat-label">站长</span>
              <span class="stat-value">{{ stats.admins }}</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="table-container">
        <el-table :data="accountData" style="width: 100%" class="modern-table" row-key="id">
          <el-table-column label="用户身份" width="300">
            <template #default="{ row }">
              <div class="user-profile">
                <el-avatar :size="44" :src="row.userPicSrc || defaultAvatar" class="custom-avatar"/>
                <div class="name-box">
                  <span class="username">{{ row.username }}</span>
                  <span class="nickname">@{{ row.nickname || '未设置昵称' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="邮箱联系方式" width="220">
            <template #default="{ row }">
              <span class="email-text">{{ row.email }}</span>
            </template>
          </el-table-column>

          <el-table-column label="权限角色" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.type === 0" effect="dark" type="danger" border-radius="4" disable-transitions>站长
              </el-tag>
              <el-tag v-else-if="row.type === 1" effect="light" type="primary" disable-transitions>作者</el-tag>
              <el-tag v-else type="info" plain disable-transitions>读者</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="活跃轨迹" width="250">
            <template #default="{ row }">
              <div class="time-track">
                <div class="time-item">
                  <el-icon>
                    <Timer/>
                  </el-icon>
                  <span>注册于 {{ row.createTime }}</span>
                </div>
                <div class="time-sub">最近登录: {{ row.lastLogin || '从未登录' }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="资料更新于" width="220">
            <template #default="{ row }">
              <div class="time-track">
                <div class="time-item">
                  <el-icon>
                    <Timer/>
                  </el-icon>
                  <span>{{ row.updateTime || "暂无"}}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-btns">
                <el-tooltip
                    v-if="row.username !== userInfoStore().username && row.type === 1"
                    content="提升为站长"
                    placement="top"
                >
                  <el-button :icon="Top" circle plain type="primary" @click="changeUserType(row.id,0)"/>
                </el-tooltip>
                <el-tooltip
                    v-if="row.username !== userInfoStore().username && row.type === 0"
                    content="降职为作者"
                    placement="top"
                >
                  <el-button :icon="Bottom" circle plain type="warning" @click="changeUserType(row.id,1)"/>
                </el-tooltip>
                <el-tooltip
                    v-if="row.username !== userInfoStore().username"
                    content="注销账号"
                    placement="top"
                >
                  <el-button :icon="Delete" circle plain type="danger" @click="deleteAccount(row.id, row.username)"/>
                </el-tooltip>
                <el-tag v-else type="success" size="small" effect="plain">当前登录</el-tag>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-footer">
          <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              :total="total"
              @size-change="onSizeChange"
              @current-change="onCurrentChange"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.account-manage-container {
  display: flex;
  flex-direction: column;
  animation: fadeIn 0.5s ease-out;
  height: auto;
}

/* 标题栏 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding-bottom: 12px;
  border-bottom: 1px solid #f2f2f2;
  margin-bottom: 24px;

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

/* 统计卡片样式 */
.stat-section {
  margin-bottom: 24px;
}

.stat-item {
  background: #f8fafc;
  border: 1px solid #edf2f7;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 15px;
  transition: all 0.3s;

  &:hover {
    background: #fff;
    border-color: #409eff;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }

  .stat-icon {
    width: 44px;
    height: 44px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;

    &.purple {
      background: #f5f3ff;
      color: #7c3aed;
    }

    &.blue {
      background: #eff6ff;
      color: #2563eb;
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

/* 用户信息展示列 */
.user-profile {
  display: flex;
  align-items: center;
  gap: 12px;

  .custom-avatar {
    border: 2px solid #fff;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  }

  .name-box {
    display: flex;
    flex-direction: column;

    .username {
      font-weight: 600;
      color: #2c3e50;
      font-size: 14px;
    }

    .nickname {
      font-size: 12px;
      color: #94a3b8;
      margin-top: 2px;
    }
  }
}

.email-text {
  font-size: 13px;
  color: #64748b;
}

/* 活跃轨迹 */
.time-track {
  .time-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #475569;
  }

  .time-sub {
    font-size: 11px;
    color: #b2becd;
    margin-top: 4px;
    padding-left: 19px;
  }
}

/* 表格深度定制 */
:deep(.modern-table) {
  .el-table__header th {
    background-color: #fcfcfc;
    color: #606266;
    font-weight: bold;
    font-size: 13px;
  }

  .el-table__row {
    height: 80px; /* 适当增加行高，容纳头像 */
  }
}

.pagination-footer {
  margin-top: 30px;
  display: flex;
  justify-content: flex-end;
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