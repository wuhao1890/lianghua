<template>
  <div class="fund-detail" v-loading="loading">
    <!-- 返回导航 -->
    <div class="back-nav">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回基金列表
      </el-button>
    </div>

    <!-- 基金基本信息 -->
    <el-card shadow="hover" class="info-card">
      <div class="fund-info">
        <div class="info-left">
          <div class="fund-title">
            <h2>{{ fundInfo?.name || '-' }}</h2>
            <span class="fund-code">{{ fundInfo?.code || '-' }}</span>
            <el-tag size="small" :type="getFundTypeTagType(fundInfo?.fundType || '')" effect="plain">
              {{ fundInfo?.fundType || '-' }}
            </el-tag>
          </div>
          <div class="nav-row">
            <div class="nav-main">
              <span class="nav-label">单位净值</span>
              <span class="nav-value" :style="{ color: getColor(fundInfo?.changePercent || 0) }">
                {{ formatPrice(fundInfo?.nav) }}
              </span>
            </div>
            <div class="nav-change" :style="{ color: getColor(fundInfo?.changePercent || 0) }">
              {{ formatPercent(fundInfo?.changePercent) }}
            </div>
          </div>
        </div>
        <div class="info-right">
          <div class="info-grid">
            <div class="info-item">
              <span class="label">累计净值</span>
              <span class="value">{{ formatPrice(fundInfo?.accNav) }}</span>
            </div>
            <div class="info-item">
              <span class="label">净值日期</span>
              <span class="value">{{ fundInfo?.navDate || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="label">基金类型</span>
              <span class="value">
                <el-tag size="small" :type="getFundTypeTagType(fundInfo?.fundType || '')" effect="plain">
                  {{ fundInfo?.fundType || '-' }}
                </el-tag>
              </span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 净值走势图 -->
    <el-card shadow="hover" class="chart-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">净值走势</span>
            <el-radio-group v-model="navPeriod" size="small" @change="loadNavHistory">
              <el-radio-button label="1M">1月</el-radio-button>
              <el-radio-button label="3M">3月</el-radio-button>
              <el-radio-button label="6M">6月</el-radio-button>
              <el-radio-button label="1Y">1年</el-radio-button>
            </el-radio-group>
          </div>
          <div class="header-right">
            <el-checkbox-group v-model="activeIndicators" size="small">
              <el-checkbox-button label="MA">MA</el-checkbox-button>
              <el-checkbox-button label="RSI">RSI</el-checkbox-button>
              <el-checkbox-button label="KDJ">KDJ</el-checkbox-button>
              <el-checkbox-button label="BOLL">BOLL</el-checkbox-button>
            </el-checkbox-group>
          </div>
        </div>
      </template>
      <div ref="navChartRef" class="nav-chart"></div>
    </el-card>

    <!-- 技术面分析 -->
    <el-card shadow="hover" class="signal-card" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span class="title">技术面买卖信号</span>
          <el-tag size="small" type="info" effect="plain">基于净值走势分析</el-tag>
        </div>
      </template>
      <SignalIndicator :signals="signals" :stock-code="fundCode" />
    </el-card>

    <!-- AI 智能研判（含大V舆情） -->
    <el-card shadow="hover" class="ai-card">
      <template #header>
        <div class="card-header">
          <span class="title">AI 智能综合研判（含大V舆情）</span>
          <el-button
            v-if="fundCode"
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
            <span class="label">目标净值：</span>
            <span class="value">{{ aiResult.targetPrice }}</span>
          </div>
          <div class="ai-model">
            分析模型：<el-tag size="small">{{ aiResult.modelUsed || '-' }}</el-tag>
          </div>
        </div>

        <!-- 大V舆情 -->
        <div class="ai-section" v-if="aiResult.daVMajority">
          <h4 class="section-title">
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
          <h4 class="section-title"><span>综合操作建议</span></h4>
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
import { ref, computed, onMounted, watch, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Monitor, Loading } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { getFundDetail, getFundNavHistory } from '@/api/fund'
import type { FundInfo, FundNavRecord } from '@/api/fund'
import { getModelConfigs, analyzeStock } from '@/api/ai'
import { formatPrice, formatPercent, getColor } from '@/utils/format'
import type { AiAnalysisResponse } from '@/types'
import SignalIndicator from '@/components/SignalIndicator.vue'
import { getTradeSignal } from '@/api/analysis'

const route = useRoute()
const router = useRouter()

const fundCode = computed(() => route.params.code as string)

const loading = ref(false)
const fundInfo = ref<FundInfo | null>(null)
const navHistory = ref<FundNavRecord[]>([])
const navPeriod = ref('1M')
const activeIndicators = ref<string[]>(['MA'])
const aiResult = ref<AiAnalysisResponse | null>(null)
const aiLoading = ref(false)
const hasAiConfig = ref(false)
const signals = ref<any>(null)

// ============ ECharts NAV Chart ============
const navChartRef = ref<HTMLElement>()
let navChart: echarts.ECharts | null = null

function getPeriodDays(): number {
  switch (navPeriod.value) {
    case '1M': return 30
    case '3M': return 90
    case '6M': return 180
    case '1Y': return 365
    default: return 30
  }
}

function buildNavChart() {
  if (!navChartRef.value || !navHistory.value.length) return

  if (!navChart) {
    navChart = echarts.init(navChartRef.value)
  }

  const records = navHistory.value.slice().reverse()
  const dates = records.map(r => r.date)
  const navValues = records.map(r => r.nav)
  const accNavValues = records.map(r => r.accNav)

  const series: any[] = [
    {
      name: '单位净值',
      type: 'line',
      data: navValues,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: '#33b86a' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(51,184,106,0.3)' },
          { offset: 1, color: 'rgba(51,184,106,0.02)' }
        ])
      }
    },
    {
      name: '累计净值',
      type: 'line',
      data: accNavValues,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: '#409eff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.2)' },
          { offset: 1, color: 'rgba(64,158,255,0.02)' }
        ])
      }
    }
  ]

  // 技术指标辅助线 (simplified)
  if (activeIndicators.value.includes('MA')) {
    const ma5 = navValues.map((_, i, arr) => {
      if (i < 4) return null
      const sum = arr.slice(i - 4, i + 1).reduce((a, b) => a + b, 0)
      return +(sum / 5).toFixed(4)
    })
    const ma10 = navValues.map((_, i, arr) => {
      if (i < 9) return null
      const sum = arr.slice(i - 9, i + 1).reduce((a, b) => a + b, 0)
      return +(sum / 10).toFixed(4)
    })
    series.push({
      name: 'MA5',
      type: 'line',
      data: ma5,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 1, color: '#e6a23c', type: 'dashed' }
    })
    series.push({
      name: 'MA10',
      type: 'line',
      data: ma10,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 1, color: '#f56c6c', type: 'dashed' }
    })
  }

  if (activeIndicators.value.includes('BOLL')) {
    const period = 20
    const bollMid = navValues.map((_, i, arr) => {
      if (i < period - 1) return null
      const slice = arr.slice(i - period + 1, i + 1)
      return +(slice.reduce((a, b) => a + b, 0) / period).toFixed(4)
    })
    const bollUpper = bollMid.map((mid, i) => {
      if (mid === null || i < period - 1) return null
      const slice = navValues.slice(i - period + 1, i + 1)
      const mean = slice.reduce((a, b) => a + b, 0) / period
      const variance = slice.reduce((sum, v) => sum + (v - mean) ** 2, 0) / period
      const std = Math.sqrt(variance)
      return +(mean + 2 * std).toFixed(4)
    })
    const bollLower = bollMid.map((mid, i) => {
      if (mid === null || i < period - 1) return null
      const slice = navValues.slice(i - period + 1, i + 1)
      const mean = slice.reduce((a, b) => a + b, 0) / period
      const variance = slice.reduce((sum, v) => sum + (v - mean) ** 2, 0) / period
      const std = Math.sqrt(variance)
      return +(mean - 2 * std).toFixed(4)
    })
    series.push({
      name: 'BOLL上轨',
      type: 'line',
      data: bollUpper,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 1, color: '#909399', type: 'dotted' }
    })
    series.push({
      name: 'BOLL中轨',
      type: 'line',
      data: bollMid,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 1, color: '#909399', type: 'dashed' }
    })
    series.push({
      name: 'BOLL下轨',
      type: 'line',
      data: bollLower,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 1, color: '#909399', type: 'dotted' }
    })
  }

  navChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#e4e7ed',
      textStyle: { color: '#303133', fontSize: 12 },
      formatter: (params: any) => {
        let html = `<div style="font-weight:600;margin-bottom:6px">${params[0].axisValue}</div>`
        params.forEach((p: any) => {
          if (p.value !== null && p.value !== undefined) {
            html += `<div style="display:flex;justify-content:space-between;gap:20px">
              <span>${p.marker} ${p.seriesName}</span>
              <b>${p.value}</b>
            </div>`
          }
        })
        return html
      }
    },
    legend: {
      data: series.map(s => s.name),
      top: 0,
      textStyle: { fontSize: 12 }
    },
    grid: { left: 60, right: 30, top: 40, bottom: 60 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        color: '#909399',
        fontSize: 11,
        rotate: dates.length > 60 ? 45 : 0
      },
      axisLine: { lineStyle: { color: '#e4e7ed' } }
    },
    yAxis: {
      type: 'value',
      name: '净值',
      nameTextStyle: { color: '#909399', fontSize: 11 },
      axisLabel: { color: '#909399' },
      splitLine: { lineStyle: { color: '#f0f2f5' } },
      scale: true
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', start: 0, end: 100, bottom: 5, height: 20 }
    ],
    series
  })
}

