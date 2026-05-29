<template>
  <div class="risk-page">
    <!-- 风控状态 -->
    <el-row :gutter="16" class="status-row">
      <el-col :xs="12" :sm="6">
        <div class="status-card" :class="dailyLossStatus">
          <div class="status-label">当日亏损</div>
          <div class="status-value">{{ formatMoney(riskStatus?.dailyLoss) }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="status-card" :class="drawdownStatus">
          <div class="status-label">当前回撤</div>
          <div class="status-value">{{ riskStatus?.currentDrawdown ? (riskStatus.currentDrawdown * 100).toFixed(2) + '%' : '0.00%' }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="status-card" :class="positionRatioStatus">
          <div class="status-label">仓位比例</div>
          <div class="status-value">{{ riskStatus?.positionRatio ? (riskStatus.positionRatio * 100).toFixed(1) + '%' : '0.0%' }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="status-card" :class="circuitBreakerStatus">
          <div class="status-label">熔断状态</div>
          <div class="status-value">{{ riskSettings?.circuitBreakerEnabled ? '运行中' : '已关闭' }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 风控设置 -->
    <el-card shadow="hover" class="settings-card">
      <template #header>
        <div class="card-header">
          <span>风控设置</span>
          <el-button text type="primary" :icon="'Refresh'" @click="loadSettings" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-form :model="riskSettings" label-width="160px" label-position="left">
        <el-form-item label="单笔最大金额">
          <el-input-number v-model="riskSettings.maxAmountPerOrder" :min="0" :step="10000" controls-position="right" style="width:200px" />
        </el-form-item>
        <el-form-item label="最大仓位百分比">
          <div class="slider-group">
            <el-slider v-model="riskSettings.maxPositionPercent" :min="0" :max="100" style="width:300px" />
            <span class="slider-value">{{ riskSettings.maxPositionPercent }}%</span>
          </div>
        </el-form-item>
        <el-form-item label="日亏损限额">
          <el-input-number v-model="riskSettings.dailyLossLimit" :min="0" :step="1000" controls-position="right" style="width:200px" />
        </el-form-item>
        <el-form-item label="最大回撤阈值">
          <div class="slider-group">
            <el-slider v-model="riskSettings.maxDrawdownThreshold" :min="0" :max="100" style="width:300px" />
            <span class="slider-value">{{ riskSettings.maxDrawdownThreshold }}%</span>
          </div>
        </el-form-item>
        <el-form-item label="熔断开关">
          <el-switch v-model="riskSettings.circuitBreakerEnabled" />
        </el-form-item>
        <el-form-item label="熔断触发亏损额">
          <el-input-number v-model="riskSettings.circuitBreakerLossAmount" :min="0" :step="1000" :disabled="!riskSettings.circuitBreakerEnabled" controls-position="right" style="width:200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="saving">保存设置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 风控检查 -->
    <el-card shadow="hover" class="check-card" style="margin-top:16px">
      <template #header><span>风控检查</span></template>
      <el-form :model="checkForm" label-width="120px" inline>
        <el-form-item label="股票代码">
          <el-input v-model="checkForm.stockCode" placeholder="输入代码" style="width:140px" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="checkForm.price" :min="0" :step="0.01" controls-position="right" style="width:140px" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="checkForm.quantity" :min="0" :step="100" controls-position="right" style="width:140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCheck" :loading="checking">检查</el-button>
        </el-form-item>
      </el-form>

      <!-- 检查结果 -->
      <div v-if="checkResult !== null" class="check-result" :class="checkResult.passed ? 'check-pass' : 'check-fail'">
        <div class="result-header">
          <el-icon :size="20"><component :is="checkResult.passed ? 'SuccessFilled' : 'WarningFilled'" /></el-icon>
          <span>{{ checkResult.passed ? '检查通过' : '检查未通过' }}</span>
        </div>
        <div class="result-message">{{ checkResult.message || (checkResult.passed ? '该交易符合风控规则' : '该交易触发了风控限制') }}</div>
        <div v-if="checkResult.details" class="result-details">
          <div v-for="(detail, idx) in checkResult.details" :key="idx" class="detail-item">
            <span class="detail-label">{{ detail.rule }}</span>
            <span class="detail-status" :class="detail.passed ? 'text-success' : 'text-danger'">{{ detail.passed ? '通过' : '未通过' }}</span>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getRiskSettings, saveRiskSettings, checkRisk } from '@/api/risk'
import { formatMoney } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const checking = ref(false)
const checkResult = ref<any>(null)

const riskSettings = reactive({
  maxAmountPerOrder: 100000,
  maxPositionPercent: 50,
  dailyLossLimit: 50000,
  maxDrawdownThreshold: 20,
  circuitBreakerEnabled: false,
  circuitBreakerLossAmount: 100000
})

const riskStatus = ref<any>(null)

const checkForm = reactive({
  stockCode: '',
  price: 0,
  quantity: 0
})

const dailyLossStatus = computed(() => {
  if (!riskStatus.value?.dailyLoss || !riskSettings.dailyLossLimit) return 'status-normal'
  return riskStatus.value.dailyLoss >= riskSettings.dailyLossLimit ? 'status-danger' : 'status-normal'
})

const drawdownStatus = computed(() => {
  if (!riskStatus.value?.currentDrawdown || !riskSettings.maxDrawdownThreshold) return 'status-normal'
  return (riskStatus.value.currentDrawdown * 100) >= riskSettings.maxDrawdownThreshold ? 'status-danger' : 'status-normal'
})

const positionRatioStatus = computed(() => {
  if (!riskStatus.value?.positionRatio || !riskSettings.maxPositionPercent) return 'status-normal'
  return (riskStatus.value.positionRatio * 100) >= riskSettings.maxPositionPercent ? 'status-warning' : 'status-normal'
})

const circuitBreakerStatus = computed(() => {
  return riskSettings.circuitBreakerEnabled ? 'status-normal' : 'status-muted'
})

async function loadSettings() {
  loading.value = true
  try {
    const res = await getRiskSettings()
    const data = res.data
    if (data.code === 200 && data.data) {
      const settings = data.data
      riskSettings.maxAmountPerOrder = settings.maxAmountPerOrder ?? riskSettings.maxAmountPerOrder
      riskSettings.maxPositionPercent = settings.maxPositionPercent ?? riskSettings.maxPositionPercent
      riskSettings.dailyLossLimit = settings.dailyLossLimit ?? riskSettings.dailyLossLimit
      riskSettings.maxDrawdownThreshold = settings.maxDrawdownThreshold ?? riskSettings.maxDrawdownThreshold
      riskSettings.circuitBreakerEnabled = settings.circuitBreakerEnabled ?? riskSettings.circuitBreakerEnabled
      riskSettings.circuitBreakerLossAmount = settings.circuitBreakerLossAmount ?? riskSettings.circuitBreakerLossAmount
      riskStatus.value = {
        dailyLoss: settings.dailyLoss,
        currentDrawdown: settings.currentDrawdown,
        positionRatio: settings.positionRatio
      }
    }
  } catch (error: unknown) {
    const errMsg = error instanceof Error ? error.message : '加载风控设置失败'
    ElMessage.error(errMsg)
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      maxAmountPerOrder: riskSettings.maxAmountPerOrder,
      maxPositionPercent: riskSettings.maxPositionPercent,
      dailyLossLimit: riskSettings.dailyLossLimit,
      maxDrawdownThreshold: riskSettings.maxDrawdownThreshold,
      circuitBreakerEnabled: riskSettings.circuitBreakerEnabled,
      circuitBreakerLossAmount: riskSettings.circuitBreakerLossAmount
    }
    const res = await saveRiskSettings(payload)
    if (res.data.code === 200) {
      ElMessage.success('风控设置保存成功')
    } else {
      ElMessage.error(res.data.message || '保存失败')
    }
  } catch (error: unknown) {
    const errMsg = error instanceof Error ? error.message : '保存失败'
    ElMessage.error(errMsg)
  } finally {
    saving.value = false
  }
}

async function handleCheck() {
  if (!checkForm.stockCode || !checkForm.price || !checkForm.quantity) {
    ElMessage.warning('请填写完整的交易信息')
    return
  }
  checking.value = true
  checkResult.value = null
  try {
    const res = await checkRisk({
      stockCode: checkForm.stockCode,
      price: checkForm.price,
      quantity: checkForm.quantity
    })
    const data = res.data
    checkResult.value = data.data || { passed: data.code === 200, message: data.message }
  } catch (error: unknown) {
    const errMsg = error instanceof Error ? error.message : '检查失败'
    ElMessage.error(errMsg)
    checkResult.value = { passed: false, message: errMsg }
  } finally {
    checking.value = false
  }
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped lang="scss">
.risk-page {
  max-width: 1400px;
}

.status-row {
  margin-bottom: 16px;
}

.status-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: transform 0.3s;
  border-left: 4px solid #909399;

  &:hover {
    transform: translateY(-2px);
  }

  &.status-normal {
    border-left-color: #67c23a;
  }

  &.status-warning {
    border-left-color: #e6a23c;
  }

  &.status-danger {
    border-left-color: #f56c6c;
  }

  &.status-muted {
    border-left-color: #909399;
  }

  .status-label {
    font-size: 13px;
    color: #909399;
    margin-bottom: 6px;
  }

  .status-value {
    font-size: 22px;
    font-weight: 600;
    color: #303133;
  }
}

.settings-card, .check-card {
  border-radius: 8px;

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid #f0f2f5;
    font-weight: 500;
    font-size: 15px;
    color: #303133;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.slider-group {
  display: flex;
  align-items: center;
  gap: 16px;
}

.slider-value {
  font-weight: 500;
  color: #303133;
  min-width: 48px;
}

.check-result {
  margin-top: 16px;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid;

  &.check-pass {
    background: #f0f9eb;
    border-color: #e1f3d8;
    color: #67c23a;
  }

  &.check-fail {
    background: #fef0f0;
    border-color: #fde2e2;
    color: #f56c6c;
  }

  .result-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 8px;
  }

  .result-message {
    font-size: 14px;
    color: #606266;
  }

  .result-details {
    margin-top: 12px;
  }

  .detail-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 0;
    font-size: 13px;
    border-bottom: 1px solid rgba(0,0,0,0.05);

    &:last-child {
      border-bottom: none;
    }

    .detail-label {
      color: #606266;
    }

    .detail-status {
      font-weight: 500;
    }
  }
}

.text-success {
  color: #67c23a !important;
}

.text-danger {
  color: #f56c6c !important;
}
</style>
