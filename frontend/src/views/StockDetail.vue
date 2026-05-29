<template>
  <div class="stock-detail-page">
    <!-- 返回导航 + 股票摘要 -->
    <div class="stock-header-bar">
      <el-button text @click="goBack" class="back-btn">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <div class="stock-meta">
        <h2 class="stock-name">{{ stockInfo?.name || '-' }}</h2>
        <span class="stock-code">{{ stockInfo?.code || '-' }}</span>
        <el-tag size="small" :type="stockInfo?.market === 'A_STOCK' ? '' : 'info'" class="market-tag">
          {{ marketLabel }}
        </el-tag>
      </div>
      <div class="stock-price-area">
        <span class="price-value" :style="{ color: priceColor }">{{ formatPrice(stockInfo?.currentPrice) }}</span>
        <span class="price-change" :style="{ color: priceColor }">
          {{ stockInfo?.changeAmount >= 0 ? '+' : '' }}{{ formatPrice(stockInfo?.changeAmount) }}
        </span>
        <span class="price-percent" :style="{ color: priceColor }">
          {{ formatPercent(stockInfo?.changePercent) }}
        </span>
      </div>
      <div class="stock-actions">
        <el-button type="danger" size="small" @click="handleTrade('BUY')"><el-icon><Top /></el-icon> 买入</el-button>
        <el-button type="success" size="small" @click="handleTrade('SELL')"><el-icon><Bottom /></el-icon> 卖出</el-button>
        <el-button size="small" @click="addToWatchlist"><el-icon><Star /></el-icon> 自选</el-button>
      </div>
    </div>

    <!-- 主内容区：左图表 + 右盘口 -->
    <div class="main-content">
      <!-- 左侧：图表区 -->
      <div class="chart-area">
        <!-- 图表工具栏 -->
        <div class="chart-toolbar">
          <div class="toolbar-group">
            <el-tooltip content="十字准线"><el-button text size="small" :type="chartTool === 'crosshair' ? 'primary' : ''" @click="chartTool = 'crosshair'"><el-icon><FullScreen /></el-icon></el-button></el-tooltip>
            <el-tooltip content="趋势线"><el-button text size="small" :type="chartTool === 'trendline' ? 'primary' : ''" @click="chartTool = 'trendline'"><el-icon><Share /></el-icon></el-button></el-tooltip>
            <el-tooltip content="水平线"><el-button text size="small" :type="chartTool === 'horiz' ? 'primary' : ''" @click="chartTool = 'horiz'"><el-icon><Minus /></el-icon></el-button></el-tooltip>
            <el-tooltip content="斐波那契"><el-button text size="small" :type="chartTool === 'fib' ? 'primary' : ''" @click="chartTool = 'fib'"><el-icon><Sort /></el-icon></el-button></el-tooltip>
            <el-tooltip content="文本标注"><el-button text size="small" :type="chartTool === 'text' ? 'primary' : ''" @click="chartTool = 'text'"><el-icon><EditPen /></el-icon></el-button></el-tooltip>
            <el-divider direction="vertical" />
            <el-tooltip content="撤销"><el-button text size="small" @click="chartUndo"><el-icon><Back /></el-icon></el-button></el-tooltip>
            <el-tooltip content="重做"><el-button text size="small" @click="chartRedo"><el-icon><Right /></el-icon></el-button></el-tooltip>
          </div>
          <div class="toolbar-group">
            <el-radio-group v-model="chartType" size="small">
              <el-radio-button value="candlestick">K线</el-radio-button>
              <el-radio-button value="line">线图</el-radio-button>
              <el-radio-button value="area">面积</el-radio-button>
            </el-radio-group>
          </div>
          <div class="toolbar-group">
            <el-radio-group v-model="klinePeriod" size="small" @change="loadKlineData">
              <el-radio-button label="1m">1分</el-radio-button>
              <el-radio-button label="5m">5分</el-radio-button>
              <el-radio-button label="15m">15分</el-radio-button>
              <el-radio-button label="60m">60分</el-radio-button>
              <el-radio-button label="daily">日K</el-radio-button>
              <el-radio-button label="weekly">周K</el-radio-button>
              <el-radio-button label="monthly">月K</el-radio-button>
            </el-radio-group>
          </div>
          <div class="toolbar-group">
            <el-dropdown trigger="click" @command="toggleIndicator">
              <el-button size="small">指标 <el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="ind in allIndicators" :key="ind" :command="ind">
                    <el-tag v-if="activeIndicators.includes(ind)" size="small" type="success" style="margin-right:6px">✓</el-tag>
                    <el-tag v-else size="small" type="info" style="margin-right:6px;visibility:hidden">✓</el-tag>
                    {{ indicatorLabel(ind) }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <!-- K线图 -->
        <div class="chart-container">
          <KlineChart
            :kline-data="klineData"
            :indicators="activeIndicators"
            :chart-type="chartType"
            :drawing-tool="chartTool"
            height="460px"
            ref="klineChartRef"
          />
        </div>
      </div>

      <!-- 右侧：盘口信息 -->
      <div class="orderbook-area">
        <!-- 五档盘口 -->
        <div class="orderbook-panel section-card">
          <div class="section-title">五档盘口</div>
          <div class="ob-table">
            <div class="ob-header"><span>档位</span><span>价格</span><span>数量</span></div>
            <div v-for="(lvl, i) in orderBookSell" :key="'s'+i" class="ob-row sell" :style="{'--bar-pct': lvl.pct + '%'}">
              <span class="ob-level">卖{{ 5 - i }}</span>
              <span class="ob-price text-danger">{{ formatPrice(lvl.price) }}</span>
              <span class="ob-qty">{{ formatVolume(lvl.qty) }}</span>
            </div>
            <div class="ob-spread">
              <span>委比 {{ orderBook?.ratio || 0 }}%</span>
              <span>差额 {{ formatVolume(orderBook?.diff) }}</span>
            </div>
            <div v-for="(lvl, i) in orderBookBuy" :key="'b'+i" class="ob-row buy" :style="{'--bar-pct': lvl.pct + '%'}">
              <span class="ob-level">买{{ i + 1 }}</span>
              <span class="ob-price text-success">{{ formatPrice(lvl.price) }}</span>
              <span class="ob-qty">{{ formatVolume(lvl.qty) }}</span>
            </div>
          </div>
        </div>

        <!-- 资金流向 -->
        <div class="capital-flow-panel section-card">
          <div class="section-title">资金流向</div>
          <div ref="capitalFlowRef" class="cf-chart" style="height:160px"></div>
          <div class="cf-stats">
            <div class="cf-item"><span class="cf-label">主力净流入</span><span class="cf-value" :class="capitalFlow?.mainForce >= 0 ? 'up' : 'down'">{{ formatMoney(capitalFlow?.mainForce) }}</span></div>
            <div class="cf-item"><span class="cf-label">超大单</span><span class="cf-value" :class="capitalFlow?.superLarge >= 0 ? 'up' : 'down'">{{ formatMoney(capitalFlow?.superLarge) }}</span></div>
            <div class="cf-item"><span class="cf-label">大单</span><span class="cf-value" :class="capitalFlow?.large >= 0 ? 'up' : 'down'">{{ formatMoney(capitalFlow?.large) }}</span></div>
            <div class="cf-item"><span class="cf-label">中单</span><span class="cf-value" :class="capitalFlow?.medium >= 0 ? 'up' : 'down'">{{ formatMoney(capitalFlow?.medium) }}</span></div>
            <div class="cf-item"><span class="cf-label">小单</span><span class="cf-value" :class="capitalFlow?.small >= 0 ? 'up' : 'down'">{{ formatMoney(capitalFlow?.small) }}</span></div>
          </div>
        </div>

        <!-- 快捷信息 -->
        <div class="quick-info section-card">
          <div class="info-row"><span class="label">今开</span><span class="value">{{ formatPrice(stockInfo?.openPrice) }}</span></div>
          <div class="info-row"><span class="label">最高</span><span class="value text-danger">{{ formatPrice(stockInfo?.highPrice) }}</span></div>
          <div class="info-row"><span class="label">最低</span><span class="value text-success">{{ formatPrice(stockInfo?.lowPrice) }}</span></div>
          <div class="info-row"><span class="label">昨收</span><span class="value">{{ formatPrice(stockInfo?.closePrice) }}</span></div>
          <div class="info-row"><span class="label">成交量</span><span class="value">{{ formatVolume(stockInfo?.volume) }}</span></div>
          <div class="info-row"><span class="label">成交额</span><span class="value">{{ formatMoney(stockInfo?.turnover) }}</span></div>
          <div class="info-row"><span class="label">换手率</span><span class="value">{{ stockInfo?.turnoverRate ? stockInfo.turnoverRate.toFixed(2) + '%' : '-' }}</span></div>
          <div class="info-row"><span class="label">市盈率</span><span class="value">{{ stockInfo?.pe ? stockInfo.pe.toFixed(2) : '-' }}</span></div>
          <div class="info-row"><span class="label">总市值</span><span class="value">{{ formatMoney(stockInfo?.marketCap) }}</span></div>
        </div>
      </div>
    </div>

    <!-- 底部标签区 -->
    <div class="bottom-tabs">
      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="技术面信号" name="signal">
          <SignalIndicator :signals="signals" :stock-code="stockCode" />
        </el-tab-pane>
        <el-tab-pane label="AI研判" name="ai">
          <div class="ai-section-inner">
            <el-button type="primary" size="small" :loading="aiLoading" @click="loadAiAnalysis" v-if="!aiResult">
              <el-icon><Monitor /></el-icon> AI深度分析
            </el-button>
            <div v-if="aiResult" class="ai-result-box">
              <div class="ai-score">{{ aiResult.signal }} — 评分 {{ aiResult.score }}/100</div>
              <div v-html="renderedAnalysis" class="ai-analysis-text"></div>
            </div>
            <el-empty v-else-if="!aiLoading" description="点击按钮获取AI分析" :image-size="50" />
            <div v-else class="ai-loading"><el-icon class="is-loading" :size="24"><Loading /></el-icon> AI分析中...</div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="公司概况" name="f10">
          <div class="f10-content" v-if="companyInfo">
            <div class="f10-section"><h4>公司简介</h4><p>{{ companyInfo.profile }}</p></div>
            <div class="f10-grid">
              <div class="f10-item"><span>主营业务</span><span>{{ companyInfo.business }}</span></div>
              <div class="f10-item"><span>所属行业</span><span>{{ companyInfo.industry }}</span></div>
              <div class="f10-item"><span>上市日期</span><span>{{ companyInfo.listDate }}</span></div>
              <div class="f10-item"><span>员工人数</span><span>{{ companyInfo.employees }}</span></div>
              <div class="f10-item"><span>注册资本</span><span>{{ formatMoney(companyInfo.registeredCapital) }}</span></div>
              <div class="f10-item"><span>董事长</span><span>{{ companyInfo.chairman }}</span></div>
            </div>
          </div>
          <el-empty v-else description="暂无公司信息" :image-size="50" />
        </el-tab-pane>
        <el-tab-pane label="财务数据" name="finance">
          <el-empty description="财务数据加载中" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Top, Bottom, Star, FullScreen, Share, Minus, Sort, EditPen, Back, Right, ArrowDown, Monitor, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getRealtimeQuote, getKlineData } from '@/api/stock'
