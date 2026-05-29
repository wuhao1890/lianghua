<template>
  <div class="gold-page" v-loading="loading">
    <!-- 页面标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <el-icon :size="22" color="#e6a23c"><Coin /></el-icon>
        <span class="page-title">黄金中心</span>
        <el-tag size="small" type="warning" effect="plain" v-if="currentProductName">{{ currentProductName }}</el-tag>
      </div>
      <el-button type="primary" @click="loadAllData" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <!-- 产品切换栏 -->
    <el-card shadow="hover" class="switcher-card">
      <div class="switcher-bar">
        <span class="switcher-label">黄金产品：</span>
        <el-radio-group v-model="productCode" size="default">
          <el-radio-button
            v-for="(name, code) in productMap"
            :key="code"
            :value="code"
          >
            {{ name }}
          </el-radio-button>
        </el-radio-group>
      </div>
    </el-card>

    <!-- 实时价格卡片 -->
    <el-card shadow="hover" class="price-card">
      <div class="price-main">
        <div class="price-info">
          <div class="price-label">当前价格</div>
          <div class="price-value" :style="{ color: getColor(latestQuote.changePercent) }">
            ¥{{ formatPrice(latestQuote.price) }}
          </div>
          <div class="price-change">
            <span
              class="change-badge"
              :class="safeScore(latestQuote.changePercent) >= 0 ? 'up' : 'down'"
            >
              {{ (safeScore(latestQuote.changePercent) >= 0 ? '+' : '') + formatPercent(latestQuote.changePercent) }}
            </span>
          </div>
        </div>
        <div class="price-details">
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">今日最高</span>
              <span class="detail-value text-danger">
                ¥{{ formatPrice(latestQuote.high) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">今日最低</span>
              <span class="detail-value text-success">
                ¥{{ formatPrice(latestQuote.low) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">开盘价</span>
              <span class="detail-value">
                ¥{{ formatPrice(latestQuote.openPrice) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">昨收价</span>
              <span class="detail-value">
                ¥{{ formatPrice(prevClose) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">数据日期</span>
              <span class="detail-value">
                {{ latestQuote.tradeDate || '-' }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">产品代码</span>
              <span class="detail-value code-value">{{ productCode }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- K线图 -->
    <el-card shadow="hover" class="chart-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="section-title">K线走势</span>
            <el-radio-group v-model="klinePeriod" size="small" @change="onPeriodChange">
              <el-radio-button label="5d">5日</el-radio-button>
              <el-radio-button label="1m">1月</el-radio-button>
              <el-radio-button label="3m">3月</el-radio-button>
            </el-radio-group>
          </div>
          <div class="header-right">
            <el-checkbox-group v-model="klineIndicators" size="small">
              <el-checkbox-button label="MA">MA</el-checkbox-button>
              <el-checkbox-button label="BOLL">BOLL</el-checkbox-button>
            </el-checkbox-group>
          </div>
        </div>
      </template>
      <div ref="klineChartRef" class="kline-chart"></div>
    </el-card>

    <!-- 技术面分析 -->
    <el-card shadow="hover" class="signal-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="section-title">技术面买卖信号</span>
          </div>
          <div class="header-right">
            <el-checkbox-group v-model="activeIndicators" size="small">
              <el-checkbox-button label="MA">MA</el-checkbox-button>
              <el-checkbox-button label="MACD">MACD</el-checkbox-button>
              <el-checkbox-button label="RSI">RSI</el-checkbox-button>
              <el-checkbox-button label="KDJ">KDJ</el-checkbox-button>
              <el-checkbox-button label="BOLL">BOLL</el-checkbox-button>
            </el-checkbox-group>
          </div>
        </div>
      </template>
      <SignalIndicator :signals="signals" :stock-code="productCode" />
    </el-card>

    <!-- AI 智能研判 -->
    <el-card shadow="hover" class="ai-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="section-title">AI 智能综合研判（含大V舆情）</span>
          </div>
          <el-button
            type="primary"
            size="small"
            :loading="aiLoading"
            :disabled="!hasAiConfig || aiLoading"
            @click="loadAiAnalysis"
          >
            <el-icon><Monitor /></el-icon>
            {{ aiResult ? '刷新深度分析' : 'AI深度分析' }}
          </el-button>
        </div>
      </template>

      <!-- AI分析结果 -->
      <template v-if="aiResult">
        <!-- 评分栏 -->
        <div class="ai-score-bar">
          <div class="ai-signal">
            <span class="label">AI综合信号</span>
            <el-tag :type="getAiSignalType(aiResult.signal)" size="large" effect="dark">
              {{ getAiSignalText(aiResult.signal) }}
            </el-tag>
          </div>
          <div class="ai-scores">
            <div class="ai-score-item">
              <span class="label">综合评分</span>
              <el-progress
                :percentage="safeScore(aiResult.score)"
                :stroke-width="18"
                :color="getScoreColor(aiResult.score || 0)"
                :format="() => `${safeScore(aiResult.score)}/100`"
              />
            </div>
            <div class="ai-score-item">
              <span class="label">技术面 (50%)</span>
              <el-progress :percentage="safeScore(aiResult.techScore) * 2" :stroke-width="14" color="#e6a23c"
                :format="() => `${safeScore(aiResult.techScore)}/50`" />
            </div>
            <div class="ai-score-item">
              <span class="label">舆情面 (50%)</span>
              <el-progress :percentage="safeScore(aiResult.sentimentScore) * 2" :stroke-width="14" color="#409eff"
                :format="() => `${safeScore(aiResult.sentimentScore)}/50`" />
            </div>
          </div>
          <div v-if="aiResult.targetPrice" class="ai-target">
            <span class="label">目标价：</span>
            <span class="value">{{ aiResult.targetPrice }}</span>
          </div>
          <div class="ai-model">
            分析模型：<el-tag size="small">{{ aiResult.modelUsed || '-' }}</el-tag>
          </div>
        </div>

        <!-- 大V舆情 -->
        <div class="ai-section" v-if="aiResult.daVMajority">
          <h4 class="section-title-inner">
            <span>大V舆情分析（占综合评分的50%）</span>
            <el-tag size="small" type="info" class="source-tip">以下内容由AI基于市场公开信息模拟生成</el-tag>
          </h4>
          <!-- 共识 -->
          <div class="consensus-card" :class="'consensus-' + aiResult.daVMajority.consensus">
            <div class="consensus-header">
              <el-tag :type="getConsensusTagType(aiResult.daVMajority.consensus)" effect="dark" size="large">
                {{ getConsensusText(aiResult.daVMajority.consensus) }}
              </el-tag>
              <span class="consensus-summary">{{ aiResult.daVMajority.summary }}</span>
            </div>
            <div class="consensus-counts">
              <span class="count bullish">看涨 {{ aiResult.daVMajority.bullishCount || 0 }}</span>
              <span class="count bearish">看跌 {{ aiResult.daVMajority.bearishCount || 0 }}</span>
              <span class="count neutral">中性 {{ aiResult.daVMajority.neutralCount || 0 }}</span>
            </div>
          </div>
          <!-- 大V列表 -->
          <div class="dav-grid" v-if="aiResult.daVOpinions?.length">
            <div
              v-for="(op, i) in aiResult.daVOpinions"
              :key="i"
              class="dav-card"
              :class="'dav-' + op.type"
              @click="showDavDetail(op)"
            >
              <div class="dav-header">
                <span class="dav-name">{{ op.name }}</span>
                <el-tag :type="getDavTagType(op.type)" size="small" effect="dark">
                  {{ getDavText(op.type) }}
                </el-tag>
              </div>
              <div class="dav-view">{{ op.view }}</div>
              <div class="dav-detail">{{ op.detail }}</div>
              <div class="dav-footer">
                <div class="dav-meta">
                  <span class="dav-time" v-if="op.publishTime">{{ op.publishTime }}</span>
                  <span class="dav-influence">
                    影响力：<span v-for="s in 5" :key="s" class="star" :class="{ active: s <= (op.influence || 3) }">★</span>
                  </span>
                </div>
                <el-button text size="small" type="primary" class="source-btn">查看来源 →</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 详细分析 -->
        <div class="ai-section" v-if="aiResult.analysis">
          <h4 class="section-title-inner"><span>综合操作建议</span></h4>
          <div class="markdown-body" v-html="renderedAnalysis"></div>
        </div>
      </template>

      <template v-else-if="!aiLoading">
        <el-empty :image-size="60" description="点击右上角「AI分析」按钮，获取包含大V舆情的详细综合研判" />
        <div v-if="!hasAiConfig" class="no-config-hint">
          <el-alert title="提示" type="warning" :closable="false" show-icon>
            <template #default>请先在"AI Agent"页面配置模型并保存，即可使用AI智能研判功能</template>
          </el-alert>
        </div>
      </template>
      <template v-else>
        <div class="ai-loading">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p>AI正在分析数据，请稍候（约10-30秒）...</p>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Refresh, Coin, Monitor, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getGoldProducts,
  getGoldLatest,
  getGoldHistory
} from '@/api/gold'
import type { GoldQuote } from '@/api/gold'
import { formatPrice, formatPercent, getColor } from '@/utils/format'
import SignalIndicator from '@/components/SignalIndicator.vue'
import type { TradeSignal, AiAnalysisResponse } from '@/types'
import { getTradeSignal } from '@/api/analysis'
import { getModelConfigs, analyzeStock } from '@/api/ai'

// ==================== 状态 ====================
const loading = ref(false)

// 产品映射: code -> name
const productMap = ref<Record<string, string>>({
  hf_GC: 'COMEX黄金期货',
  sh518880: '华安黄金ETF(518880)',
  sz159934: '易方达黄金ETF(159934)',
  sz159937: '博时黄金ETF(159937)',
  sz159812: '黄金基金ETF(159812)'
})

const productCode = ref('hf_GC')
const currentProductName = computed(() => productMap.value[productCode.value] || '')

// 实时行情
const latestQuote = reactive<GoldQuote>({
  price: 0,
  changePercent: 0,
  high: 0,
  low: 0,
  openPrice: 0,
  tradeDate: ''
})

// 昨收价（取历史最后一条的收盘价作为昨收）
const prevClose = ref(0)

// 历史数据 - 用于K线图
const historyList = ref<GoldQuote[]>([])

// K线图
const klinePeriod = ref('1m')
const klineIndicators = ref<string[]>(['MA'])
const klineChartRef = ref<HTMLElement>()
let klineChart: echarts.ECharts | null = null

// 技术面信号
const activeIndicators = ref<string[]>(['MA'])
const signals = ref<TradeSignal[]>([])

// AI分析
const aiResult = ref<AiAnalysisResponse | null>(null)
const aiLoading = ref(false)
const hasAiConfig = ref(false)

// ==================== 周期天数映射 ====================
function getPeriodDays(period: string): number {
  switch (period) {
    case '5d': return 5
    case '1m': return 30
    case '3m': return 90
    default: return 30
  }
}

// ==================== 渲染Markdown ====================
const renderedAnalysis = computed(() => {
  if (!aiResult.value?.analysis) return ''
  return aiResult.value.analysis
    .replace(/### (.*?)$/gm, '<h4>$1</h4>')
    .replace(/## (.*?)$/gm, '<h3>$1</h3>')
    .replace(/# (.*?)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/- (.*?)$/gm, '<li>$1</li>')
    .replace(/(<li>[\s\S]*?<\/li>)/g, '<ul>$1</ul>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br/>')
    .replace(/^(.+?)$/m, '<p>$1</p>')
})

// ==================== 数据加载 ====================
async function loadLatestPrice() {
  try {
    const res = await getGoldLatest(productCode.value)
    const latest = res.data?.data
    if (latest) {
      latestQuote.price = safeScore(latest.price)
      latestQuote.changePercent = safeScore(latest.changePercent)
      latestQuote.high = safeScore(latest.high)
      latestQuote.low = safeScore(latest.low)
      latestQuote.openPrice = safeScore(latest.openPrice)
      latestQuote.tradeDate = latest.tradeDate || ''
    }
  } catch (e: any) {
    console.error('加载实时价格失败:', e)
  }
}

async function loadHistory() {
  const days = getPeriodDays(klinePeriod.value)
  try {
    const res = await getGoldHistory(productCode.value, days)
    const history = res.data?.data
    if (Array.isArray(history)) {
      historyList.value = history
    } else if (history?.list) {
      historyList.value = history.list
    } else {
      historyList.value = []
    }

    // 计算昨收价（如果有至少2条数据，取倒数第二条的收盘价）
    if (historyList.value.length >= 2) {
      prevClose.value = safeScore(historyList.value[historyList.value.length - 2].price)
    } else {
      prevClose.value = 0
    }
  } catch (e: any) {
    console.error('加载历史数据失败:', e)
    historyList.value = []
  }
}

async function loadSignals() {
  try {
    const res = await getTradeSignal(productCode.value)
    signals.value = res.data?.data || []
  } catch (e) {
    console.error('加载买卖信号失败:', e)
    signals.value = []
  }
}

async function loadAllData() {
  loading.value = true
  try {
    await Promise.all([
      loadLatestPrice(),
      loadHistory()
    ])
    await loadSignals()
  } catch (e: any) {
    ElMessage.error(e.message || '加载黄金数据失败')
  } finally {
    loading.value = false
  }
}

// ==================== K线图 ====================
function buildKlineOption(): echarts.EChartsOption | null {
  if (historyList.value.length === 0) return null

  const dates = historyList.value.map(d => d.tradeDate)
  const prices = historyList.value.map(d => safeScore(d.price))
  const changes = historyList.value.map(d => safeScore(d.changePercent))
  const volumes = historyList.value.map(d => safeScore((d as any).volume || 0))

  const showMA = klineIndicators.value.includes('MA')
  const showBOLL = klineIndicators.value.includes('BOLL')

  // 计算MA
  const ma5 = calcMA(prices, 5)
  const ma10 = calcMA(prices, 10)
  const ma20 = calcMA(prices, 20)

  // 计算BOLL
  const boll20 = calcBOLL(prices, 20)

  // 颜色: 红涨绿跌
  const lineColors = changes.map(c => c >= 0 ? '#f56c6c' : '#67c23a')

  const series: any[] = [
    {
      name: '价格',
      type: 'line',
      data: prices.map((p, i) => ({
        value: p,
        itemStyle: { color: lineColors[i] }
      })),
      smooth: false,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: {
        width: 2,
        color: '#409eff'
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64, 158, 255, 0.15)' },
          { offset: 1, color: 'rgba(64, 158, 255, 0.02)' }
        ])
      },
      markLine: {
        silent: true,
        data: [
          { type: 'average', name: '均价' }
        ],
        lineStyle: { type: 'dashed', color: '#909399' },
        label: { formatter: '均价: {c}', fontSize: 11 }
      }
    }
  ]

  // MA均线
  if (showMA) {
    series.push({
      name: 'MA5',
      type: 'line',
      data: ma5,
      smooth: false,
      symbol: 'none',
      lineStyle: { width: 1, color: '#e6a23c' }
    })
    series.push({
      name: 'MA10',
      type: 'line',
      data: ma10,
      smooth: false,
      symbol: 'none',
      lineStyle: { width: 1, color: '#409eff' }
    })
    series.push({
      name: 'MA20',
      type: 'line',
      data: ma20,
      smooth: false,
      symbol: 'none',
      lineStyle: { width: 1, color: '#8b5cf6' }
    })
  }

  // BOLL
  if (showBOLL && boll20) {
    series.push({
      name: 'BOLL上轨',
      type: 'line',
      data: boll20.upper,
      smooth: false,
      symbol: 'none',
      lineStyle: { width: 1, color: '#f56c6c', type: 'dashed' }
    })
    series.push({
      name: 'BOLL中轨',
      type: 'line',
      data: boll20.middle,
      smooth: false,
      symbol: 'none',
      lineStyle: { width: 1, color: '#303133' }
    })
    series.push({
      name: 'BOLL下轨',
      type: 'line',
      data: boll20.lower,
      smooth: false,
      symbol: 'none',
      lineStyle: { width: 1, color: '#67c23a', type: 'dashed' }
    })
  }

  // 成交量（柱状图在最底部）
  const hasVolume = volumes.some(v => v > 0)
  if (hasVolume) {
    series.push({
      name: '成交量',
      type: 'bar',
      data: volumes.map((v, i) => ({
        value: v,
        itemStyle: {
          color: changes[i] >= 0 ? 'rgba(245, 108, 108, 0.4)' : 'rgba(103, 194, 58, 0.4)'
        }
      })),
      yAxisIndex: 1,
      barWidth: '60%'
    })
  }

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#dcdfe6',
      borderWidth: 1,
      padding: [10, 14],
      formatter: function (params: any[]) {
        if (!params || params.length === 0) return ''
        const date = params[0].axisValue
        const dataItem = historyList.value.find(d => d.tradeDate === date)
        let html = `<div style="font-weight:600;margin-bottom:6px">${date}</div>`
        params.forEach((p: any) => {
          if (p.value !== null && p.value !== undefined && !isNaN(p.value)) {
            html += `<div>${p.marker} ${p.seriesName}: ${p.value}</div>`
          }
        })
        if (dataItem) {
          html += `<div style="margin-top:4px;padding-top:4px;border-top:1px solid #eee;font-size:12px;color:#909399">`
          html += `开盘: ${formatPrice(dataItem.openPrice)} | 最高: ${formatPrice(dataItem.high)} | 最低: ${formatPrice(dataItem.low)}</div>`
        }
        return html
      }
    },
    legend: {
      data: ['价格', ...(showMA ? ['MA5', 'MA10', 'MA20'] : []), ...(showBOLL ? ['BOLL上轨', 'BOLL中轨', 'BOLL下轨'] : []), ...(hasVolume ? ['成交量'] : [])],
      top: 0,
      left: 'center',
      itemWidth: 16,
      itemHeight: 10,
      textStyle: { fontSize: 11 }
    },
    grid: [
      { left: '5%', right: '5%', top: '12%', height: '60%' },
      ...(hasVolume ? [{ left: '5%', right: '5%', top: '78%', height: '15%' }] : [])
    ],
    xAxis: [
      {
        type: 'category',
        data: dates,
        boundaryGap: false,
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: {
          rotate: 30,
          fontSize: 11,
          color: '#909399',
          formatter: (v: string) => v ? v.slice(5) : ''
        },
        splitLine: { show: true, lineStyle: { type: 'dashed', color: '#f0f0f0' } }
      },
      ...(hasVolume ? [{
        type: 'category',
        data: dates,
        boundaryGap: true,
        gridIndex: 1,
        axisLabel: { show: false },
        axisLine: { show: false },
        axisTick: { show: false }
      }] : [])
    ],
    yAxis: [
      {
        type: 'value',
        scale: true,
        splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } },
        axisLabel: {
          fontSize: 11,
          color: '#909399',
          formatter: (v: number) => formatPrice(v)
        }
      },
      ...(hasVolume ? [{
        type: 'value',
        scale: true,
        gridIndex: 1,
        splitLine: { show: false },
        axisLabel: { show: false }
      }] : [])
    ],
    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: [0],
        start: 0,
        end: 100
      }
    ],
    series
  }
}

function calcMA(data: number[], period: number): (number | null)[] {
  return data.map((_, i) => {
    if (i < period - 1) return null
    let sum = 0
    for (let j = i - period + 1; j <= i; j++) {
      sum += data[j]
    }
    return parseFloat((sum / period).toFixed(4))
  })
}

function calcBOLL(data: number[], period: number) {
  if (data.length < period) return null
  const middle = calcMA(data, period)
  const upper: (number | null)[] = []
  const lower: (number | null)[] = []
  data.forEach((_, i) => {
    if (i < period - 1 || middle[i] === null) {
      upper.push(null)
      lower.push(null)
      return
    }
    let sumSq = 0
    for (let j = i - period + 1; j <= i; j++) {
      sumSq += (data[j] - middle[i]!) * (data[j] - middle[i]!)
    }
    const std = Math.sqrt(sumSq / period)
    upper.push(parseFloat((middle[i]! + 2 * std).toFixed(4)))
    lower.push(parseFloat((middle[i]! - 2 * std).toFixed(4)))
  })
  return { upper, middle, lower }
}

function renderKlineChart() {
  if (!klineChartRef.value) return
  if (!klineChart) {
    klineChart = echarts.init(klineChartRef.value)
  }
  const option = buildKlineOption()
  if (option) {
    klineChart.setOption(option, true)
  }
  klineChart?.resize()
}

function onPeriodChange() {
  loadHistory().then(() => {
    nextTick(renderKlineChart)
  })
}

// ==================== AI分析 ====================
async function loadAiAnalysis() {
  if (!productCode.value || !hasAiConfig.value) return
  aiLoading.value = true
  aiResult.value = null
  try {
    const configsRes = await getModelConfigs()
    const configs = configsRes.data?.data || []
    const enabledConfig = configs.find((c: any) => c.enabled) || configs[0]
    if (!enabledConfig) {
      hasAiConfig.value = false
      return
    }
    const res = await analyzeStock({
      stockCode: productCode.value,
      configId: enabledConfig.id,
      customPrompt: `请分析黄金产品 ${currentProductName.value}（代码：${productCode.value}）的走势和投资建议`
    })
    aiResult.value = res.data?.data || null
  } catch (e: any) {
    console.error('AI分析失败:', e)
    aiResult.value = null
    if (e.message?.includes('模型配置')) {
      hasAiConfig.value = false
    }
  } finally {
    aiLoading.value = false
  }
}

async function checkAiConfig() {
  try {
    const res = await getModelConfigs()
    const configs = res.data?.data || []
    hasAiConfig.value = configs.length > 0
  } catch {
    hasAiConfig.value = false
  }
}

// ==================== 工具方法 ====================
function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

function getScoreColor(score: number) {
  if (score >= 70) return '#f56c6c'
  if (score >= 50) return '#e6a23c'
  return '#909399'
}

function getAiSignalType(signal: string) {
  if (signal === 'BUY') return 'danger'
  if (signal === 'SELL') return 'success'
  return 'info'
}

function getAiSignalText(signal: string) {
  if (signal === 'BUY') return '买入'
  if (signal === 'SELL') return '卖出'
  return '观望'
}

function getConsensusTagType(c: string) {
  if (c === 'bullish') return 'danger'
  if (c === 'bearish') return 'success'
  return 'info'
}

function getConsensusText(c: string) {
  if (c === 'bullish') return '多数看涨'
  if (c === 'bearish') return '多数看跌'
  return '多数中性'
}

function getDavTagType(t: string) {
  if (t === 'bullish') return 'danger'
  if (t === 'bearish') return 'success'
  return 'info'
}

function getDavText(t: string) {
  if (t === 'bullish') return '看涨'
  if (t === 'bearish') return '看跌'
  return '中性'
}

function showDavDetail(op: any) {
  const infl = Math.min(5, Math.max(0, op.influence || 3))
  const stars = '★'.repeat(infl) + '☆'.repeat(5 - infl)
  const timeStr = op.publishTime ? `<div style="margin-top:6px;font-size:12px;color:#909399">发布时间：${op.publishTime}</div>` : ''
  ElMessageBox({
    title: `${op.name} — 详细观点`,
    message: `<div style="margin-bottom:12px">
      <el-tag type="${getDavTagType(op.type)}" effect="dark">${getDavText(op.type)}</el-tag>
      <span style="margin-left:8px;font-weight:600;font-size:16px">${op.view}</span>
    </div>
    <div style="font-size:14px;line-height:1.8;color:#303133">${op.detail}</div>
    ${timeStr}
    <div style="margin-top:12px;font-size:12px;color:#909399">
      影响力：${stars}
      <br/>来源：AI基于公开财经媒体报道和市场观点综合生成
    </div>`,
    dangerouslyUseHTMLString: true,
    confirmButtonText: '关闭'
  })
}

// ==================== 生命周期 ====================
onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([
      loadLatestPrice(),
      loadHistory(),
      checkAiConfig()
    ])
    await loadSignals()
    await nextTick()
    renderKlineChart()
  } catch (e: any) {
    ElMessage.error(e.message || '加载黄金数据失败')
  } finally {
    loading.value = false
  }
})

