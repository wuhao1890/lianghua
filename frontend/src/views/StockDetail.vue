<template>
  <div class="stock-detail" v-loading="loading">
    <div class="back-nav">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回行情中心
      </el-button>
    </div>

    <el-alert
      v-if="!stockInfo"
      title="未获取到该股票的真实实时行情，可能已退市、停牌或代码无效；系统不会使用数据库旧行情或模拟数据。"
      type="warning"
      :closable="false"
      show-icon
      class="real-data-alert"
    />

    <el-card shadow="hover" class="info-card">
      <div class="stock-info">
        <div class="info-left">
          <div class="stock-title">
            <h2>{{ stockInfo?.name || '-' }}</h2>
            <span class="stock-code">{{ stockInfo?.code || stockCode }}</span>
            <el-tag size="small" type="info">{{ stockInfo?.market === 'US' ? '美股' : 'A股' }}</el-tag>
          </div>
          <div class="price-row">
            <span class="current-price" :style="{ color: getColor(stockInfo?.changePercent || 0) }">
              {{ stockInfo ? formatPrice(stockInfo.currentPrice) : '-' }}
            </span>
            <span v-if="stockInfo" class="change-info" :style="{ color: getColor(stockInfo.changePercent || 0) }">
              {{ (stockInfo?.changeAmount || 0) >= 0 ? '+' : '' }}{{ formatPrice(stockInfo?.changeAmount) }}
              ({{ formatPercent(stockInfo?.changePercent) }})
            </span>
          </div>
        </div>

        <div class="info-right">
          <div class="info-grid">
            <div class="info-item"><span class="label">今开</span><span class="value">{{ stockInfo ? formatPrice(stockInfo.openPrice) : '-' }}</span></div>
            <div class="info-item"><span class="label">最高</span><span class="value text-danger">{{ stockInfo ? formatPrice(stockInfo.highPrice) : '-' }}</span></div>
            <div class="info-item"><span class="label">昨收</span><span class="value">{{ stockInfo ? formatPrice(stockInfo.closePrice) : '-' }}</span></div>
            <div class="info-item"><span class="label">最低</span><span class="value text-success">{{ stockInfo ? formatPrice(stockInfo.lowPrice) : '-' }}</span></div>
            <div class="info-item"><span class="label">成交量</span><span class="value">{{ stockInfo ? formatNumber(stockInfo.volume) : '-' }}</span></div>
            <div class="info-item"><span class="label">成交额</span><span class="value">{{ stockInfo ? formatNumber(stockInfo.turnover) : '-' }}</span></div>
            <div class="info-item"><span class="label">换手率</span><span class="value">{{ stockInfo?.turnoverRate ? stockInfo.turnoverRate.toFixed(2) + '%' : '-' }}</span></div>
            <div class="info-item"><span class="label">市盈率</span><span class="value">{{ stockInfo?.pe ? stockInfo.pe.toFixed(2) : '-' }}</span></div>
          </div>
          <div v-if="stockInfo" class="action-buttons">
            <el-button type="danger" size="large" @click="handleTrade('BUY')">
              <el-icon><Top /></el-icon>
              买入
            </el-button>
            <el-button type="success" size="large" @click="handleTrade('SELL')">
              <el-icon><Bottom /></el-icon>
              卖出
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <section class="detail-nav">
      <button v-for="item in detailNav" :key="item.id" type="button" @click="scrollToBlock(item.id)">
        {{ item.name }}
      </button>
    </section>

    <section id="kline-chart" class="top-market-grid">
      <el-card shadow="hover" class="chart-card top-chart-card">
        <template #header>
          <div class="card-header">
            <div class="header-left">
              <span class="title">K线图</span>
              <el-radio-group v-model="klinePeriod" size="small" @change="loadKlineData">
                <el-radio-button value="daily">日K</el-radio-button>
                <el-radio-button value="weekly">周K</el-radio-button>
                <el-radio-button value="monthly">月K</el-radio-button>
              </el-radio-group>
            </div>
            <div class="header-right">
              <el-checkbox-group v-model="activeIndicators" size="small">
                <el-checkbox-button value="MA">MA</el-checkbox-button>
                <el-checkbox-button value="MACD">MACD</el-checkbox-button>
                <el-checkbox-button value="RSI">RSI</el-checkbox-button>
                <el-checkbox-button value="KDJ">KDJ</el-checkbox-button>
                <el-checkbox-button value="BOLL">BOLL</el-checkbox-button>
              </el-checkbox-group>
            </div>
          </div>
        </template>
        <KlineChart :kline-data="klineData" :indicators="activeIndicators" height="520px" />
      </el-card>

      <div class="quote-workbench">
        <article class="workbench-panel tape-panel">
        <div class="panel-head compact">
          <h3>盘口成交</h3>
          <span>真实报价摘要</span>
        </div>
        <div class="quote-identity">
          <div>
            <strong>{{ stockInfo?.name || '-' }}</strong>
            <span>{{ stockInfo?.code || stockCode }}</span>
          </div>
          <p :style="{ color: getColor(stockInfo?.changePercent || 0) }">
            {{ stockInfo ? formatPrice(stockInfo.currentPrice) : '-' }}
            <small v-if="stockInfo">
              {{ (stockInfo?.changeAmount || 0) >= 0 ? '+' : '' }}{{ formatPrice(stockInfo?.changeAmount) }}
              ({{ formatPercent(stockInfo?.changePercent) }})
            </small>
          </p>
        </div>
        <div class="tape-grid">
          <div><span>振幅</span><strong>{{ quoteAmplitude }}</strong></div>
          <div><span>量比</span><strong>{{ quoteVolumeRatio }}</strong></div>
          <div><span>成交量</span><strong>{{ stockInfo ? formatNumber(stockInfo.volume) : '-' }}</strong></div>
          <div><span>成交额</span><strong>{{ stockInfo ? formatNumber(stockInfo.turnover) : '-' }}</strong></div>
          <div><span>今日区间</span><strong>{{ quoteRange }}</strong></div>
          <div><span>行情源</span><strong>新浪公开行情</strong></div>
        </div>
        <div v-if="stockInfo" class="quote-actions">
          <el-button type="danger" @click="handleTrade('BUY')">
            <el-icon><Top /></el-icon>
            买入
          </el-button>
          <el-button type="success" @click="handleTrade('SELL')">
            <el-icon><Bottom /></el-icon>
            卖出
          </el-button>
        </div>
        </article>

        <article class="workbench-panel">
        <div class="panel-head compact">
          <h3>F10速览</h3>
          <span>交易前检查</span>
        </div>
        <div class="f10-list">
          <div><span>市场</span><strong>{{ stockInfo?.market === 'US' ? '美股' : 'A股' }}</strong></div>
          <div><span>市盈率</span><strong>{{ stockInfo?.pe ? stockInfo.pe.toFixed(2) : '暂无真实数据' }}</strong></div>
          <div><span>市净率</span><strong>{{ stockInfo?.pb ? stockInfo.pb.toFixed(2) : '暂无真实数据' }}</strong></div>
          <div><span>总市值</span><strong>{{ stockInfo?.marketCap ? formatNumber(stockInfo.marketCap) : '暂无真实数据' }}</strong></div>
        </div>
        </article>

        <article class="workbench-panel ai-execution">
        <div class="panel-head compact">
          <h3>智能执行卡</h3>
          <span>技术 + 新闻 + 舆情</span>
        </div>
        <div v-if="aiResult" class="execution-main" :class="'execution-' + normalizedSignal">
          <strong>{{ getAiSignalText(aiResult.signal) }}</strong>
          <span>{{ safeScore(aiResult.score) }}分</span>
          <p>{{ aiResult.quantDecision?.suggestedPosition || aiResult.quantDecision?.summary || '等待智能研判刷新' }}</p>
        </div>
        <el-empty v-else :image-size="50" description="等待智能研判" />
        </article>
      </div>
    </section>

    <el-card id="ai-research" shadow="hover" class="research-card">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <span class="title">量化研判</span>
            <el-tag v-if="aiResult?.modelAvailable === false" size="small" type="warning">
              模型不可用，已启用本地量化引擎
            </el-tag>
            <el-tag v-else-if="aiResult" size="small" type="success">
              {{ aiResult.modelUsed || '量化模型' }}
            </el-tag>
          </div>
          <el-button
            v-if="stockCode"
            type="primary"
            size="small"
            :loading="aiLoading"
            :disabled="!hasAiConfig || !stockInfo || aiLoading"
            @click="loadAiAnalysis"
          >
            <el-icon><Refresh /></el-icon>
            刷新研判
          </el-button>
        </div>
      </template>

      <div v-if="aiResult" class="research-shell">
        <div class="decision-band" :class="'decision-' + normalizedSignal">
          <div class="decision-main">
            <span class="decision-label">综合信号</span>
            <strong>{{ getAiSignalText(aiResult.signal) }}</strong>
            <span>{{ aiResult.quantDecision?.summary || '系统已完成多因子量化评估。' }}</span>
          </div>
          <div class="score-ring">
            <span>{{ safeScore(aiResult.score) }}</span>
            <small>/100</small>
          </div>
        </div>

        <div class="metric-grid">
          <div class="metric-item">
            <span>趋势状态</span>
            <strong>{{ aiResult.quantDecision?.trendState || '-' }}</strong>
          </div>
          <div class="metric-item">
            <span>建议仓位</span>
            <strong>{{ aiResult.quantDecision?.suggestedPosition || '-' }}</strong>
          </div>
          <div class="metric-item">
            <span>置信度</span>
            <strong>{{ aiResult.quantDecision?.confidence ?? safeScore(aiResult.score) }}%</strong>
          </div>
          <div class="metric-item">
            <span>风险等级</span>
            <strong>{{ getRiskText(aiResult.quantDecision?.riskLevel) }}</strong>
          </div>
          <div class="metric-item">
            <span>止损参考</span>
            <strong>{{ formatPrice(aiResult.quantDecision?.stopLoss) }}</strong>
          </div>
          <div class="metric-item">
            <span>止盈参考</span>
            <strong>{{ formatPrice(aiResult.quantDecision?.takeProfit) }}</strong>
          </div>
          <div class="metric-item wide">
            <span>目标区间</span>
            <strong>{{ aiResult.quantDecision?.targetRange || aiResult.targetPrice || '-' }}</strong>
          </div>
          <div v-if="aiResult.moneyFlow" class="metric-item">
            <span>大单买入</span>
            <strong>{{ formatNumber(aiResult.moneyFlow.bigOrderBuyAmount) }}</strong>
          </div>
          <div v-if="aiResult.moneyFlow" class="metric-item">
            <span>大单卖出</span>
            <strong>{{ formatNumber(aiResult.moneyFlow.bigOrderSellAmount) }}</strong>
          </div>
          <div v-if="aiResult.moneyFlow" class="metric-item">
            <span>大单净流向</span>
            <strong :class="aiResult.moneyFlow.netBigOrderAmount >= 0 ? 'price-up' : 'price-down'">
              {{ aiResult.moneyFlow.bigOrderDirection }} {{ formatNumber(Math.abs(aiResult.moneyFlow.netBigOrderAmount)) }}
            </strong>
          </div>
          <div v-if="aiResult.moneyFlow" class="metric-item">
            <span>预期成交量</span>
            <strong>{{ formatNumber(aiResult.moneyFlow.expectedVolume) }}</strong>
          </div>
          <div v-if="aiResult.moneyFlow" class="metric-item wide">
            <span>资金流依据</span>
            <strong>{{ aiResult.moneyFlow.basis }}</strong>
          </div>
        </div>

        <section v-if="aiResult.selectedStrategy" class="panel strategy-lab-panel">
          <div class="panel-head">
            <h3>智能策略实验室</h3>
            <span>第 {{ aiResult.evolution?.generation || 1 }} 代 · 自我迭代</span>
          </div>
          <div class="selected-strategy">
            <div>
              <span class="mini-label">当前主策略</span>
              <h4>{{ aiResult.selectedStrategy.name }}</h4>
              <p>{{ aiResult.selectedStrategy.rationale }}</p>
            </div>
            <div class="strategy-score">
              <strong>{{ safeScore(aiResult.selectedStrategy.score) }}</strong>
              <small>/100</small>
            </div>
          </div>
          <div class="strategy-rules">
            <div><strong>入场</strong><span>{{ aiResult.selectedStrategy.entryRule }}</span></div>
            <div><strong>退出</strong><span>{{ aiResult.selectedStrategy.exitRule }}</span></div>
            <div><strong>止损</strong><span>{{ aiResult.selectedStrategy.stopLossRule }}</span></div>
            <div><strong>止盈</strong><span>{{ aiResult.selectedStrategy.takeProfitRule }}</span></div>
            <div class="wide"><strong>成果评判</strong><span>{{ aiResult.selectedStrategy.evaluationRule }}</span></div>
          </div>
          <div class="strategy-grid">
            <article v-for="strategy in aiResult.candidateStrategies || []" :key="strategy.name" class="strategy-card">
              <div class="voice-top">
                <strong>{{ strategy.name }}</strong>
                <el-tag :type="getConsensusType(strategy.signal === 'BUY' ? 'bullish' : strategy.signal === 'SELL' ? 'bearish' : 'neutral')" size="small">
                  {{ getAiSignalText(strategy.signal) }}
                </el-tag>
              </div>
              <div class="strategy-mini-metrics">
                <span>综合 {{ safeScore(strategy.score) }}</span>
                <span>收益 {{ safeScore(strategy.expectedReturnScore) }}</span>
                <span>风控 {{ safeScore(strategy.riskScore) }}</span>
                <span>舆情 {{ safeScore(strategy.sentimentFitScore) }}</span>
              </div>
              <p>{{ strategy.rationale }}</p>
            </article>
          </div>
          <div v-if="aiResult.evolution" class="evolution-box">
            <p><strong>上轮学习：</strong>{{ aiResult.evolution.lastLearning }}</p>
            <p><strong>下一次变异：</strong>{{ aiResult.evolution.nextMutation }}</p>
            <p><strong>成果判断：</strong>{{ aiResult.evolution.outcomeJudgement }}</p>
          </div>
        </section>

        <section id="news-panel" class="panel news-panel">
          <div class="panel-head">
            <h3>新闻依据</h3>
            <span>{{ aiResult.newsItems?.length || 0 }} 条真实新闻</span>
          </div>
          <div v-if="aiResult.newsItems?.length" class="news-list">
            <a
              v-for="news in aiResult.newsItems"
              :key="news.url || news.title"
              class="news-item"
              :href="news.url"
              target="_blank"
              rel="noreferrer"
            >
              <div>
                <strong>{{ news.title }}</strong>
                <p>{{ news.reason }}</p>
                <span>{{ news.source || '新闻源' }} · {{ news.publishTime || '时间未知' }}</span>
              </div>
              <el-tag :type="getConsensusType(news.sentiment)" size="small">{{ getDavText(news.sentiment) }}</el-tag>
            </a>
          </div>
          <el-empty v-else :image-size="56" description="暂无可验证新闻，系统不会编造新闻依据" />
        </section>

        <section v-if="aiResult.daVOpinions?.length" class="panel sentiment-panel">
          <div class="panel-head">
            <h3>大V舆情</h3>
            <span>舆情评分 {{ safeScore(aiResult.sentimentScore) }}/100</span>
          </div>
          <div class="sentiment-consensus" :class="'consensus-' + (aiResult.daVMajority?.consensus || 'neutral')">
            <el-tag :type="getConsensusType(aiResult.daVMajority?.consensus)" effect="dark">
              {{ getConsensusText(aiResult.daVMajority?.consensus) }}
            </el-tag>
            <p>{{ aiResult.daVMajority?.summary || '正在汇总市场观点。' }}</p>
            <div class="sentiment-counts">
              <span>看多 {{ aiResult.daVMajority?.bullishCount || 0 }}</span>
              <span>看空 {{ aiResult.daVMajority?.bearishCount || 0 }}</span>
              <span>中性 {{ aiResult.daVMajority?.neutralCount || 0 }}</span>
            </div>
          </div>
          <div class="voice-grid">
            <article
              v-for="opinion in aiResult.daVOpinions"
              :key="opinion.name"
              class="voice-card"
              :class="'voice-' + opinion.type"
            >
              <div class="voice-top">
                <strong>{{ opinion.name }}</strong>
                <el-tag :type="getConsensusType(opinion.type)" size="small">{{ getDavText(opinion.type) }}</el-tag>
              </div>
              <h4>{{ opinion.view }}</h4>
              <p>{{ opinion.detail }}</p>
              <div class="voice-meta">
                <span>{{ opinion.publishTime || '实时画像' }}</span>
                <span>影响力 {{ opinion.influence || 0 }}/10</span>
              </div>
            </article>
          </div>
        </section>

        <div class="research-grid">
          <section class="panel factors-panel">
            <div class="panel-head">
              <h3>因子评分</h3>
              <span>权重合成</span>
            </div>
            <div class="factor-list">
              <div v-for="factor in aiResult.factors || []" :key="factor.name" class="factor-row">
                <div class="factor-top">
                  <strong>{{ factor.name }}</strong>
                  <span>{{ factor.score }}/100 · {{ factor.weight }}%</span>
                </div>
                <el-progress
                  :percentage="safeScore(factor.score)"
                  :stroke-width="8"
                  :show-text="false"
                  :color="getScoreColor(factor.score)"
                />
                <p>{{ factor.reason }}</p>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head">
              <h3>执行清单</h3>
              <span>交易纪律</span>
            </div>
            <ul class="action-list">
              <li v-for="item in aiResult.actions || []" :key="item">{{ item }}</li>
            </ul>

            <div class="panel-head risk-head">
              <h3>风险监控</h3>
              <span>盘中复核</span>
            </div>
            <ul class="risk-list">
              <li v-for="item in aiResult.risks || []" :key="item">{{ item }}</li>
            </ul>
          </section>
        </div>

        <section id="scenario-panel" class="panel scenario-panel">
          <div class="panel-head">
            <h3>场景推演</h3>
            <span>触发条件</span>
          </div>
          <el-table :data="aiResult.scenarios || []" size="small" border>
            <el-table-column prop="name" label="情景" min-width="120" />
            <el-table-column prop="probability" label="概率" width="90">
              <template #default="{ row }">{{ row.probability }}%</template>
            </el-table-column>
            <el-table-column prop="trigger" label="触发条件" min-width="220" />
            <el-table-column prop="action" label="动作" min-width="260" />
          </el-table>
        </section>

        <section v-if="aiResult.analysis" class="panel narrative-panel">
          <div class="panel-head">
            <h3>研究摘要</h3>
            <span>{{ aiResult.modelAvailable === false ? '本地生成' : '智能增强' }}</span>
          </div>
          <div class="markdown-body" v-html="renderedAnalysis"></div>
        </section>
      </div>

      <template v-else-if="aiLoading">
        <div class="ai-loading">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p>正在计算多因子研判...</p>
        </div>
      </template>

      <template v-else>
        <el-empty :image-size="72" description="暂无量化研判结果" />
        <div v-if="!hasAiConfig" class="no-config-hint">
          <el-alert title="请先在智能助手页面保存一个模型配置" type="warning" :closable="false" show-icon />
        </div>
      </template>
    </el-card>

    <el-card id="tech-signal" shadow="hover" class="signal-card">
      <template #header>
        <span class="title">技术面买卖信号</span>
      </template>
      <SignalIndicator :signals="signals" :stock-code="stockCode" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Bottom, Loading, Refresh, Top } from '@element-plus/icons-vue'
