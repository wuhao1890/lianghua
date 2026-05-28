<template>
  <div class="stock-detail" v-loading="loading">
    <!-- 返回导航 -->
    <div class="back-nav">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回行情中心
      </el-button>
    </div>

    <!-- 股票基本信息 -->
    <el-card shadow="hover" class="info-card">
      <div class="stock-info">
        <div class="info-left">
          <div class="stock-title">
            <h2>{{ stockInfo?.name || '-' }}</h2>
            <span class="stock-code">{{ stockInfo?.code || '-' }}</span>
            <el-tag size="small" type="info">{{ stockInfo?.market === 'A' ? 'A股' : '美股' }}</el-tag>
          </div>
          <div class="price-row">
            <span class="current-price" :style="{ color: getColor(stockInfo?.changePercent || 0) }">
              {{ formatPrice(stockInfo?.currentPrice) }}
            </span>
            <span class="change-info" :style="{ color: getColor(stockInfo?.changePercent || 0) }">
              {{ (stockInfo?.changeAmount || 0) >= 0 ? '+' : '' }}{{ formatPrice(stockInfo?.changeAmount) }}
              ({{ formatPercent(stockInfo?.changePercent) }})
            </span>
          </div>
        </div>
        <div class="info-right">
          <div class="info-grid">
            <div class="info-item">
              <span class="label">今开</span>
              <span class="value">{{ formatPrice(stockInfo?.openPrice) }}</span>
            </div>
            <div class="info-item">
              <span class="label">最高</span>
              <span class="value text-danger">{{ formatPrice(stockInfo?.highPrice) }}</span>
            </div>
            <div class="info-item">
              <span class="label">昨收</span>
              <span class="value">{{ formatPrice(stockInfo?.closePrice) }}</span>
            </div>
            <div class="info-item">
              <span class="label">最低</span>
              <span class="value text-success">{{ formatPrice(stockInfo?.lowPrice) }}</span>
            </div>
            <div class="info-item">
              <span class="label">成交量</span>
              <span class="value">{{ formatNumber(stockInfo?.volume) }}</span>
            </div>
            <div class="info-item">
              <span class="label">成交额</span>
              <span class="value">{{ formatNumber(stockInfo?.turnover) }}</span>
            </div>
            <div class="info-item">
              <span class="label">换手率</span>
              <span class="value">{{ stockInfo?.turnoverRate ? stockInfo.turnoverRate.toFixed(2) + '%' : '-' }}</span>
            </div>
            <div class="info-item">
              <span class="label">市盈率</span>
              <span class="value">{{ stockInfo?.pe ? stockInfo.pe.toFixed(2) : '-' }}</span>
            </div>
          </div>
          <div class="action-buttons">
            <el-button type="danger" size="large" @click="handleTrade('BUY')">
              <el-icon><Top /></el-icon> 买入
            </el-button>
            <el-button type="success" size="large" @click="handleTrade('SELL')">
              <el-icon><Bottom /></el-icon> 卖出
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- K线图 -->
    <el-card shadow="hover" class="chart-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">K线图</span>
            <el-radio-group v-model="klinePeriod" size="small" @change="loadKlineData">
              <el-radio-button label="daily">日K</el-radio-button>
              <el-radio-button label="weekly">周K</el-radio-button>
              <el-radio-button label="monthly">月K</el-radio-button>
            </el-radio-group>
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
      <KlineChart
        :kline-data="klineData"
        :indicators="activeIndicators"
        height="500px"
      />
    </el-card>

    <!-- 技术面买卖信号 -->
    <el-card shadow="hover" class="signal-card">
      <template #header>
        <span class="title">技术面买卖信号</span>
      </template>
      <SignalIndicator :signals="signals" :stock-code="stockCode" />
    </el-card>

    <!-- AI 智能研判（含大V舆情 + 详细分析） -->
    <el-card shadow="hover" class="ai-card">
      <template #header>
        <div class="card-header">
          <span class="title">AI 智能综合研判（含大V舆情）</span>
          <el-button
            v-if="stockCode"
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
          <h4 class="section-title">
            <span>🗣️ 大V舆情分析（占综合评分的50%）</span>
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
                    影响力：<span v-for="s in 5" :key="s" class="star" :class="{ active: s <= (op.influence || 3) }">★</span></span>
                </div>
                <el-button text size="small" type="primary" class="source-btn">查看来源 →</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 详细分析 -->
        <div class="ai-section" v-if="aiResult.analysis">
          <h4 class="section-title"><span>📊 综合操作建议</span></h4>
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Monitor, Loading } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useStockStore } from '@/store/stock'
import { getTradeSignal } from '@/api/analysis'
import { getModelConfigs, analyzeStock } from '@/api/ai'
import { formatPrice, formatPercent, formatNumber, getColor } from '@/utils/format'
import KlineChart from '@/components/KlineChart.vue'
import SignalIndicator from '@/components/SignalIndicator.vue'
import type { KlineData, TradeSignal, AiAnalysisResponse } from '@/types'

