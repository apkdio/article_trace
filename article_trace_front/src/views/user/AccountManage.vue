<script setup>
/* 逻辑部分保持不变，仅增加统计数据的 computed */
import {Delete, Timer, User, Avatar, Top, Bottom} from '@element-plus/icons-vue'
import {onMounted, ref, computed} from 'vue'
import {changeType, checkType, getAllAccountsService} from "@/api/user.js";
import {listApplies, reviewApply} from "@/api/apply.js";
import {confirmDeleteAccount} from "@/api/confirmDeleteAccount.js";
import defaultAvatar from "@/assets/defaultLogo.jpg";
import {userInfoStore} from "@/stores/userInfo.js";
import router from "@/router/index.js";
import {promptMasterPassword} from "@/utils/confirm.js";
import PageHeader from "@/components/PageHeader.vue";
import StatCard from "@/components/StatCard.vue";

const accountData = ref([])
const isLoading = ref(true)
const pageNum = ref(1)
const total = ref(0)
const pageSize = ref(10) // 默认 10 条更适合管理页

// 作者申请（待审）
const pendingApplies = ref([])

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
  getPendingApplies()
  setTimeout(() => {
    isLoading.value = false
  },100)
})

const getPendingApplies = async () => {
  try {
    const res = await listApplies(0, 1, 20) // status=0 待审
    if (res.code === 0) {
      pendingApplies.value = res.data?.items || []
    }
  } catch (e) {
    // 申请列表获取失败不影响账号管理主功能
  }
}

const approveApply = async (item) => {
  try {
    await ElMessageBox.confirm(
        `确定通过「${item.nickname || item.username}」的作者申请？通过后该用户将升级为作者。`,
        "提示",
        {confirmButtonText: "通过", cancelButtonText: "取消", type: "warning", center: true}
    )
  } catch (e) {
    return
  }
  try {
    const res = await reviewApply(item.id, true, '')
    if (res.code === 0) {
      ElMessage.success('已通过该申请')
      await getPendingApplies()
      await getAccounts()
    } else {
      ElMessage.error(res.message || '操作失败！')
    }
  } catch (e) {
    ElMessage.error('服务器响应失败！')
  }
}

const rejectApply = async (item) => {
  try {
    const {value} = await ElMessageBox.prompt("请填写拒绝原因（可留空）", "拒绝申请", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      inputPlaceholder: "如：暂不符合要求"
    })
    const res = await reviewApply(item.id, false, value || '')
    if (res.code === 0) {
      ElMessage.success('已拒绝该申请')
      await getPendingApplies()
    } else {
      ElMessage.error(res.message || '操作失败！')
    }
  } catch (e) {
    // 用户取消
  }
}

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
          promptMasterPassword().then(async ({value}) => {
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
    <PageHeader title="站内账号管理" subtitle="监控系统用户活跃度，管理权限与账户安全"/>

    <template v-if="!isLoading">
      <el-row :gutter="20" class="stat-section">
        <el-col :span="6">
          <StatCard label="总注册用户" :value="total" color="purple">
            <template #icon><el-icon><User/></el-icon></template>
          </StatCard>
        </el-col>
        <el-col :span="6">
          <StatCard label="站长" :value="stats.admins" color="blue">
            <template #icon><el-icon><Avatar/></el-icon></template>
          </StatCard>
        </el-col>
      </el-row>

      <!-- 作者申请待审 -->
      <el-card v-if="pendingApplies.length > 0" class="apply-card" shadow="never">
        <template #header>
          <div class="apply-header">
            <span class="apply-title">作者申请待审</span>
            <el-tag type="warning" size="small" disable-transitions>{{ pendingApplies.length }}</el-tag>
          </div>
        </template>
        <div v-for="a in pendingApplies" :key="a.id" class="apply-item">
          <div class="apply-main">
            <div class="apply-user">
              {{ a.nickname || '未设置昵称' }}
              <span class="apply-username">@{{ a.username }}</span>
            </div>
            <div class="apply-reason">{{ a.reason || '（未填写理由）' }}</div>
            <div class="apply-time">{{ a.createTime }}</div>
          </div>
          <div class="apply-actions">
            <el-button type="success" size="small" @click="approveApply(a)">通过</el-button>
            <el-button type="danger" size="small" @click="rejectApply(a)">拒绝</el-button>
          </div>
        </div>
      </el-card>

      <div class="table-container">
        <el-table :data="accountData" style="width: 100%" class="modern-table" row-key="id">
          <el-table-column label="用户身份" width="300">
            <template #default="{ row }">
              <div class="user-profile">
                <el-avatar :size="44" :src="row.userPicThumbSrc || defaultAvatar" class="custom-avatar"/>
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
          <el-table-column label="资料更新于" >
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

/* 作者申请待审卡片 */
.apply-card {
  margin-bottom: 20px;

  .apply-header {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .apply-title {
    font-weight: 600;
    color: #2c3e50;
  }

  .apply-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 10px 0;
    border-bottom: 1px solid #f2f3f5;

    &:last-child {
      border-bottom: none;
    }
  }

  .apply-main {
    flex: 1;
    min-width: 0;
  }

  .apply-user {
    font-size: 14px;
    font-weight: 600;
    color: #303133;

    .apply-username {
      margin-left: 4px;
      font-size: 12px;
      font-weight: 400;
      color: #909399;
    }
  }

  .apply-reason {
    margin-top: 4px;
    font-size: 13px;
    color: #606266;
    word-break: break-word;
  }

  .apply-time {
    margin-top: 4px;
    font-size: 12px;
    color: #a8abb2;
  }

  .apply-actions {
    flex-shrink: 0;
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




</style>