<template>
  <div class="indices-page">
    <div class="page-header">
      <span class="page-title">大盘指数</span>
      <el-button text type="primary" @click="loadData" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <div class="indices-grid">
      <div
        v-for="index in indices"
        :key="index.code"
        class="index-card"
        :class="{ positive: index.change > 0, negative: index.change < 0 }"
      >
        <!-- 指数基本信息 -->
        <div class="index-main">
          <div class="index-header">
            <span class="index-name">{{ index.name }}</span>
            <span class="index-code">{{ index.code }}</span>
          </div>
          <div class="index-price">
            <span class="price-value">{{ formatNumber(index.current) }}</span>
            <div class="change-box">
              <span class="change-amount">
                {{ index.change >= 0 ? '+' : '' }}{{ formatNumber(index.change) }}
              </span>
              <span class="change-percent">
                {{ index.changePercent >= 0 ? '+' : '' }}{{ index.changePercent.toFixed(2) }}%
              </span>
            </div>
          </div>
          <div class="index-details">
            <div class="detail-row">
              <div class="detail-item">
                <span class="label">今开</span>
                <span class="value">{{ formatNumber(index.open) }}</span>
              </div>
              <div class="detail-item">
                <span class="label">最高</span>
                <span class="value text-danger">{{ formatNumber(index.high) }}</span>
              </div>
              <div class="detail-item">
                <span class="label">昨收</span>
                <span class="value">{{ formatNumber(index.prevClose) }}</span>
              </div>
              <div class="detail-item">
                <span class="label">最低</span>
                <span class="value text-success">{{ formatNumber(index.low) }}</span>
              </div>
            </div>
            <div class="detail-row">
              <div class="detail-item">
                <span class="label">振幅</span>
                <span class="value">{{ calcAmplitude(index).toFixed(2) }}%</span>
              </div>
              <div class="detail-item">
                <span class="label">成交量</span>
                <span class="value">{{ formatVolume(index.volume) }}</span>
              </div>
              <div class="detail-item">
                <span class="label">成交额</span>
                <span class="value">{{ formatAmount(index.amount) }}</span>
              </div>
              <div class="detail-item">
                <span class="label">涨跌额</span>
                <span class="value" :class="index.change >= 0 ? 'text-danger' : 'text-success'">
                  {{ index.change >= 0 ? '+' : '' }}{{ formatNumber2(index.change) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- K线图 -->
        <div class="chart-wrapper">
          <div :ref="el => setChartRef(index.code, el as HTMLElement)" class="kline-chart"></div>
        </div>
      </div>
    </div>

    <!-- 指数说明 -->
    <el-card shadow="hover" class="info-card">
      <template #header>
        <span>指数说明</span>
      </template>
      <div class="index-intro">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="6">
            <div class="intro-item">
              <h4>上证指数</h4>
              <p>上海证券交易所全部A股和B股的加权股价指数，反映上海证券交易所上市股票价格的整体走势。</p>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="intro-item">
              <h4>深证成指</h4>
              <p>深圳证券交易所成分股价指数，选择深圳证券交易所上市A股中具有代表性的40家公司作为样本。</p>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="intro-item">
              <h4>创业板指</h4>
              <p>深圳证券交易所创业板市场指数，选取创业板中最具代表性的100家公司作为样本。</p>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="intro-item">
              <h4>沪深300</h4>
              <p>上海和深圳证券交易所联合发布的A股市场指数，选取沪深两市中市值大、流动性好的300只股票。</p>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getSinaIndices } from '@/api/stock'
import * as echarts from 'echarts'

interface IndexItem {
  code: string
  name: string
  current: number
  change: number
  changePercent: number
  high: number
  low: number
  open: number
  prevClose: number
  volume: number
  amount: number
}

const loading = ref(false)
const indices = ref<IndexItem[]>([])
let refreshTimer: ReturnType<typeof setInterval> | null = null
const chartInstances = new Map<string, echarts.ECharts>()
const chartElements = new Map<string, HTMLElement>()

function setChartRef(code: string, el: HTMLElement | null) {
  if (el) {
    chartElements.set(code, el)
    // 等DOM渲染完成后初始化图表
    nextTick(() => initChart(code))
  }
}

function formatNumber(num: number | undefined): string {
  if (num == null) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatNumber2(num: number | undefined): string {
  if (num == null) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatVolume(vol: number | undefined): string {
  if (vol == null) return '-'
  if (vol >= 100000000) return (vol / 100000000).toFixed(2) + '亿'
  return (vol / 10000).toFixed(2) + '万'
}

function formatAmount(amount: number | undefined): string {
  if (amount == null) return '-'
  return (amount / 100000000).toFixed(2) + '亿'
}

function calcAmplitude(index: IndexItem): number {
  if (!index.prevClose || index.prevClose === 0) return 0
  return ((index.high - index.low) / index.prevClose) * 100
}

const INDEX_NAME_MAP: Record<string, string> = {
  '000001': '上证指数',
  '399001': '深证成指',
  '399006': '创业板指',
  '000300': '沪深300'
}

// 计算移动平均
function calcMA(data: number[], period: number): (number | null)[] {
  const result: (number | null)[] = []
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      result.push(null)
    } else {
      let sum = 0
      for (let j = i - period + 1; j <= i; j++) {
        sum += data[j]
      }
      result.push(Math.round((sum / period) * 100) / 100)
    }
  }
  return result
}

function initChart(code: string) {
  const el = chartElements.get(code)
  if (!el) return

  const old = chartInstances.get(code)
  if (old) old.dispose()

  const index = indices.value.find(i => i.code === code)
  if (!index) return

  const chart = echarts.init(el)
  chartInstances.set(code, chart)

  // 不生成模拟K线数据，图表保持为空
  const resizeHandler = () => chart.resize()
  window.addEventListener('resize', resizeHandler)

  // 清理旧resize监听
  ;(chart as any)._resizeHandler = resizeHandler
}

async function loadData() {
  loading.value = true
  try {
    const res = await getSinaIndices()
    const list = res.data.data || []
    indices.value = list.map((item: any) => ({
      ...item,
      name: INDEX_NAME_MAP[item.code] || item.code
    }))
    // 数据更新后重新渲染图表
    await nextTick()
    indices.value.forEach(idx => {
      const el = chartElements.get(idx.code)
      if (el) initChart(idx.code)
    })
  } catch (e) {
    console.error('加载指数失败:', e)
    indices.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
  refreshTimer = setInterval(loadData, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  chartInstances.forEach((c, code) => {
    const handler = (c as any)._resizeHandler
    if (handler) window.removeEventListener('resize', handler)
    c.dispose()
  })
  chartInstances.clear()
})
</script>

<style scoped lang="scss">
.indices-page {
  max-width: 1400px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}

.info-card {
  border-radius: 8px;
  margin-top: 16px;
}

.indices-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

.index-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
  border-left: 4px solid #909399;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }

  &.positive {
    border-left-color: #f56c6c;
  }

  &.negative {
    border-left-color: #67c23a;
  }
}

.index-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;

  .index-name {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }

  .index-code {
    font-size: 12px;
    color: #909399;
    background: #f5f7fa;
    padding: 2px 8px;
    border-radius: 4px;
  }
}

