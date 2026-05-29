<template>
  <div class="trade-panel">
    <!-- 价格输入 -->
    <div class="form-section">
      <div class="form-label">委托价格</div>
      <div class="price-input-group">
        <el-radio-group v-model="orderType" size="small" class="type-radio">
          <el-radio-button label="LIMIT">限价</el-radio-button>
          <el-radio-button label="MARKET">市价</el-radio-button>
        </el-radio-group>
        <el-input-number
          v-model="price"
          :disabled="orderType === 'MARKET'"
          :precision="2"
          :step="0.01"
          :min="0"
          controls-position="right"
          size="large"
          class="price-input"
        />
      </div>
      <div class="price-hint" v-if="orderType === 'MARKET'">
        市价委托将以当前最新价格成交
      </div>
    </div>

    <!-- 数量输入 -->
    <div class="form-section">
      <div class="form-label">
        委托数量
        <span class="available" v-if="direction === 'BUY'">
          可用资金: {{ formatMoney(availableCash) }}
        </span>
        <span class="available" v-else>
          可卖数量: {{ availableQuantity }}
        </span>
      </div>
      <el-input-number
        v-model="quantity"
        :min="100"
        :step="100"
        controls-position="right"
        size="large"
        class="quantity-input"
      />
      <div class="quick-btns">
        <el-button size="small" @click="setQuantity(0.25)">1/4仓</el-button>
        <el-button size="small" @click="setQuantity(0.33)">1/3仓</el-button>
        <el-button size="small" @click="setQuantity(0.5)">1/2仓</el-button>
        <el-button size="small" @click="setQuantity(1)">全仓</el-button>
      </div>
    </div>

    <!-- 止损止盈 -->
    <div class="form-section sltp-section">
      <div class="form-label">风控设置（可选）</div>
      <div class="sltp-grid">
        <div class="sltp-item">
          <span class="sltp-label">止损价</span>
          <el-input-number v-model="stopLoss" :precision="2" :step="0.01" :min="0" controls-position="right" size="default" placeholder="不设置" class="sltp-input" />
        </div>
        <div class="sltp-item">
          <span class="sltp-label">止盈价</span>
          <el-input-number v-model="takeProfit" :precision="2" :step="0.01" :min="0" controls-position="right" size="default" placeholder="不设置" class="sltp-input" />
        </div>
      </div>
    </div>

    <!-- 预估信息 -->
    <div class="estimate-section">
      <div class="estimate-row">
        <span>委托价格</span>
        <span>{{ formatPrice(currentPrice) }}</span>
      </div>
      <div class="estimate-row">
        <span>委托数量</span>
        <span>{{ quantity }} 股</span>
      </div>
      <div class="estimate-row">
        <span>预估金额</span>
        <span class="amount">{{ formatMoney(estimatedAmount) }}</span>
      </div>
      <div class="estimate-row">
        <span>预估手续费</span>
        <span>{{ formatMoney(estimatedFee) }}</span>
      </div>
      <el-divider />
      <div class="estimate-row total">
        <span>预估总额</span>
        <span class="amount">{{ formatMoney(estimatedTotal) }}</span>
      </div>
    </div>

    <!-- 提交按钮 -->
    <el-button
      :type="direction === 'BUY' ? 'danger' : 'success'"
      size="large"
      class="submit-btn"
      :disabled="!canSubmit"
      :loading="submitting"
      @click="handleSubmit"
    >
      {{ direction === 'BUY' ? '确认买入' : '确认卖出' }}
      {{ stock?.name }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatMoney, formatPrice } from '@/utils/format'
import type { StockInfo } from '@/types'

const props = defineProps<{
  stock: StockInfo
  direction: 'BUY' | 'SELL'
  availableCash: number
  availableQuantity: number
  submitting?: boolean
}>()

const emit = defineEmits<{
  submit: [data: { price: number; quantity: number; stopLoss?: number; takeProfit?: number }]
}>()