import { formatPrice, formatPercent, formatNumber, formatMoney, getColor } from '@/utils/format'
import KlineChart from '@/components/KlineChart.vue'
import SignalIndicator from '@/components/SignalIndicator.vue'
import type { TradeSignal } from '@/types'

const route = useRoute()
const router = useRouter()
const stockCode = computed(() => route.params.code as string)

// ===== Stock Info =====
const loading = ref(true)
const stockInfo = ref<any>(null)
const klineData = ref<any>(null)
const signals = ref<TradeSignal[]>([])
const klinePeriod = ref('daily')
const chartType = ref('candlestick')
const chartTool = ref('crosshair')
const activeIndicators = ref<string[]>(['MA'])
const klineChartRef = ref()

const allIndicators = ['MA', 'MACD', 'RSI', 'KDJ', 'BOLL', 'VOL', 'OBV', 'ATR', 'CCI', 'WR']
function indicatorLabel(ind: string) {
  const map: Record<string, string> = { MA: '移动均线', MACD: 'MACD', RSI: 'RSI', KDJ: 'KDJ', BOLL: '布林带', VOL: '成交量', OBV: 'OBV', ATR: 'ATR', CCI: 'CCI', WR: '威廉%R' }
  return map[ind] || ind
}
function toggleIndicator(ind: string) {
  const idx = activeIndicators.value.indexOf(ind)
  if (idx >= 0) activeIndicators.value.splice(idx, 1)
  else activeIndicators.value.push(ind)
}
function chartUndo() { /* placeholder */ }
function chartRedo() { /* placeholder */ }

