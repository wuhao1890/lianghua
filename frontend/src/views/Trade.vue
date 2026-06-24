<template>
  <div class="trade-page">
    <el-row :gutter="16">
      <!-- 左侧：交易面板 -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="hover" class="trade-card">
          <template #header>
            <div class="card-header">
              <h3>股票交易</h3>
              <el-tag type="info" effect="plain">模拟交易</el-tag>
            </div>
          </template>

          <div class="mode-section">
            <el-alert
              title="当前仅保留模拟交易，不会动用证券账户现金。"
              type="info"
              :closable="false"
              show-icon
            />
          </div>

          <!-- 搜索股票 -->
          <div class="search-section">
            <el-input
              v-model="searchKeyword"
              placeholder="输入股票代码或名称搜索"
              prefix-icon="Search"
              clearable
              size="large"
              @input="handleSearch"
            />
            <div v-if="stockStore.searchResults.length > 0" class="search-results">
              <div
                v-for="item in stockStore.searchResults"
                :key="item.code"
                class="search-item"
                @click="selectStock(item)"
              >
                <span class="name">{{ item.name }}</span>
                <span class="code">{{ item.code }}</span>
                <span class="price" :style="{ color: getColor(item.changePercent) }">
                  {{ formatPrice(item.currentPrice) }}
                </span>
              </div>
            </div>
          </div>

          <!-- 买入/卖出切换 -->
          <el-tabs v-model="direction" class="trade-tabs">
            <el-tab-pane name="BUY">
              <template #label>
                <span class="tab-label buy">买入</span>
              </template>
            </el-tab-pane>
            <el-tab-pane name="SELL">
              <template #label>
                <span class="tab-label sell">卖出</span>
              </template>
            </el-tab-pane>
          </el-tabs>

          <!-- 已选股票信息 -->
          <div v-if="selectedStock" class="selected-info">
            <div class="stock-name">{{ selectedStock.name }} <span class="code">{{ selectedStock.code }}</span></div>
            <div class="stock-price" :style="{ color: getColor(selectedStock.changePercent) }">
              {{ formatPrice(selectedStock.currentPrice) }}
              <span class="change">{{ formatPercent(selectedStock.changePercent) }}</span>
            </div>
          </div>

          <!-- 交易面板 -->
          <TradePanel
            v-if="selectedStock"
            :stock="selectedStock"
            :direction="direction"
            :available-cash="accountOverview?.availableCash || 0"
            :available-quantity="availableQuantity"
            :submitting="panelSubmitting"
            @submit="handleSubmit"
          />
          <el-empty v-else description="请先搜索并选择股票" />
        </el-card>
      </el-col>

      <!-- 右侧：账户信息 -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="hover" class="account-card">
          <template #header>
            <h3>账户信息</h3>
          </template>
          <div class="account-info">
            <div class="info-row">
              <span class="label">总资产</span>
              <span class="value">{{ formatMoney(accountOverview?.totalAssets) }}</span>
            </div>
            <div class="info-row">
              <span class="label">可用资金</span>
              <span class="value">{{ formatMoney(accountOverview?.availableCash) }}</span>
            </div>
            <div class="info-row">
              <span class="label">持仓市值</span>
              <span class="value">{{ formatMoney(accountOverview?.marketValue) }}</span>
            </div>
            <el-divider />
            <div class="info-row">
              <span class="label">总盈亏</span>
              <span class="value" :class="getColorClass(accountOverview?.totalProfit)">
                {{ formatMoney(accountOverview?.totalProfit) }}
              </span>
            </div>
            <div class="info-row">
              <span class="label">收益率</span>
              <span class="value" :class="getColorClass(accountOverview?.totalProfitPercent)">
                {{ formatPercent(accountOverview?.totalProfitPercent) }}
              </span>
            </div>
          </div>
        </el-card>

        <!-- 当前持仓 -->
        <el-card shadow="hover" class="position-card">
          <template #header>
            <div class="card-header">
              <h3>当前持仓</h3>
              <el-button text type="primary" @click="$router.push('/position')">查看全部</el-button>
            </div>
          </template>
          <el-table :data="tradeStore.positions" stripe size="small" max-height="400">
            <el-table-column prop="stockName" label="股票" width="80" />
            <el-table-column prop="quantity" label="持仓" width="60" align="right" />
            <el-table-column prop="profit" label="盈亏" align="right">
              <template #default="{ row }">
                <span :class="getColorClass(row.profit)">
                  {{ formatMoney(row.profit) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="profitPercent" label="盈亏%" width="90" align="right">
              <template #default="{ row }">
                <span :class="getColorClass(row.profitPercent)">
                  {{ formatPercent(row.profitPercent) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useStockStore } from '@/store/stock'
import { useTradeStore } from '@/store/trade'
import { formatPrice, formatPercent, formatMoney, getColor, getColorClass } from '@/utils/format'
import TradePanel from '@/components/TradePanel.vue'
import type { StockInfo } from '@/types'

const route = useRoute()
const stockStore = useStockStore()
const tradeStore = useTradeStore()

const searchKeyword = ref('')
const direction = ref<'BUY' | 'SELL'>((route.query.direction as 'BUY' | 'SELL') || 'BUY')
const selectedStock = ref<StockInfo | null>(null)

const accountOverview = computed(() => tradeStore.accountOverview)

const availableQuantity = computed(() => {
  if (direction.value !== 'SELL' || !selectedStock.value) return 0
  const pos = tradeStore.positions.find(p => p.stockCode === selectedStock.value!.code)
  return pos?.availableQuantity || 0
})

const panelSubmitting = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | null = null

function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (searchKeyword.value.trim()) {
      stockStore.searchStocks(searchKeyword.value)
    } else {
      stockStore.searchResults = []
    }
  }, 300)
}

