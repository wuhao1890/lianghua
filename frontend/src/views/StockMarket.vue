<template>
  <div class="stock-market">
    <el-card shadow="hover" class="market-card">
      <template #header>
        <div class="card-header">
          <h3>行情中心</h3>
          <div class="header-actions">
            <el-popover
              v-model:visible="showSearchPopover"
              placement="bottom-start"
              :width="360"
              trigger="manual"
              popper-class="search-popover"
            >
              <template #reference>
                <el-input
                  v-model="searchKeyword"
                  placeholder="输入代码或名称搜索"
                  prefix-icon="Search"
                  clearable
                  style="width: 240px"
                  @input="handleSearch"
                  @focus="onSearchFocus"
                  @blur="onSearchBlur"
                />
              </template>
              <div class="search-result-list" v-if="stockStore.searchResults.length > 0">
                <div
                  v-for="item in stockStore.searchResults"
                  :key="item.code"
                  class="search-result-item"
                  @mousedown.prevent="goToDetail(item)"
                >
                  <span class="sr-code">{{ item.code }}</span>
                  <span class="sr-name">{{ item.name }}</span>
                  <span class="sr-price" :style="{ color: getColor(item.changePercent) }">
                    {{ formatPrice(item.currentPrice) }}
                  </span>
                  <span class="sr-change" :style="{ color: getColor(item.changePercent) }">
                    {{ item.changePercent >= 0 ? '+' : '' }}{{ item.changePercent?.toFixed(2) }}%
                  </span>
                </div>
              </div>
              <div v-else-if="searchKeyword.trim()" class="search-empty">
                <span>未找到匹配的股票</span>
              </div>
            </el-popover>
          </div>
        </div>
      </template>

      <!-- 市场切换 -->
      <el-tabs v-model="currentMarket" @tab-click="handleMarketChange">
        <el-tab-pane label="A股" name="A" />
        <el-tab-pane label="美股" name="US" />
      </el-tabs>

      <!-- 涨跌排行 -->
      <div class="rank-bar">
        <el-radio-group v-model="sortField" size="small" @change="loadData">
          <el-radio-button value="changePercent">涨跌幅</el-radio-button>
          <el-radio-button value="volume">成交量</el-radio-button>
          <el-radio-button value="turnoverRate">换手率</el-radio-button>
          <el-radio-button value="marketCap">总市值</el-radio-button>
        </el-radio-group>
        <el-button-group size="small" style="margin-left: 12px;">
          <el-button :type="sortOrder === 'desc' ? 'primary' : ''" @click="sortOrder = 'desc'; loadData()">降序</el-button>
          <el-button :type="sortOrder === 'asc' ? 'primary' : ''" @click="sortOrder = 'asc'; loadData()">升序</el-button>
        </el-button-group>
      </div>

      <!-- 股票列表 -->
      <el-table
        :data="stockStore.stockList"
        v-loading="stockStore.loading"
        stripe
        style="width: 100%"
        @row-click="goToDetail"
        highlight-current-row
        class="stock-table"
      >
        <el-table-column prop="code" label="代码" width="100" />
        <el-table-column prop="name" label="名称" width="120">
          <template #default="{ row }">
            <span class="stock-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="currentPrice" label="现价" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: getColor(row.changePercent) }">
              {{ formatPrice(row.currentPrice) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changeAmount" label="涨跌额" width="90" align="right">
          <template #default="{ row }">
            <span :style="{ color: getColor(row.changePercent) }">
              {{ getChangeAmount(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅" width="100" align="right">
          <template #default="{ row }">
            <span class="change-tag" :class="row.changePercent >= 0 ? 'up' : 'down'">
              {{ formatPercent(row.changePercent) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="成交量" width="110" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.volume) }}
          </template>
        </el-table-column>
        <el-table-column prop="turnover" label="成交额" width="110" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.turnover) }}
          </template>
        </el-table-column>
        <el-table-column prop="turnoverRate" label="换手率" width="80" align="right">
          <template #default="{ row }">
            {{ row.turnoverRate ? row.turnoverRate.toFixed(2) + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="pe" label="市盈率" width="80" align="right">
          <template #default="{ row }">
            {{ row.pe ? row.pe.toFixed(2) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="marketCap" label="总市值" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.marketCap) }}
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="stockStore.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useStockStore } from '@/store/stock'
import { formatPrice, formatPercent, formatNumber, getColor } from '@/utils/format'
import type { StockInfo } from '@/types'

const router = useRouter()
const stockStore = useStockStore()

const currentMarket = ref('A')
const searchKeyword = ref('')
const showSearchPopover = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const sortField = ref('changePercent')
const sortOrder = ref('desc')

let searchTimer: ReturnType<typeof setTimeout> | null = null

function onSearchFocus() {
  if (searchKeyword.value.trim()) {
    showSearchPopover.value = true
  }
}

function onSearchBlur() {
  // 延迟关闭，让 mousedown 事件先触发
  setTimeout(() => { showSearchPopover.value = false }, 200)
}

function getChangeAmount(row: StockInfo) {
  const prevClose = (row as any).prevClose || row.closePrice || 0
  const change = row.currentPrice - prevClose
  if (change > 0) return '+' + change.toFixed(2)
  if (change < 0) return change.toFixed(2)
  return '0.00'
}

function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (!searchKeyword.value.trim()) {
      showSearchPopover.value = false
      stockStore.searchResults = []
      return
    }
    showSearchPopover.value = true
    stockStore.searchStocks(searchKeyword.value)
  }, 300)
}

function handleMarketChange() {
  currentPage.value = 1
  loadData()
}

function loadData() {
  const marketMap: Record<string, string> = {
    'A': 'A_STOCK',
    'US': 'NASDAQ'
  }
  stockStore.getStockList({
    market: marketMap[currentMarket.value] || 'A_STOCK',
    page: currentPage.value,
    pageSize: pageSize.value,
    keyword: searchKeyword.value || undefined
  })
}

function goToDetail(row: StockInfo) {
  router.push(`/stock/${row.code}`)
}

onMounted(() => {
  loadData()
})

watch(currentMarket, () => {
  currentPage.value = 1
  loadData()
})
</script>

<style scoped lang="scss">
.stock-market {
  max-width: 1400px;
}

.market-card {
  border-radius: 8px;
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

.rank-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.stock-table {
  cursor: pointer;

  :deep(.el-table__row) {
    &:hover {
      .stock-name {
        color: #409eff;
      }
    }
  }
}

.stock-name {
  font-weight: 500;
  color: #303133;
  transition: color 0.3s;
}

.change-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;

  &.up {
    background: rgba(245, 108, 108, 0.1);
    color: #f56c6c;
  }

  &.down {
    background: rgba(103, 194, 58, 0.1);
    color: #67c23a;
  }
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.search-result-list {
  max-height: 320px;
  overflow-y: auto;
}

.search-result-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s;

  &:hover {
    background: #f5f7fa;
  }

  .sr-code {
    font-size: 13px;
    color: #909399;
    width: 70px;
    flex-shrink: 0;
  }

  .sr-name {
    font-size: 14px;
    color: #303133;
    font-weight: 500;
    flex: 1;
    margin-right: 12px;
  }

  .sr-price {
    font-size: 14px;
    font-weight: 500;
    width: 80px;
    text-align: right;
    flex-shrink: 0;
  }

  .sr-change {
    font-size: 13px;
    width: 70px;
    text-align: right;
    flex-shrink: 0;
  }
}

.search-empty {
  padding: 16px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
</style>