const orderType = ref<'LIMIT' | 'MARKET'>('LIMIT')
const price = ref(0)
const quantity = ref(100)
const stopLoss = ref(0)
const takeProfit = ref(0)

const currentPrice = computed(() => {
  if (orderType.value === 'MARKET') return props.stock.currentPrice
  return price.value || props.stock.currentPrice
})

const estimatedAmount = computed(() => {
  return currentPrice.value * quantity.value
})

const estimatedFee = computed(() => {
  return Math.max(5, estimatedAmount.value * 0.0003) // 最低5元，费率0.03%
})

const estimatedTotal = computed(() => {
  if (props.direction === 'BUY') {
    return estimatedAmount.value + estimatedFee.value
  }
  return estimatedAmount.value - estimatedFee.value
})

const canSubmit = computed(() => {
  if (quantity.value <= 0) return false
  if (props.direction === 'BUY') {
    return estimatedTotal.value <= props.availableCash
  }
  return quantity.value <= props.availableQuantity
})

function setQuantity(ratio: number) {
  if (props.direction === 'BUY') {
    const maxAmount = props.availableCash / (currentPrice.value * 1.0003)
    quantity.value = Math.floor(maxAmount * ratio / 100) * 100
  } else {
    quantity.value = Math.floor(props.availableQuantity * ratio / 100) * 100
  }
  if (quantity.value < 100) quantity.value = 100
}

async function handleSubmit() {
  if (!canSubmit.value) {
    if (props.direction === 'BUY') {
      ElMessage.warning('可用资金不足')
    } else {
      ElMessage.warning('可卖数量不足')
    }
    return
  }

  try {
    await ElMessageBox.confirm(
      `${props.direction === 'BUY' ? '买入' : '卖出'}确认\n` +
      `股票: ${props.stock.name} (${props.stock.code})\n` +
      `价格: ${formatPrice(currentPrice.value)}\n` +
      `数量: ${quantity.value} 股\n` +
      `金额: ${formatMoney(estimatedAmount.value)}\n` +
      `手续费: ${formatMoney(estimatedFee.value)}`,
      '交易确认',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const payload: { price: number; quantity: number; stopLoss?: number; takeProfit?: number } = {
      price: currentPrice.value,
      quantity: quantity.value
    }
    if (stopLoss.value > 0) payload.stopLoss = stopLoss.value
    if (takeProfit.value > 0) payload.takeProfit = takeProfit.value
    emit('submit', payload)
  } catch (e) {
    // 用户取消
  }
}

// 当股票变化时重置价格
watch(() => props.stock, (newStock) => {
  if (newStock) {
    price.value = newStock.currentPrice
  }
}, { immediate: true })
</script>

<style scoped lang="scss">
.trade-panel {
  padding: 8px 0;
}

.form-section {
  margin-bottom: 20px;
}

.form-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;

  .available {
    font-size: 12px;
    color: #909399;
  }
}

.price-input-group {
  display: flex;
  gap: 12px;
  align-items: center;
}

.type-radio {
  flex-shrink: 0;
}

.price-input {
  flex: 1;
  width: 100%;
}

.price-hint {
  font-size: 12px;
  color: #e6a23c;
  margin-top: 4px;
}

.quantity-input {
  width: 100%;
  margin-bottom: 8px;
}

.quick-btns {
  display: flex;
  gap: 8px;
}

.quick-btns .el-button {
  flex: 1;
}

.estimate-section {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}

.estimate-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 14px;
  color: #606266;

  .amount {
    font-weight: 600;
    color: #303133;
  }

  &.total {
    font-size: 15px;
    font-weight: 500;

    .amount {
      font-size: 18px;
      color: #409eff;
    }
  }
}

.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
}

/* 止损止盈 */
.sltp-grid {
  display: flex;
  gap: 12px;
}

.sltp-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sltp-label {
  font-size: 12px;
  color: #909399;
}

.sltp-input {
  width: 100%;
}
</style>