function handleResize() {
  navChart?.resize()
}

// ============ 数据加载 ============
async function loadFundDetail() {
  if (!fundCode.value) return
  try {
    const res = await getFundDetail(fundCode.value)
    fundInfo.value = res.data?.data || null
  } catch (e) {
    console.error('加载基金详情失败:', e)
  }
}

async function loadNavHistory() {
  if (!fundCode.value) return
  try {
    const res = await getFundNavHistory(fundCode.value, getPeriodDays())
    const data = res.data?.data
    if (Array.isArray(data)) {
      navHistory.value = data as FundNavRecord[]
    } else if (data?.list) {
      navHistory.value = data.list as FundNavRecord[]
    } else {
      navHistory.value = []
    }
  } catch (e) {
    console.error('加载净值历史失败:', e)
    navHistory.value = []
  }
  await nextTick()
  buildNavChart()
}

// ============ AI分析 ============
async function loadSignals() {
  try {
    const res = await getTradeSignal(fundCode.value || '000001')
    signals.value = res.data?.data || null
  } catch {}
}

async function loadAiAnalysis() {
  if (!fundCode.value || !hasAiConfig.value) return
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
      stockCode: fundCode.value,
      configId: enabledConfig.id
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

// ============ 工具方法 ============
function goBack() {
  router.push('/funds')
}

function getFundTypeTagType(fundType: string): string {
  const map: Record<string, string> = {
    '股票型': 'danger',
    '混合型': 'warning',
    '债券型': 'success',
    '货币型': 'primary',
    '指数型': '',
    'QDII': 'info'
  }
  return map[fundType] || ''
}

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

// Markdown rendering
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

// ============ 生命周期 ============
onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([
      loadFundDetail(),
      checkAiConfig(),
      loadSignals()
    ])
    await loadNavHistory()
  } catch (e) {
    console.error('加载基金详情失败:', e)
  } finally {
    loading.value = false
  }
  if (hasAiConfig.value && fundCode.value) {
    loadAiAnalysis()
  }
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  navChart?.dispose()
})

