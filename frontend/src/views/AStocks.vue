<template>
  <div class="a-stocks-page">
    <el-card shadow="hover" class="market-card">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <el-icon :size="20" color="#33b86a"><TrendCharts /></el-icon>
            <span class="page-title">A股行情</span>
          </div>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="输入代码或名称搜索"
              prefix-icon="Search"
              clearable
              style="width: 220px; margin-right: 12px"
              @input="handleSearch"
              @clear="handleSearch"
            />
            <el-button type="primary" @click="loadData" :loading="loading">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <div class="board-tabs">
        <el-radio-group v-model="selectedBoard" size="small" @change="onBoardChange">
          <el-radio-button value="">全部A股</el-radio-button>
          <el-radio-button value="main">沪深主板</el-radio-button>
          <el-radio-button value="chinext">创业板</el-radio-button>
          <el-radio-button value="star">科创板</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 排序选项 -->
      <div class="rank-bar">
        <span class="rank-label">排序：</span>
        <el-radio-group v-model="sortField" size="small" @change="loadData">
          <el-radio-button value="changePercent">涨跌幅</el-radio-button>
          <el-radio-button value="volume">成交量</el-radio-button>
          <el-radio-button value="turnover">成交额</el-radio-button>
          <el-radio-button value="marketCap">总市值</el-radio-button>
        </el-radio-group>
        <el-button-group size="small" style="margin-left: 12px;">
          <el-button :type="sortOrder === 'desc' ? 'primary' : ''" @click="sortOrder = 'desc'; loadData()">
            <el-icon><SortDown /></el-icon> 降序
          </el-button>
          <el-button :type="sortOrder === 'asc' ? 'primary' : ''" @click="sortOrder = 'asc'; loadData()">
            <el-icon><SortUp /></el-icon> 升序
          </el-button>
        </el-button-group>
      </div>

      <!-- 股票列表 -->
      <el-table
        :data="stockList"
        v-loading="loading"
        stripe
        style="width: 100%"
        @row-click="goToDetail"
        highlight-current-row
        class="stock-table"
        :default-sort="{ prop: 'changePercent', order: 'descending' }"
      >
        <el-table-column prop="code" label="代码" width="110" />
        <el-table-column label="板块" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ boardName(row.code) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="130">
          <template #default="{ row }">
            <span class="stock-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="currentPrice" label="现价" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: getColor(row.changePercent), fontWeight: 600 }">
              {{ formatPrice(row.currentPrice) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅" width="110" align="right" sortable="custom">
          <template #default="{ row }">
            <span class="change-tag" :class="safeScore(row.changePercent) >= 0 ? 'up' : 'down'">
              {{ formatPercent(row.changePercent) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="成交量" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.volume) }}
          </template>
        </el-table-column>
        <el-table-column prop="turnover" label="成交额" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.turnover) }}
          </template>
        </el-table-column>
        <el-table-column prop="marketCap" label="总市值" width="130" align="right">
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
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, TrendCharts, SortDown, SortUp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getSinaAStocks } from '@/api/stock'
import { formatPrice, formatPercent, formatNumber, getColor } from '@/utils/format'
import type { StockInfo } from '@/types'

const router = useRouter()

const loading = ref(false)
const stockList = ref<StockInfo[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const sortField = ref('changePercent')
const sortOrder = ref('desc')
const selectedBoard = ref('')

let searchTimer: ReturnType<typeof setTimeout> | null = null

function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    loadData()
  }, 300)
}

function boardName(code: string) {
  if (String(code).startsWith('300')) return '创业板'
  if (String(code).startsWith('688')) return '科创板'
  return '主板'
}

function onBoardChange() {
  currentPage.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await getSinaAStocks(currentPage.value, pageSize.value, { board: selectedBoard.value })
    const data = res.data
    if (data) {
      let list = (data.data || []).map((s: any) => ({
        code: s.code,
        name: s.name,
        market: 'A',
        currentPrice: s.current,
        openPrice: s.open,
        closePrice: s.prevClose,
        highPrice: s.high,
        lowPrice: s.low,
        changePercent: s.changePercent,
        changeAmount: s.change,
        volume: s.volume,
        turnover: s.amount,
        turnoverRate: 0,
        pe: 0,
        pb: 0,
        marketCap: 0,
        totalShares: 0,
        circulateShares: 0
      })) as StockInfo[]
      if (searchKeyword.value.trim()) {
        const kw = searchKeyword.value.trim().toLowerCase()
        list = list.filter(s => s.code.toLowerCase().includes(kw) || s.name.toLowerCase().includes(kw))
      }
      // 客户端排序
      list.sort((a: any, b: any) => {
        const va = safeScore(a[sortField.value])
        const vb = safeScore(b[sortField.value])
        return sortOrder.value === 'desc' ? vb - va : va - vb
      })
      stockList.value = list
      total.value = data.total || list.length
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载A股数据失败')
    stockList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goToDetail(row: StockInfo) {
  router.push(`/stock/${row.code}`)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.a-stocks-page {
  max-width: 1400px;
}

.market-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .header-title {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .page-title {
    font-size: 17px;
    font-weight: 600;
    color: #303133;
  }

  .header-actions {
    display: flex;
    align-items: center;
  }
}

.rank-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;

  .rank-label {
    font-size: 13px;
    color: #909399;
    margin-right: 8px;
    white-space: nowrap;
  }
}

.board-tabs {
  margin-bottom: 12px;
}

.stock-table {
  cursor: pointer;

  :deep(.el-table__row) {
    &:hover {
      .stock-name {
        color: #33b86a;
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
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  min-width: 70px;
  text-align: center;

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
</style>