const marketLabel = computed(() => {
  const m = stockInfo.value?.market
  if (m === 'A_STOCK' || m === 'A') return 'A股'
  if (m === 'NASDAQ' || m === 'NYSE') return '美股'
  if (m === 'HK') return '港股'
  return m || '-'
})
const priceColor = computed(() => getColor(stockInfo.value?.changePercent ?? 0))

// ===== 五档盘口 =====
const orderBook = ref<{ buy: any[]; sell: any[]; ratio: number; diff: number } | null>(null)
const orderBookBuy = computed(() => orderBook.value?.buy || [])
const orderBookSell = computed(() => orderBook.value?.sell || [])

// ===== 资金流向 =====
const capitalFlowRef = ref<HTMLElement>()
const capitalFlow = ref<{ mainForce: number; superLarge: number; large: number; medium: number; small: number } | null>(null)

function initCapitalFlowChart() {
  if (!capitalFlowRef.value) return
  const chart = echarts.init(capitalFlowRef.value)
  const cf = capitalFlow.value || { mainForce: 0, superLarge: 0, large: 0, medium: 0, small: 0 }
  const data = [
    { name: '超大单', value: cf.superLarge },
    { name: '大单', value: cf.large },
    { name: '中单', value: cf.medium },
    { name: '小单', value: cf.small }
  ]
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '3%', top: '5%', bottom: '3%' },
    xAxis: { type: 'category', data: data.map(d => d.name), axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 10, formatter: (v: number) => v >= 100000000 ? (v/100000000).toFixed(1)+'亿' : v >= 10000 ? (v/10000).toFixed(1)+'万' : v.toFixed(0) } },
    series: [{ type: 'bar', data: data.map(d => ({ value: d.value, itemStyle: { color: d.value >= 0 ? '#f56c6c' : '#67c23a' } })), barWidth: '50%' }]
  })
}

