<template>
  <div class="position-table">
    <el-table
      :data="positions"
      stripe
      style="width: 100%"
      :default-sort="{ prop: 'marketValue', order: 'descending' }"
      size="small"
    >
      <el-table-column prop="stockCode" label="代码" width="90">
        <template #default="{ row }">
          <el-link type="primary" @click="$emit('stockClick', row.stockCode)">
            {{ row.stockCode }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="stockName" label="名称" width="100" />
      <el-table-column prop="quantity" label="持仓" width="70" align="right" />
      <el-table-column prop="costPrice" label="成本价" width="90" align="right">
        <template #default="{ row }">
          {{ formatPrice(row.costPrice) }}
        </template>
      </el-table-column>
      <el-table-column prop="currentPrice" label="现价" width="90" align="right">
        <template #default="{ row }">
          <span :style="{ color: getColor(row.todayProfitPercent) }">
            {{ formatPrice(row.currentPrice) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="marketValue" label="市值" width="110" align="right">
        <template #default="{ row }">
          {{ formatMoney(row.marketValue) }}
        </template>
      </el-table-column>
      <el-table-column prop="profit" label="盈亏" width="110" align="right">
        <template #default="{ row }">
          <span :class="getColorClass(row.profit)">
            {{ row.profit >= 0 ? '+' : '' }}{{ formatMoney(row.profit) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="profitPercent" label="盈亏%" width="100" align="right">
        <template #default="{ row }">
          <span :class="getColorClass(row.profitPercent)">
            {{ formatPercent(row.profitPercent) }}
          </span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { formatMoney, formatPrice, formatPercent, getColor, getColorClass } from '@/utils/format'
import type { Position } from '@/types'

defineProps<{
  positions: Position[]
}>()

defineEmits<{
  stockClick: [code: string]
}>()
</script>

<style scoped lang="scss">
.position-table {
  :deep(.el-table) {
    font-size: 13px;
  }
}

.text-danger {
  color: #f56c6c !important;
}

.text-success {
  color: #67c23a !important;
}
</style>