import { useStockStore } from '@/store/stock'
import { getTradeSignal } from '@/api/analysis'
import { analyzeStock, getModelConfigs } from '@/api/ai'
import { formatNumber, formatPercent, formatPrice, getColor } from '@/utils/format'
import KlineChart from '@/components/KlineChart.vue'
import SignalIndicator from '@/components/SignalIndicator.vue'
import type { AiAnalysisResponse, KlineData } from '@/types'

const route = useRoute()
const router = useRouter()
const stockStore = useStockStore()

const stockCode = computed(() => route.params.code as string)
const stockInfo = computed(() => stockStore.currentStock)
const loading = ref(false)
const klinePeriod = ref('daily')
const activeIndicators = ref<string[]>(['MA'])
const klineData = ref<KlineData | null>(null)
const signals = ref<any>([])
const aiResult = ref<AiAnalysisResponse | null>(null)
const aiLoading = ref(false)
const hasAiConfig = ref(false)

const normalizedSignal = computed(() => (aiResult.value?.signal || 'HOLD').toLowerCase())
const detailNav = [
  { id: 'ai-research', name: '智能研判' },
  { id: 'kline-chart', name: 'K线技术' },
  { id: 'tech-signal', name: '买卖信号' },
  { id: 'news-panel', name: '新闻舆情' },
  { id: 'scenario-panel', name: '场景推演' }
]