function selectStock(stock: StockInfo) {
  selectedStock.value = stock
  stockStore.searchResults = []
  searchKeyword.value = stock.name
}

async function handleSubmit(data: { price: number; quantity: number }) {
  if (!selectedStock.value) return
  panelSubmitting.value = true
  try {
    if (direction.value === 'BUY') {
      await tradeStore.buy({
        stockCode: selectedStock.value.code,
        stockName: selectedStock.value.name,
        market: selectedStock.value.market === 'A' ? 'A_STOCK' : 'NASDAQ',
        direction: 'BUY',
        orderType: 'LIMIT',
        price: data.price,
        quantity: data.quantity
      })
      ElMessage.success('买入委托已提交')
    } else {
      await tradeStore.sell({
        stockCode: selectedStock.value.code,
        stockName: selectedStock.value.name,
        market: selectedStock.value.market === 'A' ? 'A_STOCK' : 'NASDAQ',
        direction: 'SELL',
        orderType: 'LIMIT',
        price: data.price,
        quantity: data.quantity
      })
      ElMessage.success('卖出委托已提交')
    }
    // 刷新数据
    tradeStore.getAccountOverview()
    tradeStore.getPositions()
  } catch (error: unknown) {
    const errMsg = error instanceof Error ? error.message : '交易失败'
    ElMessage.error(errMsg)
  } finally {
    panelSubmitting.value = false
  }
}

onMounted(async () => {
  try {
    await Promise.all([
      tradeStore.getAccountOverview(),
      tradeStore.getPositions()
    ])

    // 如果URL带了code参数，自动搜索
    if (route.query.code) {
      await stockStore.searchStocks(route.query.code as string)
      if (stockStore.searchResults.length > 0) {
        selectStock(stockStore.searchResults[0])
      }
    }
  } catch (e) {
    console.error('加载交易数据失败:', e)
  }
})
</script>

<style scoped lang="scss">
.trade-page {
  max-width: 1400px;
}

.trade-card, .account-card, .position-card {
  border-radius: 8px;
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h3 {
    font-size: 16px;
    color: #303133;
    margin: 0;
  }
}

.search-section {
  position: relative;
  margin-bottom: 20px;
}

.mode-section {
  display: grid;
  gap: 12px;
  margin-bottom: 18px;
}

.search-results {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 100;
  max-height: 200px;
  overflow-y: auto;
}

.search-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #f5f7fa;
  }

  .name {
    font-weight: 500;
    color: #303133;
  }

  .code {
    color: #909399;
    font-size: 13px;
  }

  .price {
    font-weight: 500;
  }
}

.trade-tabs {
  margin-bottom: 20px;

  .tab-label {
    font-size: 15px;
    font-weight: 600;
    padding: 4px 20px;

    &.buy {
      color: #f56c6c;
    }

    &.sell {
      color: #67c23a;
    }
  }
}

.selected-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 20px;

  .stock-name {
    font-size: 16px;
    font-weight: 500;
    color: #303133;

    .code {
      font-size: 13px;
      color: #909399;
      margin-left: 8px;
    }
  }

  .stock-price {
    font-size: 24px;
    font-weight: 700;

    .change {
      font-size: 14px;
      margin-left: 8px;
    }
  }
}

.account-info {
  .info-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 0;

    .label {
      color: #909399;
      font-size: 14px;
    }

    .value {
      font-size: 15px;
      font-weight: 500;
      color: #303133;
    }
  }
}

.text-danger {
  color: #f56c6c !important;
}

.text-success {
  color: #67c23a !important;
}
</style>
