<template>
  <div class="position-page">
    <el-card shadow="hover" class="position-card">
      <template #header>
        <div class="card-header">
          <h3>持仓管理</h3>
          <div class="summary">
            <span>持仓市值：<b>{{ formatMoney(totalMarketValue) }}</b></span>
            <span :class="getColorClass(totalProfit)">
              总盈亏：<b>{{ formatMoney(totalProfit) }}</b>
            </span>
          </div>
        </div>
      </template>

      <el-table
        :data="sortedPositions"
        v-loading="tradeStore.loading"
        stripe
        style="width: 100%"
        :default-sort="{ prop: 'marketValue', order: 'descending' }"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="stockCode" label="代码" width="100">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/stock/${row.stockCode}`)">
              {{ row.stockCode }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="stockName" label="名称" width="120" />
        <el-table-column prop="quantity" label="持仓数量" width="100" align="right" sortable="custom" />
        <el-table-column prop="availableQuantity" label="可卖数量" width="100" align="right" />
        <el-table-column prop="costPrice" label="成本价" width="100" align="right" sortable="custom">
          <template #default="{ row }">
            {{ formatPrice(row.costPrice) }}
          </template>
        </el-table-column>
        <el-table-column prop="currentPrice" label="现价" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: getColor(row.todayProfitPercent) }">
              {{ formatPrice(row.currentPrice) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="marketValue" label="持仓市值" width="120" align="right" sortable="custom">
          <template #default="{ row }">
            {{ formatMoney(row.marketValue) }}
          </template>
        </el-table-column>
        <el-table-column prop="profit" label="盈亏金额" width="120" align="right" sortable="custom">
          <template #default="{ row }">
            <span :class="getColorClass(row.profit)">
              {{ row.profit >= 0 ? '+' : '' }}{{ formatMoney(row.profit) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="profitPercent" label="盈亏比例" width="110" align="right" sortable="custom">
          <template #default="{ row }">
            <span :class="getColorClass(row.profitPercent)">
              {{ formatPercent(row.profitPercent) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="todayProfit" label="今日盈亏" width="120" align="right">
          <template #default="{ row }">
            <span :class="getColorClass(row.todayProfit)">
              {{ row.todayProfit >= 0 ? '+' : '' }}{{ formatMoney(row.todayProfit) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="handleSell(row)">
              卖出
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!tradeStore.loading && tradeStore.positions.length === 0" description="暂无持仓" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTradeStore } from '@/store/trade'
import { formatMoney, formatPrice, formatPercent, getColor, getColorClass } from '@/utils/format'
import type { Position } from '@/types'

const router = useRouter()
const tradeStore = useTradeStore()
const sortProp = ref<string>('')
const sortOrder = ref<string>('')

const sortedPositions = computed(() => {
  const positions = [...tradeStore.positions]
  if (!sortProp.value) return positions
  const prop = sortProp.value as keyof Position
  const asc = sortOrder.value === 'ascending'
  positions.sort((a, b) => {
    const va = a[prop] as number
    const vb = b[prop] as number
    return asc ? va - vb : vb - va
  })
  return positions
})

const totalMarketValue = computed(() => {
  return tradeStore.positions.reduce((sum, p) => sum + p.marketValue, 0)
})

const totalProfit = computed(() => {
  return tradeStore.positions.reduce((sum, p) => sum + p.profit, 0)
})

function handleSortChange({ prop, order }: { prop: string; order: string }) {
  sortProp.value = prop
  sortOrder.value = order
}

function handleSell(position: Position) {
  router.push({ path: '/trade', query: { code: position.stockCode, direction: 'SELL' } })
}

onMounted(() => {
  tradeStore.getPositions()
})
</script>

<style scoped lang="scss">
.position-page {
  max-width: 1400px;
}

.position-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;

  h3 {
    font-size: 16px;
    color: #303133;
    margin: 0;
  }
}

.summary {
  display: flex;
  gap: 24px;
  font-size: 14px;
  color: #606266;

  b {
    font-size: 15px;
    color: #303133;
  }
}

.text-danger {
  color: #f56c6c !important;
}

.text-success {
  color: #67c23a !important;
}
</style>