const quoteAmplitude = computed(() => {
  const high = Number(stockInfo.value?.highPrice || 0)
  const low = Number(stockInfo.value?.lowPrice || 0)
  const close = Number(stockInfo.value?.closePrice || 0)
  if (!high || !low || !close) return '-'
  return `${(((high - low) / close) * 100).toFixed(2)}%`
})

const quoteRange = computed(() => {
  if (!stockInfo.value) return '-'
  return `${formatPrice(stockInfo.value.lowPrice)} - ${formatPrice(stockInfo.value.highPrice)}`
})

const quoteVolumeRatio = computed(() => {
  const volume = Number(stockInfo.value?.volume || 0)
  if (!volume) return '暂无真实数据'
  return volume >= 100000000 ? '高活跃' : volume >= 10000000 ? '活跃' : '普通'
})

const renderedAnalysis = computed(() => {
  if (!aiResult.value?.analysis) return ''
  return aiResult.value.analysis
    .replace(/### (.*?)$/gm, '<h4>$1</h4>')
    .replace(/## (.*?)$/gm, '<h3>$1</h3>')
    .replace(/# (.*?)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/- (.*?)$/gm, '<li>$1</li>')
    .replace(/(<li>[\s\S]*?<\/li>)/g, '<ul>$1</ul>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br/>')
    .replace(/^(.+?)$/m, '<p>$1</p>')
})

async function loadAiAnalysis() {
  if (!stockCode.value || !hasAiConfig.value || !stockStore.currentStock) return
  aiLoading.value = true
  try {
    const configsRes = await getModelConfigs()
    const configs = configsRes.data?.data || []
    const enabledConfig = configs.find((config: any) => config.enabled) || configs[0]
    if (!enabledConfig) {
      hasAiConfig.value = false
      aiResult.value = null
      return
    }

    const res = await analyzeStock({
      stockCode: stockCode.value,
      configId: enabledConfig.id
    })
    aiResult.value = res.data?.data || null
  } catch (error) {
    console.error('量化研判失败:', error)
    aiResult.value = null
  } finally {
    aiLoading.value = false
  }
}

async function checkAiConfig() {
  try {
    const res = await getModelConfigs()
    hasAiConfig.value = (res.data?.data || []).length > 0
  } catch {
    hasAiConfig.value = false
  }
}

async function loadKlineData() {
  if (!stockCode.value || !stockStore.currentStock) {
    klineData.value = null
    return
  }
  try {
    klineData.value = await stockStore.getKlineData(stockCode.value, klinePeriod.value)
  } catch (error) {
    console.error('加载K线数据失败:', error)
  }
}

async function loadSignals() {
  if (!stockCode.value || !stockStore.currentStock) {
    signals.value = null
    return
  }
  try {
    const res = await getTradeSignal(stockCode.value)
    signals.value = res.data.data || null
  } catch (error) {
    console.error('加载买卖信号失败:', error)
    signals.value = null
  }
}

function handleTrade(direction: string) {
  router.push({ path: '/trade', query: { code: stockCode.value, direction } })
}

function goBack() {
  router.push('/market')
}

function scrollToBlock(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function getScoreColor(score: number) {
  if (score >= 70) return '#d84d4d'
  if (score >= 50) return '#d49530'
  return '#6f7f95'
}

function getAiSignalText(signal?: string) {
  if (signal === 'BUY') return '买入'
  if (signal === 'SELL') return '卖出'
  return '观望'
}

function getRiskText(risk?: string) {
  if (risk === 'LOW') return '低'
  if (risk === 'HIGH') return '高'
  if (risk === 'MEDIUM') return '中'
  return '-'
}

function getConsensusType(type?: string) {
  if (type === 'bullish') return 'danger'
  if (type === 'bearish') return 'success'
  return 'info'
}

function getConsensusText(type?: string) {
  if (type === 'bullish') return '多数看多'
  if (type === 'bearish') return '多数看空'
  return '分歧中性'
}

function getDavText(type?: string) {
  if (type === 'bullish') return '看多'
  if (type === 'bearish') return '看空'
  return '中性'
}

function safeScore(value: any) {
  const score = Number(value)
  if (Number.isNaN(score)) return 0
  return Math.max(0, Math.min(100, Math.round(score)))
}

async function bootstrap() {
  loading.value = true
  try {
    klineData.value = null
    signals.value = null
    aiResult.value = null
    await Promise.all([
      stockStore.getStockDetail(stockCode.value),
      checkAiConfig()
    ])
    if (stockStore.currentStock) {
      await Promise.all([
        loadKlineData(),
        loadSignals()
      ])
    }
  } finally {
    loading.value = false
  }
  if (hasAiConfig.value && stockStore.currentStock) {
    loadAiAnalysis()
  }
}

onMounted(bootstrap)

watch(stockCode, async () => {
  aiResult.value = null
  await bootstrap()
})
</script>

<style scoped lang="scss">
.stock-detail {
  max-width: 1400px;
}

.back-nav {
  margin-bottom: 8px;
}

.real-data-alert {
  margin-bottom: 12px;
}

.info-card,
.research-card,
.chart-card,
.signal-card {
  border-radius: 8px;
  margin-bottom: 16px;
}

.stock-detail > .info-card {
  display: none;
}

.detail-nav {
  position: sticky;
  top: 64px;
  z-index: 5;
  display: flex;
  gap: 8px;
  padding: 10px 0;
  margin-bottom: 12px;
  background: #f5f7fa;

  button {
    padding: 8px 14px;
    border: 1px solid #dcdfe6;
    border-radius: 6px;
    background: #fff;
    color: #303133;
    cursor: pointer;

    &:hover {
      color: #409eff;
      border-color: #409eff;
    }
  }
}

.top-market-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(340px, 0.75fr);
  gap: 12px;
  margin-bottom: 16px;
}

.top-chart-card {
  margin-bottom: 0;
}

.quote-workbench {
  display: grid;
  gap: 12px;
}

.workbench-panel {
  padding: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.panel-head.compact {
  margin-bottom: 10px;
}

.quote-identity {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #eef1f5;
  border-radius: 8px;
  background: #fbfcfe;

  div {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    color: #303133;
  }

  strong {
    font-size: 18px;
  }

  span {
    color: #909399;
  }

  p {
    margin: 8px 0 0;
    font-size: 28px;
    font-weight: 700;
    line-height: 1.2;
  }

  small {
    display: block;
    margin-top: 4px;
    font-size: 13px;
    font-weight: 600;
  }
}

.tape-grid,
.f10-list {
  display: grid;
  gap: 8px;
}

.tape-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.tape-grid div,
.f10-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f8fafc;

  span {
    color: #909399;
    font-size: 12px;
  }

  strong {
    color: #303133;
    text-align: right;
  }
}

.quote-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;

  .el-button {
    width: 100%;
    margin-left: 0;
  }
}

.ai-execution {
  display: flex;
  flex-direction: column;
}

.execution-main {
  display: grid;
  gap: 8px;
  min-height: 144px;
  padding: 14px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  background: #f8fafc;

  strong {
    font-size: 28px;
    color: #303133;
  }

  span {
    color: #d84d4d;
    font-weight: 700;
  }

  p {
    margin: 0;
    color: #606266;
    line-height: 1.6;
  }
}

.execution-buy {
  border-color: rgba(216, 77, 77, 0.28);
  background: #fff6f6;
}

.execution-sell {
  border-color: rgba(60, 155, 98, 0.28);
  background: #f5fbf7;
}

.card-header,
.header-left,
.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.card-header {
  justify-content: space-between;
}

.title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

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
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;

  h2 {
    font-size: 24px;
    color: #303133;
    margin: 0;
    white-space: nowrap;
  }
}

.stock-code {
  font-size: 14px;
  color: #909399;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.current-price {
  font-size: 32px;
  font-weight: 700;
}

.change-info {
  font-size: 16px;
  font-weight: 500;
}

.info-right {
  flex: 2;
  min-width: 420px;
}

.info-grid,
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.info-item,
.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 6px;
  min-height: 56px;
}