watch(fundCode, async () => {
  if (fundCode.value) {
    loading.value = true
    aiResult.value = null
    try {
      await loadFundDetail()
      await loadNavHistory()
    } finally {
      loading.value = false
    }
    if (hasAiConfig.value) {
      loadAiAnalysis()
    }
  }
})

watch(activeIndicators, () => {
  nextTick(() => buildNavChart())
}, { deep: true })
</script>

<style scoped lang="scss">
.fund-detail {
  max-width: 1400px;
}

.back-nav {
  margin-bottom: 8px;
}

.info-card, .chart-card, .ai-card {
  border-radius: 8px;
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

// ============= 基金信息 =============
.fund-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 20px;
}

.info-left {
  flex: 1;
  min-width: 280px;
}

.fund-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;

  h2 { font-size: 24px; color: #303133; margin: 0; }
  .fund-code { font-size: 14px; color: #909399; }
}

.nav-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.nav-main {
  display: flex;
  align-items: baseline;
  gap: 10px;

  .nav-label {
    font-size: 13px;
    color: #909399;
  }

  .nav-value {
    font-size: 30px;
    font-weight: 700;
  }
}

.nav-change {
  font-size: 18px;
  font-weight: 600;
}

.info-right {
  flex: 1;
  min-width: 280px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.info-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 6px;

  .label { font-size: 12px; color: #909399; margin-bottom: 6px; }
  .value { font-size: 14px; font-weight: 600; color: #303133; }
}

// ============= NAV Chart =============
.nav-chart {
  width: 100%;
  height: 480px;
}

// ============= AI分析区 =============
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

.section-title {
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
</style>