// ===== F10 =====
const companyInfo = ref<any>(null)

// ===== Tab =====
const activeTab = ref('signal')

// ===== AI =====
const aiResult = ref<any>(null)
const aiLoading = ref(false)
const renderedAnalysis = computed(() => {
  if (!aiResult.value?.analysis) return ''
  return aiResult.value.analysis.replace(/### (.*)/g, '<h4>$1</h4>').replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>').replace(/\n/g, '<br/>')
})
async function loadAiAnalysis() {
  aiLoading.value = true
  try { const { analyzeStock } = await import('@/api/ai'); const r = await analyzeStock({ code: stockCode.value }); aiResult.value = r.data?.data || null } catch { ElMessage.error('AI分析失败') }
  finally { aiLoading.value = false }
}

// ===== Data Loading =====
async function loadData() {
  loading.value = true
  try {
    const res = await getRealtimeQuote(stockCode.value)
    stockInfo.value = res.data?.data || null
    capitalFlow.value = null
    companyInfo.value = null
  } catch { ElMessage.error('加载股票信息失败') }
  finally { loading.value = false }
}

async function loadKlineData() {
  try {
    const res = await getKlineData(stockCode.value, klinePeriod.value)
    klineData.value = res.data?.data || null
  } catch { /* use empty */ }
}

function goBack() { router.back() }
function handleTrade(dir: string) { router.push({ path: '/trade', query: { code: stockCode.value, direction: dir } }) }
function addToWatchlist() { ElMessage.success('已添加到自选股') }
function formatVolume(v: any) { return formatNumber(v) }

onMounted(async () => {
  await loadData()
  await loadKlineData()
  await nextTick()
  initCapitalFlowChart()
})

watch(capitalFlow, () => { nextTick(() => initCapitalFlowChart()) })
</script>

<style scoped lang="scss">
.stock-detail-page { max-width: 1400px; }

// Header Bar
.stock-header-bar {
  display: flex; align-items: center; gap: 12px; margin-bottom: 12px;
  padding: 10px 16px; background: rgba(255,255,255,0.92); border-radius: 10px;
  border: 1px solid #ebeef5; flex-wrap: wrap;
  .back-btn { flex-shrink: 0; }
  .stock-meta { display: flex; align-items: center; gap: 8px; flex-shrink: 0;
    .stock-name { font-size: 20px; font-weight: 700; margin: 0; color: #303133; }
    .stock-code { font-size: 13px; color: #909399; }
  }
  .stock-price-area { display: flex; align-items: baseline; gap: 8px; flex-shrink: 0;
    .price-value { font-size: 28px; font-weight: 700; }
    .price-change { font-size: 16px; font-weight: 600; }
    .price-percent { font-size: 16px; font-weight: 600; }
  }
  .stock-actions { margin-left: auto; display: flex; gap: 6px; }
}

// Main Layout
.main-content { display: flex; gap: 12px; margin-bottom: 12px;
  .chart-area { flex: 1; min-width: 0; }
  .orderbook-area { width: 280px; flex-shrink: 0; }
}

// Chart Toolbar
.chart-toolbar {
  display: flex; align-items: center; gap: 4px; flex-wrap: wrap;
  padding: 6px 10px; background: rgba(255,255,255,0.92); border: 1px solid #ebeef5;
  border-radius: 8px 8px 0 0; margin-bottom: 1px;
  .toolbar-group { display: flex; align-items: center; gap: 2px; }
  &:empty { display: none; }
}

.chart-container {
  background: rgba(255,255,255,0.92); border: 1px solid #ebeef5;
  border-radius: 0 0 8px 8px; padding: 4px;
}

// Orderbook
.section-card { background: rgba(255,255,255,0.92); border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; margin-bottom: 10px; }
.section-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }

.ob-table { font-size: 12px;
  .ob-header { display: flex; justify-content: space-between; padding: 4px 8px; color: #909399; font-size: 11px; }
  .ob-row { display: flex; justify-content: space-between; padding: 3px 8px; position: relative; cursor: default;
    &::before { content: ''; position: absolute; right: 0; top: 0; height: 100%; width: var(--bar-pct); opacity: 0.1; border-radius: 2px; }
    &.sell::before { background: #f56c6c; }
    &.buy::before { background: #67c23a; }
    .ob-level { width: 30px; color: #909399; }
    .ob-price { width: 80px; text-align: right; font-weight: 600; }
    .ob-qty { width: 60px; text-align: right; }
  }
  .ob-spread { display: flex; justify-content: space-between; padding: 4px 8px; background: #f5f7fa; font-size: 11px; color: #909399; border-top: 1px solid #ebeef5; border-bottom: 1px solid #ebeef5; }
}

// Capital Flow
.cf-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; margin-top: 8px;
  .cf-item { display: flex; justify-content: space-between; padding: 2px 4px; font-size: 12px;
    .cf-label { color: #909399; }
    .cf-value { font-weight: 600; &.up { color: #f56c6c; } &.down { color: #67c23a; } }
  }
}

// Quick Info
.quick-info .info-row { display: flex; justify-content: space-between; padding: 3px 4px; font-size: 12px;
  .label { color: #909399; } .value { font-weight: 500; color: #303133; }
}

// Bottom Tabs
.bottom-tabs { background: rgba(255,255,255,0.92); border: 1px solid #ebeef5; border-radius: 8px; padding: 8px 16px; }
.detail-tabs { :deep(.el-tabs__header) { margin-bottom: 12px; } }

// F10
.f10-content { padding: 8px 0;
  .f10-section { margin-bottom: 16px;
    h4 { font-size: 15px; color: #303133; margin: 0 0 8px 0; }
    p { font-size: 13px; color: #606266; line-height: 1.7; margin: 0; }
  }
  .f10-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;
    .f10-item { display: flex; flex-direction: column; padding: 8px 12px; background: #f5f7fa; border-radius: 6px;
      span:first-child { font-size: 11px; color: #909399; }
      span:last-child { font-size: 13px; color: #303133; font-weight: 500; }
    }
  }
}

// AI
.ai-section-inner { padding: 8px 0; min-height: 100px; }
.ai-result-box {
  .ai-score { font-size: 16px; font-weight: 700; margin-bottom: 12px; color: #409eff; }
  .ai-analysis-text { font-size: 13px; line-height: 1.8; color: #606266; }
}
.ai-loading { display: flex; align-items: center; gap: 8px; justify-content: center; color: #909399; padding: 40px 0; }
</style>