.label,
.metric-item span {
  font-size: 12px;
  color: #909399;
}

.value,
.metric-item strong {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  text-align: center;
  word-break: break-word;
}

.metric-item.wide {
  grid-column: span 2;
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 16px;

  .el-button {
    min-width: 120px;
  }
}

.text-danger {
  color: #f56c6c !important;
}

.text-success {
  color: #67c23a !important;
}

.research-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.decision-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  background: #f8fafc;
}

.decision-buy {
  border-color: rgba(216, 77, 77, 0.28);
  background: linear-gradient(90deg, rgba(216, 77, 77, 0.1), #fff);
}

.decision-sell {
  border-color: rgba(56, 142, 88, 0.28);
  background: linear-gradient(90deg, rgba(56, 142, 88, 0.1), #fff);
}

.decision-main {
  display: flex;
  flex-direction: column;
  gap: 6px;

  strong {
    font-size: 30px;
    color: #303133;
  }

  span:last-child {
    color: #606266;
    line-height: 1.6;
  }
}

.decision-label {
  font-size: 12px;
  color: #909399;
}

.score-ring {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  border: 8px solid #d7dde7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  flex: 0 0 auto;

  span {
    font-size: 26px;
    font-weight: 700;
    line-height: 1;
  }

  small {
    color: #909399;
  }
}

.research-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 14px;
}

.strategy-lab-panel,
.news-panel {
  margin-bottom: 16px;
}

.selected-strategy {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #f8fafc;

  h4 {
    margin: 4px 0 8px;
    font-size: 18px;
    color: #1f2d3d;
  }

  p {
    margin: 0;
    color: #606266;
    line-height: 1.6;
  }
}

.mini-label {
  font-size: 12px;
  color: #909399;
}

.strategy-score {
  min-width: 90px;
  text-align: right;

  strong {
    display: block;
    font-size: 32px;
    color: #d84d4d;
    line-height: 1;
  }

  small {
    color: #909399;
  }
}

.strategy-rules {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;

  div {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px;
    border-radius: 8px;
    background: #fff;
    border: 1px solid #ebeef5;
  }

  .wide {
    grid-column: 1 / -1;
  }

  strong {
    color: #303133;
    font-size: 13px;
  }

  span {
    color: #606266;
    line-height: 1.5;
  }
}

.strategy-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.strategy-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  background: #fff;

  p {
    margin: 8px 0 0;
    color: #606266;
    line-height: 1.5;
  }
}

