<template>
  <div class="analysis-page">
    <!-- 交易统计概览 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-icon" style="background: rgba(64,158,255,0.1); color: #409eff;">
            <el-icon :size="24"><DataAnalysis /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">总交易次数</div>
            <div class="stat-value">{{ analysis?.totalTradeCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-icon" style="background: rgba(103,194,58,0.1); color: #67c23a;">
            <el-icon :size="24"><TrendCharts /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">胜率</div>
            <div class="stat-value">{{ analysis?.winRate ? (analysis.winRate * 100).toFixed(1) + '%' : '0%' }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-icon" style="background: rgba(245,108,108,0.1); color: #f56c6c;">
            <el-icon :size="24"><Coin /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">平均盈利</div>
            <div class="stat-value text-danger">{{ formatMoney(analysis?.avgProfit) }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-icon" style="background: rgba(230,162,60,0.1); color: #e6a23c;">
            <el-icon :size="24"><ScaleToOriginal /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">盈亏比</div>
            <div class="stat-value">{{ analysis?.profitLossRatio?.toFixed(2) || '0.00' }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 收益曲线 -->
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>收益曲线</span>
              <el-radio-group v-model="profitRange" size="small" @change="loadProfitRecords">
                <el-radio-button label="1w">近1周</el-radio-button>
                <el-radio-button label="1m">近1月</el-radio-button>
                <el-radio-button label="3m">近3月</el-radio-button>
                <el-radio-button label="1y">近1年</el-radio-button>
                <el-radio-button label="all">全部</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <ProfitChart :records="tradeStore.profitRecords" height="360px" />
        </el-card>
      </el-col>

      <!-- 持仓分布 -->
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <span>持仓分布</span>
          </template>
          <div ref="pieChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;">
      <!-- 月度收益 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <span>月度收益</span>
          </template>
          <div ref="barChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 详细统计 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <span>交易统计详情</span>
          </template>
          <div class="detail-stats">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="盈利次数">{{ analysis?.winCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="亏损次数">{{ analysis?.loseCount || 0 }}</el-descriptions-item>
              <el-descriptions-item label="总盈利">
                <span class="text-danger">{{ formatMoney(analysis?.totalProfit) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="总亏损">
                <span class="text-success">{{ formatMoney(analysis?.totalLoss) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="平均亏损">
                <span class="text-success">{{ formatMoney(analysis?.avgLoss) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="最大回撤">
                <span class="text-danger">{{ analysis?.maxDrawdown ? (analysis.maxDrawdown * 100).toFixed(2) + '%' : '-' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="夏普比率">{{ analysis?.sharpeRatio?.toFixed(2) || '-' }}</el-descriptions-item>
              <el-descriptions-item label="总收益率">
                <span :class="getColorClass(analysis?.totalProfit)">
                  {{ formatPercent(((analysis?.totalProfit || 0) / 1000000 * 100)) }}
                </span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 回撤曲线 -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="24">
        <el-card shadow="hover" class="chart-card">
          <template #header><span>回撤曲线</span></template>
          <div ref="drawdownChartRef" class="chart-container" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 月度收益分布 -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="24">
        <el-card shadow="hover" class="chart-card">
          <template #header><span>月度收益分布</span></template>
          <div ref="monthlyChartRef" class="chart-container" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 交易统计详细指标 -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :xs="24" :sm="8">
        <div class="stat-detail-card">
          <div class="label">夏普比率</div>
          <div class="value">{{ analysis?.sharpeRatio?.toFixed(2) || '-' }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="stat-detail-card">
          <div class="label">最大回撤</div>
          <div class="value text-danger">{{ analysis?.maxDrawdown ? (analysis.maxDrawdown * 100).toFixed(2) + '%' : '-' }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="stat-detail-card">
          <div class="label">年化收益率</div>
          <div class="value">{{ analysis?.annualizedReturn ? (analysis.annualizedReturn * 100).toFixed(2) + '%' : '-' }}</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { useTradeStore } from '@/store/trade'
import { formatMoney, formatPercent, getColorClass } from '@/utils/format'

const tradeStore = useTradeStore()
const profitRange = ref('1m')
const pieChartRef = ref<HTMLElement>()
const barChartRef = ref<HTMLElement>()
const drawdownChartRef = ref<HTMLElement>()
const monthlyChartRef = ref<HTMLElement>()

const analysis = computed(() => tradeStore.profitAnalysis)

function initPieChart() {
  if (!pieChartRef.value) return
  const chart = echarts.init(pieChartRef.value)
  const positions = tradeStore.positions

  if (positions.length === 0) {
    chart.setOption({
      title: {
        text: '暂无持仓',
        left: 'center',
        top: 'center',
        textStyle: { color: '#909399', fontSize: 14 }
      }
    })
    return
  }

  const colors = ['#409eff', '#e6a23c', '#67c23a', '#f56c6c', '#909399', '#b37feb', '#36cfc9', '#ff85c0']
  const data = positions.map((p, i) => ({
    name: p.stockName,
    value: p.marketValue,
    itemStyle: { color: colors[i % colors.length] }
  }))

  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0,
      textStyle: { color: '#606266', fontSize: 12 }
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 13, fontWeight: 'bold' }
        },
        data
      }
    ]
  })

  window.addEventListener('resize', () => chart.resize())
}

function initBarChart() {
  if (!barChartRef.value) return
  const chart = echarts.init(barChartRef.value)
  const records = tradeStore.profitRecords

  if (records.length === 0) {
    chart.setOption({
      title: {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#909399', fontSize: 14 }
      }
    })
    return
  }

  // 按月聚合
  const monthlyMap = new Map<string, number>()
  records.forEach(r => {
    const month = r.date.substring(0, 7)
    monthlyMap.set(month, (monthlyMap.get(month) || 0) + r.profit)
  })

  const months = Array.from(monthlyMap.keys()).sort()
  const profits = months.map(m => monthlyMap.get(m) || 0)

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        return `${p.name}<br/>收益: <b style="color:${p.value >= 0 ? '#f56c6c' : '#67c23a'}">${formatMoney(p.value)}</b>`
      }
    },
    grid: { left: 60, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: months,
      axisLabel: { color: '#909399' }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#909399', formatter: (v: number) => formatMoney(v) }
    },
    series: [
      {
        type: 'bar',
        data: profits.map(v => ({
          value: v,
          itemStyle: { color: v >= 0 ? '#f56c6c' : '#67c23a' }
        })),
        barWidth: '50%'
      }
    ]
  })

  window.addEventListener('resize', () => chart.resize())
}

function initDrawdownChart() {
  if (!drawdownChartRef.value) return
  const chart = echarts.init(drawdownChartRef.value)
  const records = tradeStore.profitRecords

  if (records.length === 0) {
    chart.setOption({
      title: {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#909399', fontSize: 14 }
      }
    })
    return
  }

  // 计算回撤：从累积收益中计算
  let peak = -Infinity
  const drawdownData: number[] = []
  const dates: string[] = []
  let cumulative = 0

  records.forEach(r => {
    cumulative += r.profit
    dates.push(r.date)
    if (cumulative > peak) peak = cumulative
    const drawdown = peak > 0 ? (cumulative - peak) / peak : 0
    drawdownData.push(Math.min(drawdown, 0))
  })

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        return `${p.name}<br/>回撤: <b style="color:#f56c6c">${(p.value * 100).toFixed(2)}%</b>`
      }
    },
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#909399', formatter: (v: number) => (v * 100).toFixed(0) + '%' }
    },
    series: [
      {
        type: 'line',
        data: drawdownData,
        smooth: true,
        showSymbol: false,
        lineStyle: { color: '#f56c6c', width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245,108,108,0.3)' },
            { offset: 1, color: 'rgba(245,108,108,0.02)' }
          ])
        }
      }
    ]
  })

  window.addEventListener('resize', () => chart.resize())
}

