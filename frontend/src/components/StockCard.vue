<template>
  <div class="stock-card" @click="$emit('click')">
    <div class="card-header">
      <span class="stock-name">{{ stock.name }}</span>
      <span class="stock-code">{{ stock.code }}</span>
    </div>
    <div class="card-price" :style="{ color: getColor(stock.changePercent) }">
      {{ formatPrice(stock.currentPrice) }}
    </div>
    <div class="card-change" :class="stock.changePercent >= 0 ? 'up' : 'down'">
      {{ formatPercent(stock.changePercent) }}
    </div>
    <div class="card-footer">
      <span class="volume">成交量: {{ formatNumber(stock.volume) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatPrice, formatPercent, formatNumber, getColor } from '@/utils/format'
import type { StockInfo } from '@/types'

defineProps<{
  stock: StockInfo
}>()

defineEmits<{
  click: []
}>()
</script>

<style scoped lang="scss">
.stock-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.stock-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.stock-code {
  font-size: 12px;
  color: #909399;
}

.card-price {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 4px;
}

.card-change {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;

  &.up {
    color: #f56c6c;
  }

  &.down {
    color: #67c23a;
  }
}

.card-footer {
  .volume {
    font-size: 12px;
    color: #c0c4cc;
  }
}
</style>