.strategy-mini-metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin-top: 10px;

  span {
    font-size: 12px;
    color: #606266;
    background: #f5f7fa;
    border-radius: 6px;
    padding: 5px 8px;
  }
}

.evolution-box {
  margin-top: 12px;
  padding: 12px;
  border-left: 3px solid #409eff;
  background: #f4f9ff;

  p {
    margin: 4px 0;
    color: #4b5b70;
    line-height: 1.5;
  }
}

.news-list {
  display: grid;
  gap: 10px;
}

.news-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
  background: #fff;

  strong {
    color: #303133;
  }

  p {
    margin: 6px 0;
    color: #606266;
    line-height: 1.5;
  }

  span {
    font-size: 12px;
    color: #909399;
  }
}

.sentiment-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sentiment-consensus {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  background: #f7faf8;
  border: 1px solid #e1eee7;

  p {
    margin: 0;
    color: #303133;
    line-height: 1.6;
  }
}

.sentiment-counts {
  display: flex;
  gap: 10px;
  color: #606266;
  font-size: 12px;
  white-space: nowrap;
}

.voice-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.voice-card {
  border: 1px solid #ebeef5;
  border-left: 3px solid #909399;
  border-radius: 8px;
  padding: 12px;
  background: #fff;

  &.voice-bullish {
    border-left-color: #d84d4d;
  }

  &.voice-bearish {
    border-left-color: #3c9b62;
  }

  h4 {
    margin: 8px 0 6px;
    color: #303133;
    font-size: 14px;
  }

  p {
    margin: 0;
    color: #606266;
    font-size: 12px;
    line-height: 1.6;
  }
}

