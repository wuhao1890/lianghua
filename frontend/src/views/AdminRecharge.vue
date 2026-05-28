<template>
  <div class="admin-recharge-page">
    <!-- 给用户充值 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="card-header">
          <el-icon><Plus /></el-icon>
          <span>给用户充值</span>
        </div>
      </template>

      <el-form
        ref="rechargeFormRef"
        :model="rechargeForm"
        :rules="rechargeRules"
        label-width="100px"
        :inline="true"
        class="recharge-form"
      >
        <el-form-item label="用户ID" prop="userId">
          <el-input-number
            v-model="rechargeForm.userId"
            :min="1"
            placeholder="请输入用户ID"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="充值金额" prop="amount">
          <el-input-number
            v-model="rechargeForm.amount"
            :min="1"
            :max="10000000"
            :precision="2"
            :step="100"
            placeholder="请输入金额"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="rechargeForm.remark"
            placeholder="备注（选填）"
            style="width: 200px;"
            maxlength="200"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="rechargeLoading"
            @click="handleAdminRecharge"
          >
            确认充值
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 待确认充值 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="card-header">
          <el-icon><Clock /></el-icon>
          <span>待确认充值</span>
          <el-tag type="warning" size="small" style="margin-left: 8px;">
            {{ pendingRecords.length }} 条待处理
          </el-tag>
        </div>
      </template>

      <el-table
        :data="pendingRecords"
        v-loading="pendingLoading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="userId" label="用户ID" width="100" align="center" />
        <el-table-column prop="amount" label="金额" width="140" align="right">
          <template #default="{ row }">
            <span class="amount-text">¥{{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createTime" label="申请时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button
              type="success"
              size="small"
              :loading="row._confirming"
              @click="handleConfirm(row)"
            >
              确认
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!pendingLoading && pendingRecords.length === 0" description="暂无待确认的充值申请" />
    </el-card>

    <!-- 所有充值记录 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="card-header">
          <el-icon><List /></el-icon>
          <span>所有充值记录</span>
        </div>
      </template>

      <div class="filter-bar">
        <el-select
          v-model="statusFilter"
          placeholder="筛选状态"
          clearable
          style="width: 140px; margin-right: 12px;"
          @change="handleFilterChange"
        >
          <el-option label="全部" value="" />
          <el-option label="待确认" value="PENDING" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </div>

      <el-table
        :data="allRecords"
        v-loading="allLoading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="userId" label="用户ID" width="100" align="center" />
        <el-table-column prop="amount" label="金额" width="140" align="right">
          <template #default="{ row }">
            <span class="amount-text">¥{{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="120" align="center">
          <template #default="{ row }">
            {{ typeText(row.type) }}
          </template>
        </el-table-column>
        <el-table-column prop="operatorId" label="操作人ID" width="100" align="center">
          <template #default="{ row }">
            {{ row.operatorId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!allLoading && allRecords.length === 0" description="暂无充值记录" />

      <div class="pagination-wrapper" v-if="allTotal > allPageSize">
        <el-pagination
          v-model:current-page="allCurrentPage"
          :page-size="allPageSize"
          :total="allTotal"
          layout="total, prev, pager, next"
          @current-change="fetchAllRecords"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { adminRecharge, confirmRecharge, getAllRechargeRecords } from '@/api/recharge'
import type { RechargeOrder } from '@/types'

// ---- 给用户充值 ----
const rechargeFormRef = ref<FormInstance>()
const rechargeLoading = ref(false)
const rechargeForm = reactive({
  userId: undefined as number | undefined,
  amount: undefined as number | undefined,
  remark: ''
})

const rechargeRules: FormRules = {
  userId: [
    { required: true, message: '请输入用户ID', trigger: 'blur' }
  ],
  amount: [
    { required: true, message: '请输入充值金额', trigger: 'blur' },
    { type: 'number', min: 1, message: '金额不能小于1元', trigger: 'blur' }
  ]
}

async function handleAdminRecharge() {
  if (!rechargeFormRef.value) return
  await rechargeFormRef.value.validate(async (valid) => {
    if (!valid) return
    rechargeLoading.value = true
    try {
      await adminRecharge({
        userId: rechargeForm.userId!,
        amount: rechargeForm.amount!,
        remark: rechargeForm.remark || undefined
      })
      ElMessage.success('充值成功')
      rechargeForm.userId = undefined
      rechargeForm.amount = undefined
      rechargeForm.remark = ''
      fetchPendingRecords()
      fetchAllRecords()
    } catch (error) {
      // error handled by interceptor
    } finally {
      rechargeLoading.value = false
    }
  })
}

// ---- 待确认充值 ----
const pendingRecords = ref<(RechargeOrder & { _confirming?: boolean })[]>([])
const pendingLoading = ref(false)

async function fetchPendingRecords() {
  pendingLoading.value = true
  try {
    const res = await getAllRechargeRecords({ page: 1, pageSize: 100, status: 'PENDING' })
    const data = res.data.data
    pendingRecords.value = (data.list || data.records || []).map((item: RechargeOrder) => ({
      ...item,
      _confirming: false
    }))
  } catch (error) {
    // error handled by interceptor
  } finally {
    pendingLoading.value = false
  }
}

async function handleConfirm(row: RechargeOrder & { _confirming?: boolean }) {
  try {
    await ElMessageBox.confirm(
      `确认给用户 ${row.userId} 充值 ¥${row.amount.toFixed(2)}？`,
      '确认充值',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  row._confirming = true
  try {
    await confirmRecharge(row.id)
    ElMessage.success('已确认充值')
    fetchPendingRecords()
    fetchAllRecords()
  } catch (error) {
    // error handled by interceptor
  } finally {
    row._confirming = false
  }
}

// ---- 所有充值记录 ----
const allRecords = ref<RechargeOrder[]>([])
const allLoading = ref(false)
const allCurrentPage = ref(1)
const allPageSize = ref(10)
const allTotal = ref(0)
const statusFilter = ref('')

function handleFilterChange() {
  allCurrentPage.value = 1
  fetchAllRecords()
}

async function fetchAllRecords() {
  allLoading.value = true
  try {
    const params: { page: number; pageSize: number; status?: string } = {
      page: allCurrentPage.value,
      pageSize: allPageSize.value
    }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const res = await getAllRechargeRecords(params)
    const data = res.data.data
    allRecords.value = data.list || data.records || []
    allTotal.value = data.total || 0
  } catch (error) {
    // error handled by interceptor
  } finally {
    allLoading.value = false
  }
}

// ---- 工具函数 ----
function formatMoney(value: number): string {
  return value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}

function statusTagType(status: string): 'warning' | 'success' | 'danger' {
  const map: Record<string, 'warning' | 'success' | 'danger'> = {
    PENDING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger'
  }
  return map[status] || 'warning'
}

function statusText(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待确认',
    SUCCESS: '成功',
    FAILED: '失败'
  }
  return map[status] || status
}

function typeText(type: string): string {
  const map: Record<string, string> = {
    WECHAT: '微信转账',
    ADMIN: '管理员充值'
  }
  return map[type] || type
}

onMounted(() => {
  fetchPendingRecords()
  fetchAllRecords()
})
</script>

<style scoped lang="scss">
.admin-recharge-page {
  max-width: 1400px;
  margin: 0 auto;
}

.section-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 16px;
  font-weight: 600;
}

.recharge-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
}

.amount-text {
  font-weight: 600;
  color: #409eff;
}

.filter-bar {
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
