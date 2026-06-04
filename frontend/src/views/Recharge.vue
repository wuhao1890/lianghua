<template>
  <div class="recharge-page">
    <!-- 账户余额 -->
    <el-card class="balance-card" shadow="hover">
      <div class="balance-info">
        <div class="balance-label">模拟账户可用余额</div>
        <div class="balance-amount">
          <span class="currency">¥</span>
          {{ formatMoney(userStore.userInfo?.availableCash || 0) }}
        </div>
      </div>
    </el-card>

    <el-card class="real-broker-card" shadow="hover">
      <template #header>
        <div class="card-header broker-header">
          <span>华宝证券真实资金</span>
          <el-tag :type="brokerStatus?.ready ? 'success' : 'warning'" effect="plain">
            {{ brokerStatus?.ready ? '已具备联调条件' : '未启用' }}
          </el-tag>
        </div>
      </template>
      <el-alert
        title="真钱充值不是系统内充值，应走华宝证券银证转账。未完成华宝官方 OpenAPI 授权前，系统不会发起真实资金划转。"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
      />
      <el-row :gutter="16">
        <el-col :xs="24" :lg="14">
          <el-form :model="brokerForm" label-width="120px" class="broker-form">
            <el-form-item label="接口地址">
              <el-input v-model="brokerForm.apiBase" placeholder="华宝证券提供的正式或测试接口地址" />
            </el-form-item>
            <el-form-item label="客户编号">
              <el-input v-model="brokerForm.clientId" :placeholder="brokerStatus?.clientIdMasked || '华宝证券分配的客户编号'" />
            </el-form-item>
            <el-form-item label="证券账户">
              <el-input v-model="brokerForm.accountId" :placeholder="brokerStatus?.accountIdMasked || '华宝证券账户号'" />
            </el-form-item>
            <el-form-item label="接入确认">
              <div class="confirm-list">
                <el-checkbox v-model="brokerForm.officialDocsConfirmed">已拿到华宝证券官方开放接口文档和授权</el-checkbox>
                <el-checkbox v-model="brokerForm.sandboxReady">测试环境已完成查询、撤单、下单全流程联调</el-checkbox>
                <el-checkbox v-model="brokerForm.cashTransferReady">银证转账接口已完成授权</el-checkbox>
                <el-checkbox v-model="brokerForm.tradingEnabled">我确认开启真实交易闸门</el-checkbox>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingBroker" @click="saveBroker">保存华宝接入配置</el-button>
            </el-form-item>
          </el-form>
        </el-col>
        <el-col :xs="24" :lg="10">
          <div class="real-transfer-box">
            <div class="transfer-title">真实资金划转</div>
            <el-radio-group v-model="realTransfer.direction">
              <el-radio-button value="转入证券账户">转入证券账户</el-radio-button>
              <el-radio-button value="转出银行卡">转出银行卡</el-radio-button>
            </el-radio-group>
            <el-input-number
              v-model="realTransfer.amount"
              :min="1"
              :precision="2"
              :step="1000"
              style="width: 100%; margin-top: 12px;"
            />
            <el-button
              type="danger"
              size="large"
              class="submit-btn"
              :loading="transferring"
              @click="submitRealTransfer"
            >
              发起华宝真实资金划转
            </el-button>
            <div class="blocker-list" v-if="brokerStatus?.blockers?.length">
              <div v-for="item in brokerStatus.blockers" :key="item">阻断：{{ item }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="20">
      <!-- 左侧：充值表单 -->
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>模拟账户充值</span>
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
import { getHuabaoStatus, huabaoCashTransfer, saveHuabaoConfig } from '@/api/trade'
import type { HuabaoBrokerStatus, RechargeOrder } from '@/types'

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)
const records = ref<RechargeOrder[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const brokerStatus = ref<HuabaoBrokerStatus | null>(null)
const savingBroker = ref(false)
const transferring = ref(false)
const brokerForm = reactive({
  apiBase: '',
  clientId: '',
  accountId: '',
  officialDocsConfirmed: false,
  sandboxReady: false,
  cashTransferReady: false,
  tradingEnabled: false
})
const realTransfer = reactive({
  amount: 0,
  direction: '转入证券账户' as '转入证券账户' | '转出银行卡'
})

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

async function fetchBrokerStatus() {
  try {
    const res = await getHuabaoStatus()
    brokerStatus.value = res.data.data
    brokerForm.apiBase = brokerStatus.value.apiBase || ''
    brokerForm.officialDocsConfirmed = Boolean(brokerStatus.value.officialDocsConfirmed)
    brokerForm.sandboxReady = Boolean(brokerStatus.value.sandboxReady)
    brokerForm.cashTransferReady = Boolean(brokerStatus.value.cashTransferReady)
    brokerForm.tradingEnabled = Boolean(brokerStatus.value.tradingEnabled)
  } catch {
    brokerStatus.value = null
  }
}

async function saveBroker() {
  savingBroker.value = true
  try {
    const res = await saveHuabaoConfig(brokerForm)
    brokerStatus.value = res.data.data
    brokerForm.clientId = ''
    brokerForm.accountId = ''
    ElMessage.success('华宝接入配置已保存')
  } finally {
    savingBroker.value = false
  }
}

async function submitRealTransfer() {
  if (realTransfer.amount <= 0) {
    ElMessage.warning('请输入真实划转金额')
    return
  }
  transferring.value = true
  try {
    await huabaoCashTransfer({ ...realTransfer })
    ElMessage.success('真实资金划转已提交')
  } finally {
    transferring.value = false
    fetchBrokerStatus()
  }
}

onMounted(() => {
  fetchRecords()
  fetchBrokerStatus()
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

.real-broker-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.broker-header {
  justify-content: space-between;
}

.broker-form {
  max-width: 720px;
}

.confirm-list {
  display: grid;
  gap: 8px;
}

.real-transfer-box {
  display: grid;
  gap: 12px;
  padding: 16px;
  background: #fff8f0;
  border: 1px solid #f3d19e;
  border-radius: 8px;
}

.transfer-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.blocker-list {
  display: grid;
  gap: 6px;
  color: #b88230;
  font-size: 13px;
  line-height: 1.5;
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