function initMonthlyChart() {
  if (!monthlyChartRef.value) return
  const chart = echarts.init(monthlyChartRef.value)
  const records = tradeStore.profitRecords

  if (records.length === 0) {
    chart.setOption({
      title: {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#909399', fontSize: 14 }
      }
    })
    return
  }

  // 按月聚合收益
  const monthlyMap = new Map<string, number>()
  records.forEach(r => {
    const month = r.date.substring(0, 7)
    monthlyMap.set(month, (monthlyMap.get(month) || 0) + r.profit)
  })

  const months = Array.from(monthlyMap.keys()).sort()
  // 取最近12个月
  const last12 = months.slice(-12)
  const profits = last12.map(m => monthlyMap.get(m) || 0)

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        return `${p.name}<br/>收益: <b style="color:${p.value >= 0 ? '#f56c6c' : '#67c23a'}">${formatMoney(p.value)}</b>`
      }
    },
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: last12,
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#909399', formatter: (v: number) => formatMoney(v) }
    },
    series: [
      {
        type: 'bar',
        data: profits.map(v => ({
          value: v,
          itemStyle: {
            color: v >= 0
              ? new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: '#f56c6c' },
                  { offset: 1, color: '#ffb0b0' }
                ])
              : new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: '#67c23a' },
                  { offset: 1, color: '#b3e19d' }
                ])
          }
        })),
        barWidth: '60%'
      }
    ]
  })

  window.addEventListener('resize', () => chart.resize())
}

function loadProfitRecords() {
  tradeStore.fetchProfitRecords(profitRange.value)
}

onMounted(async () => {
  try {
    await Promise.all([
      tradeStore.getProfitAnalysis(),
      tradeStore.fetchProfitRecords(profitRange.value),
      tradeStore.getPositions()
    ])
  } catch (e) {
    console.error('加载分析数据失败:', e)
  }

  await nextTick()
  initPieChart()
  initBarChart()
  initDrawdownChart()
  initMonthlyChart()
})

watch(() => tradeStore.profitRecords, async () => {
  await nextTick()
  initBarChart()
  initDrawdownChart()
  initMonthlyChart()
})
</script>

<style scoped lang="scss">
.analysis-page {
  max-width: 1400px;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: transform 0.3s;

  &:hover {
    transform: translateY(-2px);
  }
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.chart-card {
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
  flex-wrap: wrap;
  gap: 12px;
}

.chart-container {
  height: 320px;
}

.detail-stats {
  padding: 8px 0;
}

.stat-detail-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  text-align: center;
  transition: transform 0.3s;

  &:hover {
    transform: translateY(-2px);
  }

  .label {
    font-size: 13px;
    color: #909399;
    margin-bottom: 8px;
  }

  .value {
    font-size: 22px;
    font-weight: 600;
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