// 监听产品切换
watch(productCode, async (newCode) => {
  if (!newCode) return
  aiResult.value = null
  loading.value = true
  try {
    await Promise.all([
      loadLatestPrice(),
      loadHistory()
    ])
    await loadSignals()
    await nextTick()
    renderKlineChart()
    if (hasAiConfig.value) {
      loadAiAnalysis()
    }
  } catch (e: any) {
    ElMessage.error(e.message || '切换产品失败')
  } finally {
    loading.value = false
  }
})

// 监听技术指标切换 -> 重绘K线图
watch(klineIndicators, () => {
  nextTick(renderKlineChart)
}, { deep: true })

// 窗口resize
function onResize() {
  klineChart?.resize()
}
window.addEventListener('resize', onResize)

onBeforeUnmount(() => {
  klineChart?.dispose()
  klineChart = null
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped lang="scss">
.gold-page {
  max-width: 1400px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}

// ============= 产品切换栏 =============
.switcher-card {
  border-radius: 8px;
  margin-bottom: 12px;
  border-left: 3px solid #e6a23c;

  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}

.switcher-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;

  .switcher-label {
    font-size: 14px;
    font-weight: 500;
    color: #606266;
    white-space: nowrap;
  }
}

// ============= 价格卡片 =============
.price-card {
  border-radius: 10px;
  margin-bottom: 14px;
  border-left: 4px solid #e6a23c;

  :deep(.el-card__body) {
    padding: 24px;
  }
}

.price-main {
  display: flex;
  align-items: center;
  gap: 40px;

  @media (max-width: 768px) {
    flex-direction: column;
    gap: 20px;
    align-items: flex-start;
  }
}

.price-info {
  text-align: center;
  min-width: 240px;

  .price-label {
    font-size: 14px;
    color: #909399;
    margin-bottom: 8px;
  }

  .price-value {
    font-size: 42px;
    font-weight: 700;
    color: #303133;
    line-height: 1.2;
    margin-bottom: 10px;
  }

  .price-change {
    .change-badge {
      display: inline-block;
      padding: 4px 16px;
      border-radius: 20px;
      font-size: 16px;
      font-weight: 600;

      &.up {
        background: rgba(245, 108, 108, 0.1);
        color: #f56c6c;
      }

      &.down {
        background: rgba(103, 194, 58, 0.1);
        color: #67c23a;
      }
    }
  }
}

.price-details {
  flex: 1;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  background: #f5fcf8;
  border-radius: 8px;

  .detail-label {
    font-size: 12px;
    color: #909399;
  }

  .detail-value {
    font-size: 17px;
    font-weight: 600;
    color: #303133;

    &.text-danger {
      color: #f56c6c;
    }

    &.text-success {
      color: #67c23a;
    }

    &.code-value {
      font-size: 14px;
      color: #606266;
    }
  }
}

// ============= K线图 =============
.chart-card {
  border-radius: 8px;
  margin-bottom: 14px;
}

.kline-chart {
  width: 100%;
  height: 480px;
}

// ============= 技术面 =============
.signal-card {
  border-radius: 8px;
  margin-bottom: 14px;
}

// ============= 通用卡片头部 =============
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

// ============= AI分析区 =============
.ai-card {
  border-radius: 8px;
  margin-bottom: 14px;
}

.ai-score-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: #fafbfc;
  border-radius: 8px;
  margin-bottom: 20px;
}