.index-price {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 14px;

  .price-value {
    font-size: 32px;
    font-weight: 700;
    color: #303133;
  }

  .change-box {
    text-align: right;

    .change-amount {
      display: block;
      font-size: 16px;
      font-weight: 500;
    }

    .change-percent {
      display: block;
      font-size: 14px;
    }
  }

  .positive & .price-value,
  .positive & .change-amount,
  .positive & .change-percent {
    color: #f56c6c;
  }

  .negative & .price-value,
  .negative & .change-amount,
  .negative & .change-percent {
    color: #67c23a;
  }
}

.index-details {
  margin-bottom: 12px;
  padding: 12px;
  background: #fafbfc;
  border-radius: 8px;
}

.detail-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;

  & + & {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid #f0f0f0;
  }
}

.detail-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;

  .label {
    font-size: 11px;
    color: #909399;
  }

  .value {
    font-size: 13px;
    color: #606266;
    font-weight: 500;
  }
}

.chart-wrapper {
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
  animation: fadeIn 0.4s ease;
}

@keyframes fadeIn {
  from { opacity: 0.3; }
  to { opacity: 1; }
}

.kline-chart {
  width: 100%;
  height: 320px;
}

.index-intro {
  .intro-item {
    h4 {
      font-size: 15px;
      color: #303133;
      margin: 0 0 8px 0;
    }

    p {
      font-size: 13px;
      color: #606266;
      margin: 0;
      line-height: 1.6;
    }
  }
}
</style>
