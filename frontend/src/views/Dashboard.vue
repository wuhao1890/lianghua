<template>
  <div class="dashboard">
    <!-- 账户概览卡片 -->
    <el-row :gutter="16" class="overview-row">
      <el-col :xs="12" :sm="8" :md="4">
        <div class="overview-card">
          <div class="card-label">总资产</div>
          <div class="card-value">{{ formatMoney(overview?.totalAssets) }}</div>
          <div class="card-sub" :class="getColorClass(overview?.todayProfit)">
            今日 {{ formatMoney(overview?.todayProfit) }}
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="overview-card">
          <div class="card-label">可用资金</div>
          <div class="card-value">{{ formatMoney(overview?.availableCash) }}</div>
          <div class="card-sub">可用于交易</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="overview-card">
          <div class="card-label">持仓市值</div>
          <div class="card-value">{{ formatMoney(overview?.marketValue) }}</div>
          <div class="card-sub">{{ overview?.positionCount || 0 }} 只股票</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="overview-card">
          <div class="card-label">总盈亏</div>
          <div class="card-value" :class="getColorClass(overview?.totalProfit)">
            {{ formatMoney(overview?.totalProfit) }}
          </div>
          <div class="card-sub" :class="getColorClass(overview?.totalProfitPercent)">
            {{ formatPercent(overview?.totalProfitPercent) }}
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="overview-card">
          <div class="card-label">今日盈亏</div>
          <div class="card-value" :class="getColorClass(overview?.todayProfit)">
            {{ formatMoney(overview?.todayProfit) }}
          </div>
          <div class="card-sub" :class="getColorClass(overview?.todayProfitPercent)">
            {{ formatPercent(overview?.todayProfitPercent) }}
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <div class="overview-card">
          <div class="card-label">收益率</div>
          <div class="card-value" :class="getColorClass(overview?.totalProfitPercent)">
            {{ formatPercent(overview?.totalProfitPercent) }}
          </div>
          <div class="card-sub">累计收益</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 资产分布饼图 -->
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>资产分布</span>
            </div>
          </template>
          <div ref="pieChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 最近交易记录 -->
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>最近交易</span>
              <el-button text type="primary" @click="$router.push('/history')">
                查看全部
              </el-button>
            </div>
          </template>
          <el-table :data="recentOrders" stripe size="small" max-height="320">
            <el-table-column prop="createdAt" label="时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt, 'MM-DD HH:mm') }}
              </template>
            </el-table-column>
            <el-table-column prop="stockName" label="股票" width="100" />
            <el-table-column prop="direction" label="方向" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.direction === 'BUY' ? 'danger' : 'success'" size="small">
                  {{ row.direction === 'BUY' ? '买入' : '卖出' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="price" label="价格" width="90" align="right">
              <template #default="{ row }">
                {{ formatPrice(row.price) }}
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" align="right" />
            <el-table-column prop="amount" label="金额" width="110" align="right">
              <template #default="{ row }">
                {{ formatMoney(row.amount) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="statusTagType(row.status)"
                  size="small"
                >
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;">
      <!-- 持仓盈亏排行 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>持仓盈亏排行</span>
              <el-button text type="primary" @click="$router.push('/position')">
                查看全部
              </el-button>
            </div>
          </template>
          <el-table :data="topPositions" stripe size="small" max-height="300">
            <el-table-column prop="stockName" label="股票" width="100" />
            <el-table-column prop="stockCode" label="代码" width="80" />
            <el-table-column prop="quantity" label="持仓" width="70" align="right" />
            <el-table-column prop="costPrice" label="成本" width="80" align="right">
              <template #default="{ row }">
                {{ formatPrice(row.costPrice) }}
              </template>
            </el-table-column>
            <el-table-column prop="currentPrice" label="现价" width="80" align="right">
              <template #default="{ row }">
                {{ formatPrice(row.currentPrice) }}
              </template>
            </el-table-column>
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

      <!-- 市场热点 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>
            <div class="card-header">
              <span>市场热点</span>
              <el-button text type="primary" @click="$router.push('/market')">
                查看全部
              </el-button>
            </div>
          </template>
          <el-table :data="hotStocks" stripe size="small" max-height="300">
            <el-table-column prop="name" label="股票" width="100" />
            <el-table-column prop="code" label="代码" width="80" />
            <el-table-column prop="currentPrice" label="现价" width="80" align="right">
              <template #default="{ row }">
                {{ formatPrice(row.currentPrice) }}
              </template>
            </el-table-column>
            <el-table-column prop="changePercent" label="涨跌幅" align="right">
              <template #default="{ row }">
                <span :class="getColorClass(row.changePercent)">
                  {{ formatPercent(row.changePercent) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="volume" label="成交量" align="right">
              <template #default="{ row }">
                {{ formatNumber(row.volume) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useTradeStore } from '@/store/trade'
import { useStockStore } from '@/store/stock'
import { formatMoney, formatPercent, formatNumber, formatPrice, formatDateTime, getColorClass } from '@/utils/format'
import type { TradeOrder, Position, StockInfo } from '@/types'

const tradeStore = useTradeStore()
const stockStore = useStockStore()
const pieChartRef = ref<HTMLElement>()
const recentOrders = ref<TradeOrder[]>([])
const topPositions = ref<Position[]>([])
const hotStocks = ref<StockInfo[]>([])

const overview = computed(() => tradeStore.accountOverview)

function statusTagType(status: string) {
  const map: Record<string, string> = {
    FILLED: 'success',
    PENDING: 'warning',
    CANCELLED: 'info',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

function statusText(status: string) {
  const map: Record<string, string> = {
    FILLED: '已成交',
    PENDING: '待成交',
    CANCELLED: '已撤销',
    REJECTED: '已拒绝'
  }
  return map[status] || status
}

function initPieChart() {
  if (!pieChartRef.value) return
  const chart = echarts.init(pieChartRef.value)
  const cash = overview.value?.availableCash || 0
  const marketVal = overview.value?.marketValue || 0

  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 10,
      textStyle: { color: '#606266' }
    },
    series: [
      {
        name: '资产分布',
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}\n{d}%',
          fontSize: 12
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold'
          }
        },
        data: [
          {
            value: cash,
            name: '可用资金',
            itemStyle: { color: '#409eff' }
          },
          {
            value: marketVal,
            name: '持仓市值',
            itemStyle: { color: '#e6a23c' }
          }
        ]
      }
    ]
  })

  window.addEventListener('resize', () => chart.resize())
}

onMounted(async () => {
  try {
    await Promise.all([
      tradeStore.getAccountOverview(),
      tradeStore.getPositions(),
      tradeStore.getOrders({ page: 1, pageSize: 5 }),
      stockStore.getStockList({ market: 'A', page: 1, pageSize: 10 })
    ])
  } catch (e) {
    console.error('加载仪表盘数据失败:', e)
  }

  recentOrders.value = tradeStore.orders.slice(0, 5)
  topPositions.value = [...tradeStore.positions]
    .sort((a, b) => b.profitPercent - a.profitPercent)
    .slice(0, 8)
  hotStocks.value = [...stockStore.stockList]
    .sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent))
    .slice(0, 8)

  await nextTick()
  initPieChart()
})
</script>

<style scoped lang="scss">
.dashboard {
  max-width: 1400px;
}

.overview-row {
  margin-bottom: 16px;
}

.overview-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: transform 0.3s, box-shadow 0.3s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }
}

.card-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.card-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.card-sub {
  font-size: 12px;
  color: #909399;
}

.dashboard-card {
  height: 100%;
  border-radius: 8px;

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid #f0f2f5;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 500;
  font-size: 15px;
  color: #303133;
}

.chart-container {
  height: 300px;
}

.text-danger {
  color: #f56c6c !important;
}

.text-success {
  color: #67c23a !important;
}
</style>