const route = useRoute()
const router = useRouter()
const stockStore = useStockStore()

const stockCode = computed(() => route.params.code as string)
const loading = ref(false)
const klinePeriod = ref('daily')
const activeIndicators = ref<string[]>(['MA'])
const klineData = ref<KlineData | null>(null)
const signals = ref<TradeSignal[]>([])
const aiResult = ref<AiAnalysisResponse | null>(null)
const aiLoading = ref(false)
const hasAiConfig = ref(false)

const stockInfo = computed(() => stockStore.currentStock)

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

// ==================== AI分析 ====================
async function loadAiAnalysis() {
  if (!stockCode.value || !hasAiConfig.value) return
  aiLoading.value = true
  aiResult.value = null
  try {
    // 获取启用的模型配置
    const configsRes = await getModelConfigs()
    const configs = configsRes.data?.data || []
    const enabledConfig = configs.find((c: any) => c.enabled) || configs[0]
    if (!enabledConfig) {
      hasAiConfig.value = false
      return
    }

    const res = await analyzeStock({
      stockCode: stockCode.value,
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

// ==================== 原功能 ====================
function handleTrade(direction: string) {
  router.push({ path: '/trade', query: { code: stockCode.value, direction } })
}

function goBack() {
  router.push('/market')
}

async function loadKlineData() {
  if (!stockCode.value) return
  try {
    const data = await stockStore.getKlineData(stockCode.value, klinePeriod.value)
    klineData.value = data
  } catch (e) {
    console.error('加载K线数据失败:', e)
  }
}

async function loadSignals() {
  if (!stockCode.value) return
  try {
    const res = await getTradeSignal(stockCode.value)
    signals.value = res.data.data || null
  } catch (e) {
    console.error('加载买卖信号失败:', e)
    setTimeout(async () => {
      try {
        const res = await getTradeSignal(stockCode.value!)
        signals.value = res.data.data || null
      } catch {
        signals.value = null
      }
    }, 3000)
  }
}

// ==================== 工具方法 ====================
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

function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
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

onMounted(async () => {
  loading.value = true
  try {
    // 先加载基本数据（股票信息、K线、技术面信号）后再关闭loading
    await Promise.all([
      stockStore.getStockDetail(stockCode.value),
      loadKlineData(),
      checkAiConfig()
    ])
    await loadSignals()
  } catch (e) {
    console.error('加载股票详情失败:', e)
  } finally {
    loading.value = false  // 释放页面，用户可以滚动和操作
  }
  // 在页面可操作后，后台自动发起AI分析（不阻塞页面）
  if (hasAiConfig.value && stockCode.value) {
    loadAiAnalysis()
  }
})

watch(stockCode, async () => {
  if (stockCode.value) {
    aiResult.value = null
    await loadKlineData()
    loadSignals()
    // 切换股票时在后台自动分析，不阻塞
    if (hasAiConfig.value) {
      loadAiAnalysis()
    }
  }
})
</script>

<style scoped lang="scss">
.stock-detail {
  max-width: 1400px;
}

.back-nav {
  margin-bottom: 8px;
}

.info-card, .chart-card, .signal-card, .ai-card {
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

.title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

// ============= 股票信息 =============
.stock-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 20px;
}

.info-left {
  flex: 1;
  min-width: 250px;
}

.stock-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;

  h2 { font-size: 24px; color: #303133; margin: 0; }
  .stock-code { font-size: 14px; color: #909399; }
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.current-price { font-size: 32px; font-weight: 700; }
.change-info { font-size: 16px; font-weight: 500; }

.info-right {
  flex: 2;
  min-width: 400px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 6px;

  .label { font-size: 12px; color: #909399; margin-bottom: 4px; }
  .value { font-size: 14px; font-weight: 500; color: #303133; }
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  .el-button { min-width: 120px; }
}

.text-danger { color: #f56c6c !important; }
.text-success { color: #67c23a !important; }

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