.voice-top,
.voice-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.voice-top strong {
  color: #303133;
}

.voice-meta {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
}

.panel {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
  background: #fff;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    font-size: 15px;
    color: #303133;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.risk-head {
  margin-top: 16px;
}

.factor-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.factor-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;

  strong {
    color: #303133;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.factor-row p {
  margin: 6px 0 0;
  color: #606266;
  font-size: 12px;
  line-height: 1.5;
}

.action-list,
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 18px;
  margin: 0;
  color: #303133;
  line-height: 1.6;
}

.scenario-panel,
.narrative-panel {
  overflow: hidden;
}

.markdown-body {
  color: #303133;
  line-height: 1.8;

  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 12px 0 6px;
  }

  :deep(p) {
    margin: 8px 0;
  }

  :deep(ul) {
    padding-left: 20px;
    margin: 6px 0;
  }
}

.ai-loading {
  text-align: center;
  padding: 42px;
  color: #909399;
}

.no-config-hint {
  margin-top: 12px;
}

@media (max-width: 900px) {
  .top-market-grid {
    grid-template-columns: 1fr;
  }

  .info-right {
    min-width: 100%;
  }

  .info-grid,
  .metric-grid,
  .research-grid,
  .voice-grid {
    grid-template-columns: 1fr 1fr;
  }

  .metric-item.wide {
    grid-column: span 2;
  }
}

@media (max-width: 640px) {
  .detail-nav {
    overflow-x: auto;
  }

  .info-grid,
  .metric-grid,
  .research-grid,
  .voice-grid,
  .sentiment-consensus {
    grid-template-columns: 1fr;
  }

  .metric-item.wide {
    grid-column: span 1;
  }

  .decision-band {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
