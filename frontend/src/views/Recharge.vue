<template>
  <div class="recharge-page">
    <!-- 账户余额 -->
    <el-card class="balance-card" shadow="hover">
      <div class="balance-info">
        <div class="balance-label">可用余额</div>
        <div class="balance-amount">
          <span class="currency">¥</span>
          {{ formatMoney(userStore.userInfo?.availableCash || 0) }}
        </div>
      </div>
    </el-card>

    <el-row :gutter="20">
      <!-- 左侧：充值表单 -->
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>账户充值</span>
            </div>
          </template>

          <!-- 微信转账说明 -->
          <div class="wechat-section">
            <el-alert
              title="充值步骤"
              type="info"
              :closable="false"
              show-icon
              description="1. 扫描下方微信二维码添加管理员微信  2. 转账并备注您的用户名  3. 点击下方按钮提交充值申请  4. 等待管理员确认到账"
              style="margin-bottom: 20px;"
            />
            <div class="qr-container">
              <el-image
                src="/wechat-qr.jpg"
                fit="contain"
                class="qr-image"
              >
                <template #error>
                  <div class="image-error">
                    <el-icon :size="40"><Picture /></el-icon>
                    <span>二维码加载失败</span>
                  </div>
                </template>
              </el-image>
              <div class="qr-label">微信二维码</div>
            </div>
          </div>

          <!-- 充值表单 -->
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="80px"
            class="recharge-form"
          >
            <el-form-item label="充值金额" prop="amount">
              <el-input-number
                v-model="form.amount"
                :min="1"
                :max="1000000"
                :precision="2"
                :step="100"
                placeholder="请输入充值金额"
                style="width: 100%;"
              />
            </el-form-item>

            <el-form-item label="快捷金额">
              <div class="preset-amounts">
                <el-button
                  v-for="preset in presetAmounts"
                  :key="preset"
                  :type="form.amount === preset ? 'primary' : 'default'"
                  @click="form.amount = preset"
                >
                  {{ preset }}元
                </el-button>
                <el-button
                  :type="isCustomAmount ? 'primary' : 'default'"
                  @click="form.amount = 0"
                >
                  自定义
                </el-button>
              </div>
            </el-form-item>

            <el-form-item label="备注">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="2"
                placeholder="请输入备注信息（选填）"
                maxlength="200"
                show-word-limit
              />
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="submitting"
                @click="handleSubmit"
                class="submit-btn"
              >
                提交充值申请
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：充值记录 -->
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>充值记录</span>
            </div>
          </template>

          <el-table
            :data="records"
            v-loading="loading"
            stripe
            style="width: 100%"
            max-height="500"
          >
            <el-table-column prop="amount" label="金额" width="100" align="right">
              <template #default="{ row }">
                <span class="amount-text">¥{{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="100" align="center">
              <template #default="{ row }">
                {{ typeText(row.type) }}
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="80" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="160" align="center">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!loading && records.length === 0" description="暂无充值记录" />

          <div class="pagination-wrapper" v-if="total > pageSize">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              small
              @current-change="fetchRecords"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/store/user'
import { applyRecharge, getRechargeRecords } from '@/api/recharge'
import type { RechargeOrder } from '@/types'

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)
const records = ref<RechargeOrder[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const presetAmounts = [100, 500, 1000, 5000]

const isCustomAmount = computed(() => {
  return form.amount > 0 && !presetAmounts.includes(form.amount)
})

const form = reactive({
  amount: 0,
  remark: ''
})

const rules: FormRules = {
  amount: [
    { required: true, message: '请输入充值金额', trigger: 'blur' },
    { type: 'number', min: 1, message: '充值金额不能小于1元', trigger: 'blur' }
  ]
}

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

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.amount <= 0) {
      ElMessage.warning('请输入有效的充值金额')
      return
    }
    submitting.value = true
    try {
      await applyRecharge({
        amount: form.amount,
        remark: form.remark || undefined
      })
      ElMessage.success('充值申请已提交，请等待管理员确认')
      form.amount = 0
      form.remark = ''
      currentPage.value = 1
      await fetchRecords()
    } catch (error) {
      // error handled by interceptor
    } finally {
      submitting.value = false
    }
  })
}

async function fetchRecords() {
  loading.value = true
  try {
    const res = await getRechargeRecords({
      page: currentPage.value,
      pageSize: pageSize.value
    })
    const data = res.data.data
    records.value = data.list || data.records || []
    total.value = data.total || 0
  } catch (error) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchRecords()
})
</script>

<style scoped lang="scss">
.recharge-page {
  max-width: 1400px;
  margin: 0 auto;
}

.balance-card {
  margin-bottom: 20px;

  .balance-info {
    text-align: center;
    padding: 10px 0;
  }

  .balance-label {
    font-size: 14px;
    color: #909399;
    margin-bottom: 8px;
  }

  .balance-amount {
    font-size: 36px;
    font-weight: 700;
    color: #409eff;

    .currency {
      font-size: 20px;
      margin-right: 4px;
    }
  }
}

.card-header {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}

.wechat-section {
  margin-bottom: 24px;
}

.qr-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.qr-image {
  width: 200px;
  height: 200px;
  border-radius: 8px;
  border: 2px solid #e4e7ed;
}

.qr-label {
  margin-top: 10px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #c0c4cc;
  gap: 8px;
  font-size: 14px;
}

.recharge-form {
  margin-top: 10px;
}

.preset-amounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.submit-btn {
  width: 100%;
  margin-top: 10px;
}

.amount-text {
  font-weight: 600;
  color: #409eff;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