.ai-signal {
  display: flex;
  align-items: center;
  gap: 12px;
  .label { font-size: 14px; color: #606266; font-weight: 500; }
}

.ai-scores {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-score-item {
  .label { font-size: 13px; color: #909399; margin-bottom: 4px; display: block; }
}

.ai-target {
  display: flex;
  align-items: center;
  gap: 8px;
  .label { font-size: 14px; color: #606266; }
  .value { font-size: 16px; font-weight: 600; color: #e6a23c; }
}

.ai-model {
  font-size: 12px; color: #909399;
}

.ai-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.section-title-inner {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 14px 0;
}

// 共识卡片
.consensus-card {
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
  border-left: 4px solid #909399;
  background: #f5f7fa;

  &.consensus-bullish { border-left-color: #f56c6c; background: rgba(245,108,108,0.05); }
  &.consensus-bearish { border-left-color: #67c23a; background: rgba(103,194,58,0.05); }
}

.consensus-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;

  .consensus-summary {
    font-size: 14px;
    color: #303133;
    font-weight: 500;
  }
}

.consensus-counts {
  display: flex;
  gap: 20px;

  .count {
    font-size: 13px;
    font-weight: 500;
    &.bullish { color: #f56c6c; }
    &.bearish { color: #67c23a; }
    &.neutral { color: #909399; }
  }
}

// 大V卡片网格
.dav-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.dav-card {
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-left: 3px solid #909399;
  transition: box-shadow 0.2s;
  cursor: pointer;

  &:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.12); }
  &.dav-bullish { border-left-color: #f56c6c; }
  &.dav-bearish { border-left-color: #67c23a; }
}

.dav-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid #f0f0f0;
}

.source-btn {
  font-size: 12px;
}

.dav-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dav-time {
  font-size: 11px;
  color: #a0a0a0;
}

.source-tip {
  margin-left: 8px;
  vertical-align: middle;
}

.dav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;

  .dav-name { font-size: 14px; font-weight: 600; color: #303133; }
}

.dav-view { font-size: 13px; color: #303133; font-weight: 500; margin-bottom: 4px; }
.dav-detail { font-size: 12px; color: #606266; line-height: 1.5; margin-bottom: 6px; }

.dav-influence {
  font-size: 12px; color: #909399;
  .star { color: #dcdfe6; &.active { color: #e6a23c; } }
}

// Markdown渲染
.markdown-body {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;

  :deep(h2) { font-size: 18px; margin: 16px 0 8px; }
  :deep(h3) { font-size: 16px; margin: 14px 0 6px; }
  :deep(h4) { font-size: 15px; margin: 12px 0 6px; }
  :deep(p) { margin: 8px 0; }
  :deep(ul) { padding-left: 20px; margin: 6px 0; }
  :deep(li) { margin: 4px 0; }
  :deep(strong) { font-weight: 600; }
}

// Loading
.ai-loading {
  text-align: center;
  padding: 40px;
  color: #909399;

  .el-icon { margin-bottom: 12px; }
  p { font-size: 14px; }
}

.no-config-hint {
  margin-top: 12px;
}

.text-danger {
  color: #f56c6c !important;
}

.text-success {
  color: #67c23a !important;
}
</style>
