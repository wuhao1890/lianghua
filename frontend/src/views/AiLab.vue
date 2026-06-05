<template>
  <div class="ai-lab">
    <section class="lab-header">
      <div>
        <h2>智能自动投资实验室</h2>
        <p>{{ labPageIntro }}</p>
      </div>
      <div class="header-actions">
        <el-checkbox-group v-model="enabledAssets">
          <el-checkbox-button label="stock">股票</el-checkbox-button>
          <el-checkbox-button label="gold">黄金</el-checkbox-button>
          <el-checkbox-button label="fund">基金</el-checkbox-button>
        </el-checkbox-group>
        <span class="control-label">资金</span>
        <el-input-number v-model="capital" :min="1000" :step="10000" controls-position="right" />
        <span class="control-label">每次实验代数</span>
        <el-input-number v-model="rounds" :min="3" :max="20" controls-position="right" />
        <span class="control-label">自动频率(分钟)</span>
        <el-input-number v-model="intervalMinutes" :min="1" :max="240" controls-position="right" />
        <el-button type="primary" :loading="loading" @click="runAutoLab">
          <el-icon><VideoPlay /></el-icon>
          自动选标的并实验
        </el-button>
      </div>
    </section>

    <section class="kpi-grid">
      <article class="kpi">
        <span>最佳收益</span>
        <strong :class="bestReturn >= 0 ? 'up' : 'down'">{{ formatPercent(bestReturn) }}</strong>
        <small>{{ champion?.assetName || '等待实验' }}</small>
      </article>
      <article class="kpi">
        <span>冠军段位</span>
        <strong>{{ rankName(champion?.rank) }}</strong>
        <small>第 {{ generation }} 代 · 已入库 {{ iterationCount }} 次</small>
      </article>
      <article class="kpi">
        <span>候选资产</span>
        <strong>{{ assets.length }}</strong>
        <small>股票 / 黄金 / 基金</small>
      </article>
      <article class="kpi">
        <span>策略样本</span>
        <strong>{{ experiments.length }}</strong>
        <small>智能自动 + 自定义</small>
      </article>
    </section>

    <section class="lab-view-tabs">
      <el-button :type="labView === 'overview' ? 'primary' : ''" @click="router.push('/ai-lab')">实验总览</el-button>
      <el-button :type="labView === 'growth' ? 'primary' : ''" @click="router.push('/ai-lab/growth')">成长记录</el-button>
      <el-button :type="labView === 'research' ? 'primary' : ''" @click="router.push('/ai-lab/research')">研究控制</el-button>
      <el-button :type="labView === 'portfolio' ? 'primary' : ''" @click="router.push('/ai-lab/portfolio')">组合方案</el-button>
    </section>

    <section v-if="showOverview" class="lab-entry-grid">
      <article v-for="entry in labEntries" :key="entry.path" class="lab-entry" @click="router.push(entry.path)">
        <strong>{{ entry.title }}</strong>
        <span>{{ entry.desc }}</span>
      </article>
    </section>

    <section v-if="showOverview || showGrowth" class="decision-grid">
      <article class="panel mood-panel">
        <div class="panel-head">
          <div>
            <h3>当前结论</h3>
            <span>只看现在该怎么做，不看流水账。</span>
          </div>
          <el-tag :type="moodInfo.type" effect="dark">{{ moodInfo.name }}</el-tag>
        </div>
        <div class="decision-main">
          <strong>{{ currentDecision.title }}</strong>
          <p>{{ currentDecision.detail }}</p>
        </div>
        <div class="decision-points">
          <span v-for="item in currentDecision.points" :key="item">{{ item }}</span>
        </div>
      </article>

      <article class="panel learning-panel">
        <div class="panel-head">
          <div>
            <h3>模型学到了什么</h3>
            <span>第 {{ generation }} 代 · 当前只展示结论和变化。</span>
          </div>
        </div>
        <div class="learning-list">
          <div v-for="item in learningInsights" :key="item.title" class="learning-item">
            <strong>{{ item.title }}</strong>
            <span>{{ item.detail }}</span>
          </div>
        </div>
      </article>
    </section>

    <section v-if="showResearch || showGrowth" class="panel research-panel">
      <div class="panel-head">
        <div>
          <h3>研究控制台</h3>
          <span>指定标的或调整学习方向，避免无脑迭代。</span>
        </div>
        <el-button type="primary" plain @click="applyResearchFocus">加入研究任务</el-button>
      </div>
      <div class="research-form">
        <el-input v-model="researchForm.target" placeholder="指定股票/基金/金属代码或名称，如 600519、贵州茅台、110022" />
        <el-input
          v-model="researchForm.direction"
          type="textarea"
          :rows="3"
          placeholder="告诉模型重点研究什么：长期持有、短线波段、新闻催化、资金流、黑名单观察、某个指标等。"
        />
      </div>
      <div v-if="researchFocuses.length" class="research-task-list">
        <article v-for="item in researchFocuses" :key="researchFocusKey(item)" class="research-task">
          <div class="research-task-body" @click="goResearchFocus(item)">
            <strong>{{ item.target || '全局研究' }}</strong>
            <span>{{ item.direction || '持续研究该标的的历史规律、新闻舆情、资金流和技术面' }}</span>
            <small>加入时间 {{ formatTime(item.updatedAt) }}</small>
          </div>
          <div class="research-task-actions">
            <el-button text type="primary" @click="goResearchFocus(item)">查看</el-button>
            <el-button text type="danger" @click="removeResearchFocus(item)">移除</el-button>
          </div>
        </article>
      </div>
      <el-empty v-else description="还没有研究任务，添加多少个就会同时研究多少个" :image-size="72" />
      <div class="blacklist" v-if="blacklistItems.length">
        <strong>风险黑名单</strong>
        <span v-for="item in blacklistItems" :key="item.code">{{ item.name }}：{{ item.reason }}</span>
      </div>
    </section>

    <section v-if="showOverview || showGrowth" class="rank-track">
      <div v-for="rank in ranks" :key="rank.key" class="rank-step" :class="{ active: champion?.rank === rank.key }">
        <span :class="'rank-dot rank-' + rank.key"></span>
        <strong>{{ rank.name }}</strong>
        <small>{{ rank.range }}</small>
      </div>
    </section>

    <section v-if="showOverview || showPortfolio" class="panel portfolio-panel">
      <div class="panel-head">
        <div>
          <h3>组合资金方案</h3>
          <span>{{ activePortfolioPlan.summary }}</span>
        </div>
      </div>
      <div class="portfolio-grid">
        <article v-for="bucket in activePortfolioPlan.buckets" :key="bucket.name" class="portfolio-bucket">
          <div class="bucket-head">
            <div>
              <strong>{{ bucket.name }}</strong>
              <span>{{ bucket.description }}</span>
            </div>
            <b>{{ bucket.ratio }}%</b>
          </div>
          <div class="bucket-money">
            <span>目标资金</span>
            <strong>{{ formatMoney(bucket.targetAmount) }}</strong>
          </div>
          <div class="bucket-items">
            <div v-for="item in bucket.items" :key="item.experimentId" class="bucket-item">
              <div>
                <strong class="linkish" @click="goPortfolioItem(item)">{{ item.assetName }} {{ item.assetCode }}</strong>
                <span>{{ portfolioStrategyName(item) }} · {{ rankName(item.rank) }} · {{ signalText(item.signal) }} · {{ item.score }}分</span>
              </div>
              <em>{{ formatMoney(item.targetAmount) }}</em>
            </div>
            <small v-if="!bucket.items.length">本组等待真实数据触发买入条件</small>
          </div>
        </article>
      </div>
    </section>

    <section class="main-grid">
      <article v-if="showOverview || showPortfolio" class="panel champion-panel">
        <div class="panel-head">
          <div>
            <h3>当前最优策略</h3>
            <span>{{ champion ? `${champion.assetName} · ${assetTypeText(champion.assetType)}` : '尚未运行' }}</span>
          </div>
          <el-tag v-if="champion" :type="rankTag(champion.rank)" effect="dark">{{ rankName(champion.rank) }}</el-tag>
        </div>

        <div v-if="champion" class="champion">
          <div>
            <h4>{{ displayStrategyName(champion) }}</h4>
            <p>{{ zhText(champion.reason) }}</p>
          </div>
          <div class="champion-score">
            <strong>{{ champion.score }}</strong>
            <small>综合分</small>
          </div>
        </div>

        <div v-if="champion" class="trade-plan">
          <div><span>动作</span><strong :class="signalClass(champion.signal)">{{ signalText(champion.signal) }}</strong></div>
          <div><span>模拟收益</span><strong :class="champion.returnPct >= 0 ? 'up' : 'down'">{{ formatPercent(champion.returnPct) }}</strong></div>
          <div><span>回撤</span><strong>{{ formatPercent(champion.drawdownPct) }}</strong></div>
          <div><span>建议仓位</span><strong>{{ champion.position }}%</strong></div>
          <div><span>模拟投入</span><strong>{{ formatMoney(simulatedPosition) }}</strong></div>
          <div><span>买入时间</span><strong>{{ tradePlanFor(champion).buyTime }}</strong></div>
          <div><span>买入价格</span><strong>{{ tradePlanFor(champion).buyPrice }}</strong></div>
          <div><span>卖出时间</span><strong>{{ tradePlanFor(champion).sellTime }}</strong></div>
          <div><span>目标卖价</span><strong>{{ tradePlanFor(champion).sellPrice }}</strong></div>
          <div><span>历史盈利</span><strong :class="historicalProfit >= 0 ? 'up' : 'down'">{{ formatMoney(historicalProfit) }}</strong></div>
          <div><span>实时盈利</span><strong :class="realtimeProfit >= 0 ? 'up' : 'down'">{{ formatMoney(realtimeProfit) }}</strong></div>
          <div><span>未来验证</span><strong :class="futureProfit >= 0 ? 'up' : 'down'">{{ formatMoney(futureProfit) }}</strong></div>
          <div class="wide"><span>现在怎么做</span><p>{{ tradePlanFor(champion).action }}</p></div>
          <div class="wide"><span>买入依据</span><p>{{ zhText(champion.entryRule) }}</p></div>
          <div class="wide"><span>卖出依据</span><p>{{ zhText(champion.exitRule) }}</p></div>
          <div class="wide"><span>收益来源</span><p>{{ tradePlanFor(champion).profitSource }}</p></div>
          <div class="wide"><span>策略记忆</span><p>{{ strategyMemoryFor(champion) }}</p></div>
        </div>

        <el-empty v-else :image-size="72" description="点击自动实验后生成最优策略" />
      </article>

      <article v-if="showOverview || showPortfolio" class="panel top-five-panel">
        <div class="panel-head">
          <div>
            <h3>前五最优组合</h3>
            <span>每代自动比较股票、黄金、基金和不同策略；盈利能力下降会降级。</span>
          </div>
        </div>
        <div class="top-five-list">
          <article v-for="(item, index) in topFiveStrategies" :key="item.id" class="top-five-item" @click="goAssetDetail(item)">
            <b>{{ index + 1 }}</b>
            <div>
              <strong>{{ item.assetName }} · {{ displayStrategyName(item) }}</strong>
              <span>{{ item.assetCode }} · {{ assetTypeText(item.assetType) }} · {{ rankName(item.rank) }} · {{ signalText(item.signal) }} · 仓位 {{ item.position }}%</span>
              <small>{{ tradePlanFor(item).action }}</small>
            </div>
            <em :class="item.returnPct >= 0 ? 'up' : 'down'">{{ formatPercent(item.returnPct) }}</em>
          </article>
        </div>
      </article>

      <article v-if="showOverview || showPortfolio" class="panel trade-ledger-panel">
        <div class="panel-head">
          <div>
            <h3>持仓与成交复盘</h3>
            <span>买入后按策略周期持有，持续统计浮盈浮亏；只在卖出点提醒。</span>
          </div>
        </div>
        <div class="trade-ledger">
          <div v-for="trade in simulatedTrades.slice(0, 8)" :key="trade.id" class="trade-row">
            <strong class="linkish" @click="goTradeItem(trade)">{{ trade.assetName }} {{ trade.assetCode }} · {{ tradeStrategyName(trade) }}</strong>
            <span>{{ trade.assetCode }} · {{ trade.bucketName || '未分组' }} · {{ trade.action }} · {{ trade.status }} · 投入 {{ formatMoney(trade.amount) }} · 买入时间 {{ formatTime(trade.createdAt) }}</span>
            <small>
              周期 {{ trade.holdingPeriod || '等待周期识别' }} · 已持有 {{ trade.holdingGenerations || 0 }} 代 ·
              买入 {{ formatPrice(trade.buyPrice) }} · 当前 {{ formatPrice(trade.currentPrice || trade.buyPrice) }} · 手续费意识 {{ formatMoney(trade.fee || 5) }} ·
              {{ trade.sellPrice ? `卖出 ${formatPrice(trade.sellPrice)} · 原因 ${trade.closeReason || '策略复盘'}` : `计划 ${formatPrice(trade.plannedSellPrice)}` }}
            </small>
            <em :class="tradeProfitFor(trade) >= 0 ? 'up' : 'down'">{{ formatMoney(tradeProfitFor(trade)) }}</em>
          </div>
        </div>
        <el-empty v-if="!simulatedTrades.length" :image-size="60" description="暂无模拟成交" />
      </article>

      <article v-if="showGrowth" class="panel">
        <div class="panel-head">
          <div>
            <h3>最近同步摘要</h3>
            <span>{{ iterationHistory.length }} 条 · 每 {{ intervalMinutes }} 分钟学习一次，只展示最近3条</span>
          </div>
        </div>
        <div class="log-list iteration-list">
          <div v-for="item in iterationHistory.slice(0, 3)" :key="item.id" class="log-item">
            <strong>第 {{ item.generation }} 代 · {{ item.champion?.assetName || '暂无冠军' }}</strong>
            <span>
                  {{ strategyNameFromText(item.champion?.strategyName || '等待策略') }}
              · 收益 {{ formatPercent(item.champion?.returnPct || 0) }}
              · 回撤 {{ formatPercent(item.champion?.drawdownPct || 0) }}
              · {{ formatTime(item.createdAt) }}
            </span>
          </div>
        </div>
        <el-empty v-if="!iterationHistory.length" :image-size="60" description="暂无后台迭代记录" />
      </article>

      <article v-if="showResearch" class="panel">
        <div class="panel-head">
          <div>
            <h3>自定义策略</h3>
            <span>{{ customStrategies.length }} 个策略会和智能自动策略一起竞争</span>
          </div>
          <el-button type="primary" plain @click="addCustomStrategy">
            <el-icon><Plus /></el-icon>
            加入
          </el-button>
        </div>

        <el-form class="custom-form" :model="customForm" label-position="top">
          <el-form-item label="标题">
            <el-input v-model="customForm.title" placeholder="如：政策利好低吸、黄金避险突破、基金轮动增强" />
          </el-form-item>
          <el-form-item label="自定义内容">
            <el-input
              v-model="customForm.content"
              type="textarea"
              :rows="5"
              placeholder="直接写你的想法：关注哪些新闻、技术指标、仓位、止损、买卖条件。系统会识别成标准策略并参与迭代。"
            />
          </el-form-item>
        </el-form>

        <div class="custom-list">
          <div v-for="item in customStrategies" :key="item.id" class="custom-item">
            <div>
              <strong>{{ item.name }}</strong>
              <span>{{ styleText(item.style) }}</span>
            </div>
            <el-button text type="danger" @click="removeCustomStrategy(item.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </article>
    </section>

    <section v-if="showOverview || showResearch" class="panel">
      <div class="panel-head">
        <div>
          <h3>智能自动选标的</h3>
          <span>按真实行情、净值或公开报价筛出候选，再进入策略实验。</span>
        </div>
      </div>
      <div class="asset-grid">
        <el-popover
          v-for="asset in sortedAssets"
          :key="asset.id"
          trigger="hover"
          placement="top"
          width="360"
        >
          <template #reference>
            <article class="asset-card clickable" @click="goAssetDetailByAsset(asset)">
              <div>
                <el-tag size="small" :type="assetTag(asset.type)">{{ assetTypeText(asset.type) }}</el-tag>
                <strong>{{ asset.name }}</strong>
                <span>{{ asset.code }}</span>
              </div>
              <div class="asset-price">
                <strong>{{ formatPrice(asset.price) }}</strong>
                <span :class="asset.changePct >= 0 ? 'up' : 'down'">{{ formatPercent(asset.changePct) }}</span>
              </div>
              <el-progress :percentage="asset.aiScore" :stroke-width="7" :color="asset.aiScore >= 70 ? '#1f9d66' : '#d59b2d'" />
              <p>{{ zhText(asset.reason) }}</p>
            </article>
          </template>
          <div class="score-detail">
            <h4>{{ asset.name }} 为什么入选</h4>
            <div v-for="score in assetFactorScores(asset)" :key="score.name" class="score-detail-row">
              <div>
                <strong>{{ zhText(score.name) }}</strong>
                <span>{{ zhText(score.reason) }}</span>
              </div>
              <b>{{ score.score }}分</b>
            </div>
          </div>
        </el-popover>
      </div>
    </section>

    <section v-if="showGrowth" class="panel">
      <div class="panel-head">
        <div>
          <h3>标的长期档案</h3>
          <span>让模型记住每个标的的脾气、周期、风险和下一步动作。</span>
        </div>
      </div>
      <div class="archive-grid">
        <article v-for="item in targetArchives" :key="item.code" class="archive-card" @click="goArchiveDetail(item)">
          <div class="archive-head">
            <strong>{{ item.name }} {{ item.code }}</strong>
            <el-tag size="small" :type="item.moodType">{{ item.mood }}</el-tag>
          </div>
          <p>{{ item.profile }}</p>
          <div class="archive-scores">
            <span>技术 {{ item.techScore }}分</span>
            <span>舆情 {{ item.sentimentScore }}分</span>
            <span>风险 {{ item.riskScore }}分</span>
          </div>
          <small>{{ item.nextAction }}</small>
        </article>
      </div>
    </section>

    <section v-if="showGrowth || showOverview" class="panel">
      <div class="panel-head">
        <div>
          <h3>策略收益排位赛</h3>
          <span>每一代根据收益、回撤、舆情和技术分重新加权，弱策略降级，强策略晋级。</span>
        </div>
        <el-button :disabled="!experiments.length || loading" @click="evolveAgain">
          <el-icon><Refresh /></el-icon>
          再迭代一代
        </el-button>
        <el-button :disabled="!experiments.length || loading" :type="autoRunning ? 'warning' : 'success'" @click="toggleAutoEvolution">
          <el-icon><VideoPlay /></el-icon>
          {{ autoRunning ? '暂停自动迭代' : '开始自动迭代' }}
        </el-button>
      </div>

      <div class="experiment-board">
        <el-popover
          v-for="item in sortedExperiments"
          :key="item.id"
          trigger="hover"
          placement="top"
          width="380"
          popper-class="score-popover"
        >
          <template #reference>
            <article class="experiment-card clickable" :class="'card-' + item.rank" @click="goAssetDetail(item)">
              <div class="experiment-top">
                <div>
                  <strong>{{ displayStrategyName(item) }}</strong>
                  <span>{{ item.assetName }} {{ item.assetCode }} · {{ assetTypeText(item.assetType) }} · {{ item.generation }} 代</span>
                </div>
                <el-tag :type="rankTag(item.rank)">{{ rankName(item.rank) }}</el-tag>
              </div>
              <div class="score-row">
                <div><span>综合分</span><strong>{{ item.score }}</strong></div>
                <div><span>收益</span><strong :class="item.returnPct >= 0 ? 'up' : 'down'">{{ formatPercent(item.returnPct) }}</strong></div>
                <div><span>胜率</span><strong>{{ item.winRate }}%</strong></div>
              </div>
              <div class="mini-metrics">
                <span>动作 {{ signalText(item.signal) }}</span>
                <span>回撤 {{ formatPercent(item.drawdownPct) }}</span>
                <span>仓位 {{ item.position }}%</span>
              </div>
              <p>{{ zhText(item.reason) }}</p>
              <div class="strategy-note">{{ strategyExplain(item.style) }}</div>
              <div class="mutation">{{ zhText(item.mutation) }}</div>
            </article>
          </template>
          <div class="score-detail">
            <h4>{{ item.assetName }} · {{ displayStrategyName(item) }}</h4>
            <div v-for="score in item.factorScores" :key="score.name" class="score-detail-row">
              <div>
                <strong>{{ zhText(score.name) }}</strong>
                <span>{{ zhText(score.reason) }}</span>
              </div>
              <b>{{ score.score }}分</b>
            </div>
          </div>
        </el-popover>
      </div>
    </section>

    <section v-if="showOverview || showGrowth" class="bottom-grid">
      <article class="panel">
        <div class="panel-head">
          <div>
            <h3>进化日志</h3>
            <span>{{ evolutionLog.length }} 条</span>
          </div>
        </div>
        <div class="log-list">
          <div v-for="item in evolutionLog" :key="item.id" class="log-item">
            <strong>{{ zhText(item.title) }}</strong>
            <span>{{ zhText(item.detail) }}</span>
          </div>
        </div>
        <el-empty v-if="!evolutionLog.length" :image-size="60" description="暂无进化记录" />
      </article>

      <article class="panel">
        <div class="panel-head">
          <div>
            <h3>真实数据依据</h3>
            <span>不使用虚拟价格；不可用的数据源会被排除。</span>
          </div>
        </div>
        <div class="source-list">
          <div v-for="source in dataSources" :key="source.name">
            <strong>{{ source.name }}</strong>
            <span>{{ source.status }}</span>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Delete, Plus, Refresh, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { analyzeStock, getAiLabIterations, getAiLabState, saveAiLabIteration, saveAiLabState } from '@/api/ai'
import { getRealtimeQuote, getSinaAStocks } from '@/api/stock'
import { getGoldLatest } from '@/api/gold'
import { getFundList } from '@/api/fund'
import type { AiAnalysisResponse, CandidateStrategy } from '@/types'

type AssetType = 'stock' | 'gold' | 'fund'
type Signal = 'BUY' | 'SELL' | 'HOLD'
type RankKey = 'bronze' | 'silver' | 'gold' | 'platinum' | 'king'

interface LabAsset {
  id: string
  type: AssetType
  code: string
  name: string
  price: number
  changePct: number
  aiScore: number
  sentimentScore: number
  techScore: number
  source: string
  reason: string
  analysis?: AiAnalysisResponse | null
}

interface LabExperiment {
  id: string
  assetId: string
  assetType: AssetType
  assetCode: string
  assetName: string
  strategyName: string
  style: string
  signal: Signal
  score: number
  returnPct: number
  drawdownPct: number
  winRate: number
  position: number
  rank: RankKey
  generation: number
  entryRule: string
  exitRule: string
  reason: string
  mutation: string
  custom: boolean
  factorScores: Array<{ name: string; score: number; reason: string }>
  holdingPeriod?: string
  tradeLessons?: string[]
  lastTradeResult?: string
}

interface SimulatedTrade {
  id: string
  experimentId: string
  assetCode: string
  assetName: string
  strategyName: string
  action: string
  status: string
  generation: number
  buyPrice: number
  plannedSellPrice: number
  sellPrice: number
  amount: number
  quantity: number
  fee?: number
  profit: number
  currentPrice?: number
  floatingProfit?: number
  floatingProfitPct?: number
  holdingGenerations?: number
  holdingPeriod?: string
  bucketName?: string
  bucketRatio?: number
  closeReason?: string
  closedGeneration?: number
  createdAt: string
  closedAt?: string
}

interface PortfolioItem {
  experimentId: string
  assetCode: string
  assetName: string
  strategyName: string
  score: number
  rank: RankKey | string
  signal?: Signal | string
  targetAmount: number
  decision: string
}

interface PortfolioBucket {
  name: string
  ratio: number
  targetAmount: number
  maxPositions: number
  description: string
  items: PortfolioItem[]
}

interface PortfolioPlan {
  generatedAt: string
  capital: number
  summary: string
  buckets: PortfolioBucket[]
}

interface CustomStrategy {
  id: string
  name: string
  style: string
  rule: string
}

interface ResearchFocus {
  id?: string
  target: string
  direction: string
  updatedAt?: string
}

interface BlacklistItem {
  code: string
  name: string
  reason: string
  createdAt: string
}

const rounds = ref(6)
const capital = ref(100000)
const intervalMinutes = ref(5)
const loading = ref(false)
const autoRunning = ref(false)
const generation = ref(0)
const iterationCount = ref(0)
const enabledAssets = ref<AssetType[]>(['stock', 'gold', 'fund'])
const assets = ref<LabAsset[]>([])
const experiments = ref<LabExperiment[]>([])
const evolutionLog = ref<Array<{ id: string; title: string; detail: string }>>([])
const iterationHistory = ref<any[]>([])
const simulatedTrades = ref<SimulatedTrade[]>([])
const customStrategies = ref<CustomStrategy[]>([])
const portfolioPlan = ref<PortfolioPlan | null>(null)
const researchFocuses = ref<ResearchFocus[]>([])
const blacklistItems = ref<BlacklistItem[]>([])
const LAB_STATE_STORAGE_KEY = 'lianghua_ai_lab_state'

const customForm = reactive({
  title: '',
  content: ''
})

const researchForm = reactive({
  target: '',
  direction: ''
})

const router = useRouter()
const route = useRoute()

const ranks = [
  { key: 'bronze', name: '青铜', range: '亏损或低效' },
  { key: 'silver', name: '白银', range: '稳住风险' },
  { key: 'platinum', name: '铂金', range: '正收益' },
  { key: 'gold', name: '黄金', range: '高收益低回撤' },
  { key: 'king', name: '王者', range: '最高效策略' }
] as const

const sortedAssets = computed(() => [...assets.value].sort((a, b) => b.aiScore - a.aiScore))
const sortedExperiments = computed(() => [...experiments.value].sort((a, b) => b.score - a.score))
const topFiveStrategies = computed(() => sortedExperiments.value.slice(0, 5))
const champion = computed(() => sortedExperiments.value[0] || null)
const labView = computed(() => {
  if (route.path.endsWith('/growth')) return 'growth'
  if (route.path.endsWith('/research')) return 'research'
  if (route.path.endsWith('/portfolio')) return 'portfolio'
  return 'overview'
})
const showOverview = computed(() => labView.value === 'overview')
const showGrowth = computed(() => labView.value === 'growth')
const showResearch = computed(() => labView.value === 'research')
const showPortfolio = computed(() => labView.value === 'portfolio')
const labPageIntro = computed(() => {
  if (showGrowth.value) return '查看模型学到了什么、情绪如何变化、哪些标的被拉黑或继续观察。'
  if (showResearch.value) return '添加多个股票、基金、金属研究任务，添加多少个就会同时研究多少个。'
  if (showPortfolio.value) return '查看当前资金怎么分配、前五组合、持仓周期、买入卖出依据。'
  return '自动选股票、黄金、基金，运行策略买卖模拟，并按收益持续进化。'
})
const labEntries = [
  { title: '成长记录', desc: '模型学到了什么、情绪和黑名单', path: '/ai-lab/growth' },
  { title: '研究控制', desc: '多标的研究任务和自定义方向', path: '/ai-lab/research' },
  { title: '组合方案', desc: '资金比例、前五组合和交易依据', path: '/ai-lab/portfolio' }
]
const activePortfolioPlan = computed(() => portfolioPlan.value?.buckets?.length ? portfolioPlan.value : buildPortfolioPlan())
const bestReturn = computed(() => champion.value?.returnPct || 0)
const moodInfo = computed(() => {
  const openTrades = simulatedTrades.value.filter((trade) => trade.status === '持仓中')
  const totalFloating = openTrades.reduce((sum, trade) => sum + Number(trade.floatingProfit || 0), 0)
  const weakCount = sortedExperiments.value.filter((item) => item.score < 42 || item.drawdownPct >= 10).length
  const strongCount = sortedExperiments.value.filter((item) => item.score >= 75 && item.returnPct > 0).length
  if (weakCount >= 5 || totalFloating < -capital.value * 0.03) return { name: '完全失控', type: 'danger' as const, reason: '亏损和回撤连续扩大，进入恐慌风控，允许拉黑标的。' }
  if (weakCount >= 2 || totalFloating < 0) return { name: '有点失控', type: 'warning' as const, reason: '局部策略低于预期，先降仓观察，不急着换手。' }
  if (strongCount >= 3) return { name: '尽在掌控', type: 'success' as const, reason: '多组策略符合预期，继续按周期持有验证。' }
  return { name: '基本符合预期', type: 'primary' as const, reason: '组合仍在学习，暂不频繁交易。' }
})
const currentDecision = computed(() => {
  const buys = activePortfolioPlan.value.buckets.flatMap((bucket) => bucket.items.map((item) => ({ ...item, bucketName: bucket.name }))).filter((item) => item.signal === 'BUY')
  const holds = activePortfolioPlan.value.buckets.flatMap((bucket) => bucket.items.map((item) => ({ ...item, bucketName: bucket.name }))).filter((item) => item.signal !== 'BUY')
  const openCount = simulatedTrades.value.filter((trade) => trade.status === '持仓中').length
  return {
    title: buys.length ? `当前优先方案：${buys.map((item) => `${item.bucketName}买入${item.assetName}`).slice(0, 3).join('，')}` : '当前没有新的买入动作，保持观察',
    detail: `${moodInfo.value.reason} 现在持仓 ${openCount} 个，交易周期未到前不频繁卖出；新方案只有在旧方案持续亏损、分组超额或分数差距明显时才替换。`,
    points: [
      `研究任务：${researchFocusSummary()}`,
      `交易成本：每次买卖按5元成本意识处理，避免5分钟内无意义换手`,
      holds.length ? `观察候选：${holds.slice(0, 2).map((item) => item.assetName).join('、')}` : '候选均已达到买入条件'
    ]
  }
})
const learningInsights = computed(() => {
  const lessons = sortedExperiments.value.flatMap((item) => (item.tradeLessons || []).map((lesson) => ({ asset: item.assetName, lesson }))).slice(0, 4)
  const bucketText = activePortfolioPlan.value.buckets.map((bucket) => `${bucket.name}${bucket.ratio}%`).join('、')
  const insights = [
    { title: '组合结构', detail: `模型当前按 ${bucketText} 分配资金，先做组合前景判断，再决定是否买卖。` },
    { title: '交易节奏', detail: '短线、事件、趋势、自定义策略都有最小持有周期；定时迭代只学习，不再每代发交易邮件。' },
    { title: '当前情绪', detail: `${moodInfo.value.name}：${moodInfo.value.reason}` }
  ]
  const focusInsights = researchFocuses.value.slice(0, 4).map((item) => ({
    title: `指定研究：${item.target || '全局方向'}`,
    detail: item.direction || '等待补充方向，先按历史规律、新闻舆情、资金流和技术面持续研究。'
  }))
  return [...focusInsights, ...insights, ...lessons.map((item) => ({ title: `${item.asset}经验`, detail: zhText(item.lesson) }))].slice(0, 8)
})
const targetArchives = computed(() => sortedAssets.value.slice(0, 12).map((asset) => {
  const related = sortedExperiments.value.filter((item) => item.assetCode === asset.code)
  const best = related[0]
  const openTrade = simulatedTrades.value.find((trade) => trade.assetCode === asset.code && trade.status === '持仓中')
  const riskScore = safeScore(100 - Math.abs(asset.changePct) * 8 - (best?.drawdownPct || 0) * 3)
  const mood = riskScore < 45 ? '完全失控' : riskScore < 60 ? '有点失控' : (best?.score || asset.aiScore) >= 75 ? '尽在掌控' : '基本符合预期'
  const moodType = mood === '完全失控' ? 'danger' : mood === '有点失控' ? 'warning' : mood === '尽在掌控' ? 'success' : 'primary'
  return {
    code: asset.code,
    name: asset.name,
    type: asset.type,
    mood,
    moodType,
    techScore: asset.techScore,
    sentimentScore: asset.sentimentScore,
    riskScore,
    profile: `${asset.name} 当前涨跌 ${formatPercent(asset.changePct)}，技术分 ${asset.techScore}，舆情分 ${asset.sentimentScore}。${best ? `最适配策略是「${displayStrategyName(best)}」，综合分 ${best.score}，回撤 ${formatPercent(best.drawdownPct)}。` : '正在建立长期档案。'}`,
    nextAction: openTrade
      ? `已持仓，按${openTrade.holdingPeriod || '策略周期'}继续观察，未到卖出条件不重复买入。`
      : best?.signal === 'BUY'
        ? `满足候选买入条件，等待组合资金比例和同组冠军比较后执行。`
        : `暂不买入，继续收集新闻、舆情、技术和历史周期证据。`
  }
}))
const simulatedPosition = computed(() => champion.value ? capital.value * (champion.value.position / 100) : 0)
const historicalProfit = computed(() => champion.value ? simulatedPosition.value * (champion.value.returnPct / 100) : 0)
const realtimeProfit = computed(() => champion.value ? simulatedPosition.value * ((champion.value.returnPct * 0.38) / 100) : 0)
const futureProfit = computed(() => champion.value ? simulatedPosition.value * ((champion.value.returnPct * 0.62 - champion.value.drawdownPct * 0.4) / 100) : 0)
const dataSources = computed(() => [
  { name: '股票', status: assets.value.some((item) => item.type === 'stock') ? '新浪行情 + 新闻/公告/股吧舆情' : '未选或暂无可用数据' },
  { name: '黄金', status: assets.value.some((item) => item.type === 'gold') ? '新浪公开贵金属报价' : '未选或暂无可用数据' },
  { name: '基金', status: assets.value.some((item) => item.type === 'fund') ? '东方财富/公开基金净值估算' : '未选或暂无可用数据' }
])

onMounted(async () => {
  loadCustomStrategies()
  await loadLabState()
})

onBeforeUnmount(() => {
  stopAutoEvolution()
})

async function runAutoLab() {
  if (!enabledAssets.value.length) {
    ElMessage.warning('请至少选择一种资产')
    return
  }
  loading.value = true
  try {
    assets.value = await scanAssets()
    if (!assets.value.length) {
      experiments.value = []
      ElMessage.warning('没有拿到可实验的真实资产数据')
      return
    }
    experiments.value = seedExperiments(assets.value)
    for (let i = 0; i < rounds.value; i += 1) evolveOnce(false)
    stopAutoEvolution()
    await persistIteration()
    ElMessage.success('智能自动实验完成')
  } catch (error: any) {
    ElMessage.error(error?.message || '智能实验运行失败')
  } finally {
    loading.value = false
  }
}

async function loadLabState() {
  try {
    const [stateRes, iterationRes] = await Promise.all([
      getAiLabState({ silentError: true }),
      getAiLabIterations().catch(() => ({ data: { data: [] } }))
    ])
    const state = stateRes.data?.data
    const iterations = Array.isArray(iterationRes.data?.data) ? iterationRes.data.data : []
    applyIterationHistory(iterations)
    if (state) {
      applyLabState(state)
      if (!experiments.value.length && iterations[0]?.experiments?.length) {
        experiments.value = iterations[0].experiments.map(hydrateExperiment)
      }
      if (!evolutionLog.value.length && iterations.length) {
        evolutionLog.value = iterations.slice(0, 14).map((item: any) => ({
          id: String(item.id || item.createdAt || item.generation),
          title: `后台第 ${item.generation} 代：${item.champion?.strategyName || '策略迭代'}`,
          detail: `${item.champion?.assetName || '资产池'} 收益 ${formatPercent(item.champion?.returnPct || 0)}，回撤 ${formatPercent(item.champion?.drawdownPct || 0)}。`
        }))
      }
      saveLabStateLocal(state)
      return
    }
  } catch {
    // 线上状态不可用时不阻塞实验室本身
  }
  const localState = loadLabStateLocal()
  if (localState) applyLabState(localState)
}

function applyIterationHistory(items: any[]) {
  iterationHistory.value = items.slice(0, 20)
}

function applyLabState(state: any) {
  generation.value = Number(state.generation || 0)
  iterationCount.value = Number(state.iterationCount || state.generation || 0)
  capital.value = Number(state.capital || capital.value)
  intervalMinutes.value = Number(state.intervalMinutes || intervalMinutes.value)
  assets.value = Array.isArray(state.assets) ? state.assets : []
  experiments.value = Array.isArray(state.experiments) ? state.experiments.map(hydrateExperiment) : []
  evolutionLog.value = Array.isArray(state.evolutionLog) ? state.evolutionLog : []
  simulatedTrades.value = Array.isArray(state.simulatedTrades) ? state.simulatedTrades : []
  portfolioPlan.value = normalizePortfolioPlan(state.portfolioPlan)
  researchFocuses.value = normalizeResearchFocuses(state)
  blacklistItems.value = Array.isArray(state.blacklistItems) ? state.blacklistItems : []
  researchForm.target = ''
  researchForm.direction = ''
  applyAllResearchFocuses()
  if (Array.isArray(state.customStrategies)) {
    customStrategies.value = state.customStrategies
    saveCustomStrategies()
  }
}

function saveLabStateLocal(state: any) {
  window.localStorage.setItem(LAB_STATE_STORAGE_KEY, JSON.stringify(state))
}

function loadLabStateLocal() {
  const raw = window.localStorage.getItem(LAB_STATE_STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function hydrateExperiment(item: LabExperiment): LabExperiment {
  const normalized = {
    ...item,
    factorScores: Array.isArray(item.factorScores) ? item.factorScores : factorScoresFor(item)
  }
  normalized.strategyName = displayStrategyName(normalized)
  return normalized
}

function asArray<T = any>(value: T | T[] | null | undefined): T[] {
  if (Array.isArray(value)) return value
  if (value == null) return []
  return [value]
}

function normalizePortfolioPlan(plan: any): PortfolioPlan | null {
  if (!plan?.buckets) return null
  const buckets = asArray(plan.buckets).map((bucket: any) => ({
    name: String(bucket.name || ''),
    ratio: Number(bucket.ratio || 0),
    targetAmount: Number(bucket.targetAmount || 0),
    maxPositions: Number(bucket.maxPositions || 0),
    description: String(bucket.description || ''),
    items: asArray(bucket.items).map((item: any) => ({
      experimentId: String(item.experimentId || ''),
      assetCode: String(item.assetCode || ''),
      assetName: String(item.assetName || ''),
      strategyName: String(item.strategyName || ''),
      score: Number(item.score || 0),
      rank: String(item.rank || 'bronze'),
      signal: String(item.signal || 'HOLD'),
      targetAmount: Number(item.targetAmount || 0),
      decision: String(item.decision || '')
    }))
  }))
  return {
    generatedAt: String(plan.generatedAt || new Date().toISOString()),
    capital: Number(plan.capital || capital.value),
    summary: String(plan.summary || '组合按稳健、激进、避险三块分配资金，并在每块内部挑选候选。'),
    buckets
  }
}

function normalizeResearchFocuses(state: any): ResearchFocus[] {
  const list = Array.isArray(state?.researchFocuses)
    ? state.researchFocuses
    : state?.researchFocus
      ? [state.researchFocus]
      : []
  return list
    .map((item: any, index: number) => ({
      id: String(item?.id || `focus-${item?.updatedAt || Date.now()}-${index}`),
      target: String(item?.target || '').trim(),
      direction: String(item?.direction || '').trim(),
      updatedAt: String(item?.updatedAt || new Date().toISOString())
    }))
    .filter((item) => item.target || item.direction)
}

function researchFocusKey(item: ResearchFocus) {
  return item.id || `${item.target}-${item.updatedAt}`
}

function researchFocusSummary() {
  if (!researchFocuses.value.length) return '全市场组合筛选 收益优先，同时记录风险教训'
  return researchFocuses.value
    .slice(0, 5)
    .map((item) => `${item.target || '全局'}：${item.direction || '持续研究'}`)
    .join('；')
}

function focusMatchesExperiment(focus: ResearchFocus, item: Pick<LabExperiment, 'assetCode' | 'assetName'>) {
  const target = focus.target.trim()
  if (!target) return true
  return item.assetCode.includes(target) || item.assetName.includes(target)
}

function applyFocusToExperiments(focus: ResearchFocus) {
  const direction = focus.direction || '继续深化该标的的历史规律、新闻舆情、资金流和技术面'
  for (const item of experiments.value) {
    if (!focusMatchesExperiment(focus, item)) continue
    item.mutation = `用户指定研究任务：${focus.target || '全局方向'}。重点：${direction}。`
    item.factorScores = factorScoresFor(item)
  }
}

function applyAllResearchFocuses() {
  for (const focus of researchFocuses.value) applyFocusToExperiments(focus)
}

async function persistLabState(options: { replaceResearchFocuses?: boolean } = {}) {
  const payload = {
    generation: generation.value,
    iterationCount: iterationCount.value,
    capital: capital.value,
    intervalMinutes: intervalMinutes.value,
    assets: assets.value,
    experiments: experiments.value,
    customStrategies: customStrategies.value,
    evolutionLog: evolutionLog.value,
    simulatedTrades: simulatedTrades.value,
    portfolioPlan: activePortfolioPlan.value,
    researchFocuses: researchFocuses.value,
    researchFocus: researchFocuses.value[0] || null,
    researchFocusesReplace: options.replaceResearchFocuses === true,
    blacklistItems: blacklistItems.value,
    champion: champion.value,
    lastRunAt: new Date().toISOString()
  }
  saveLabStateLocal(payload)
  await saveAiLabState(payload, { silentError: true })
}

async function persistIteration() {
  iterationCount.value = Math.max(iterationCount.value + 1, generation.value)
  const payload = {
    generation: generation.value,
    iterationCount: iterationCount.value,
    capital: capital.value,
    intervalMinutes: intervalMinutes.value,
    assets: assets.value,
    experiments: experiments.value,
    evolutionLog: evolutionLog.value,
    simulatedTrades: simulatedTrades.value,
    portfolioPlan: activePortfolioPlan.value,
    researchFocuses: researchFocuses.value,
    researchFocus: researchFocuses.value[0] || null,
    blacklistItems: blacklistItems.value,
    champion: champion.value
  }
  saveLabStateLocal({
    ...payload,
    lastRunAt: new Date().toISOString()
  })
  await saveAiLabIteration(payload, { silentError: true })
}

async function scanAssets() {
  const tasks: Array<Promise<LabAsset[]>> = []
  if (enabledAssets.value.includes('stock')) tasks.push(scanStocks())
  if (enabledAssets.value.includes('gold')) tasks.push(scanGold())
  if (enabledAssets.value.includes('fund')) tasks.push(scanFunds())
  const groups = await Promise.allSettled(tasks)
  return groups
    .flatMap((group) => group.status === 'fulfilled' ? group.value : [])
    .filter((item) => item.price > 0)
    .sort((a, b) => b.aiScore - a.aiScore)
    .slice(0, 12)
}

async function scanStocks(): Promise<LabAsset[]> {
  let list: any[] = []
  try {
    const res = await getSinaAStocks(1, 8, { silentError: true })
    list = Array.isArray(res.data?.data) ? res.data.data : []
  } catch {
    list = await scanStockFallbackQuotes()
  }
  if (!list.length) {
    list = await scanStockFallbackQuotes()
  }
  const top = list
    .map((item: any) => ({
      code: String(item.code || ''),
      name: String(item.name || item.code || ''),
      price: Number(item.current || item.currentPrice || 0),
      changePct: Number(item.changePercent || 0)
    }))
    .filter((item: any) => item.code && item.price > 0)
    .sort((a: any, b: any) => Math.abs(b.changePct) - Math.abs(a.changePct))
    .slice(0, 4)

  const analyzed = await Promise.all(top.map(async (item: any) => {
    try {
      const ai = (await analyzeStock({ stockCode: item.code, configId: 1 }, { silentError: true })).data?.data as AiAnalysisResponse
      return stockAsset(item, ai)
    } catch {
      return stockAsset(item, null)
    }
  }))
  return analyzed
}

async function scanStockFallbackQuotes(): Promise<any[]> {
  const codes = [
    '600519', '600036', '601318', '600276', '000858', '002594',
    '300033', '300059', '300274', '300308', '300750', '300760',
    '688008', '688036', '688111', '688223', '688599', '688981'
  ]
  const rows = await Promise.allSettled(codes.map(async (code) => {
    const data = (await getRealtimeQuote(code, { silentError: true })).data?.data
    return {
      code: String(data?.code || code),
      name: String(data?.name || code),
      current: Number(data?.currentPrice || 0),
      currentPrice: Number(data?.currentPrice || 0),
      changePercent: Number(data?.changePercent || 0)
    }
  }))
  return rows
    .filter((row): row is PromiseFulfilledResult<any> => row.status === 'fulfilled')
    .map((row) => row.value)
    .filter((item) => item.code && Number(item.current || item.currentPrice || 0) > 0)
}

function stockAsset(item: any, ai: AiAnalysisResponse | null): LabAsset {
  const techScore = safeScore(ai?.techScore ?? marketTechScore(item.changePct))
  const sentimentScore = safeScore(ai?.sentimentScore ?? 50)
  const aiScore = safeScore(ai?.score ?? Math.round(techScore * 0.65 + sentimentScore * 0.35))
  return {
    id: `stock-${item.code}`,
    type: 'stock',
    code: item.code,
    name: ai?.stockName || item.name,
    price: item.price,
    changePct: item.changePct,
    aiScore,
    sentimentScore,
    techScore,
    source: '新浪行情 + 东方财富/新浪新闻 + 巨潮公告 + 股吧舆情',
    reason: ai ? '已纳入智能舆情综合研判' : '已纳入真实行情，舆情暂不可用',
    analysis: ai
  }
}

async function scanGold(): Promise<LabAsset[]> {
  const codes = ['hf_GC', 'hf_XAU']
  const rows = await Promise.all(codes.map(async (code) => {
    try {
      const data = (await getGoldLatest(code, { silentError: true })).data?.data
      const price = Number(data?.price || 0)
      const changePct = Number(data?.changePercent || 0)
      return {
        id: `gold-${code}`,
        type: 'gold' as AssetType,
        code,
        name: data?.productName || (code === 'hf_GC' ? 'COMEX黄金' : '伦敦金'),
        price,
        changePct,
        aiScore: safeScore(marketTechScore(changePct) + 4),
        sentimentScore: 50,
        techScore: marketTechScore(changePct),
        source: '新浪公开贵金属报价',
        reason: '按真实黄金报价和趋势强度进入策略实验'
      }
    } catch {
      return null
    }
  }))
  return rows.filter(Boolean) as LabAsset[]
}

async function scanFunds(): Promise<LabAsset[]> {
  try {
    const res = await getFundList({ page: 1, pageSize: 8 }, { silentError: true })
    const data = res.data?.data
    const list = Array.isArray(data?.list) ? data.list : Array.isArray(data) ? data : []
    return list.map((item: any) => {
      const changePct = Number(item.changePercent || item.gszzl || 0)
      const price = Number(item.nav || item.price || 0)
      return {
        id: `fund-${item.code}`,
        type: 'fund' as AssetType,
        code: String(item.code || ''),
        name: String(item.name || item.code || ''),
        price,
        changePct,
        aiScore: safeScore(marketTechScore(changePct) + (String(item.fundType || '').includes('指数') ? 3 : 0)),
        sentimentScore: 50,
        techScore: marketTechScore(changePct),
        source: '公开基金净值/估算数据',
        reason: `${item.fundType || '基金'}按真实净值变化进入策略实验`
      }
    }).filter((item: LabAsset) => item.code && item.price > 0)
  } catch {
    return []
  }
}

function seedExperiments(inputAssets: LabAsset[]) {
  const result: LabExperiment[] = []
  for (const asset of inputAssets) {
    const apiStrategies = asset.analysis?.candidateStrategies || autoStrategies(asset)
    apiStrategies.slice(0, 3).forEach((strategy, index) => {
      result.push(toExperiment(asset, strategy, `auto-${asset.id}-${index}`, false))
    })
    customStrategies.value.forEach((strategy) => {
      result.push(customToExperiment(asset, strategy))
    })
  }
  return result
}

function autoStrategies(asset: LabAsset): CandidateStrategy[] {
  return [
    {
      name: '智能趋势突破策略',
      style: 'trend',
      signal: asset.aiScore >= 62 ? 'BUY' : asset.aiScore <= 42 ? 'SELL' : 'HOLD',
      score: asset.aiScore,
      expectedReturnScore: safeScore(asset.aiScore + 4),
      riskScore: safeScore(88 - Math.abs(asset.changePct) * 3),
      sentimentFitScore: asset.sentimentScore,
      suggestedPosition: asset.aiScore >= 65 ? '轻仓买入' : '观察',
      entryRule: '综合分高于65且价格趋势继续确认时买入',
      exitRule: '收益回落或综合分低于50时卖出',
      stopLossRule: '最大回撤超过5%止损',
      takeProfitRule: '收益达到8%-12%分批止盈',
      evaluationRule: '每代按收益、回撤、胜率重新评估',
      rationale: '系统自动生成，优先捕捉真实趋势和资金强度'
    },
    {
      name: '智能回撤低吸策略',
      style: 'mean-reversion',
      signal: asset.changePct < -0.5 && asset.aiScore >= 48 ? 'BUY' : 'HOLD',
      score: safeScore(asset.aiScore - 3),
      expectedReturnScore: safeScore(asset.aiScore + 1),
      riskScore: safeScore(92 - Math.abs(asset.changePct) * 2),
      sentimentFitScore: asset.sentimentScore,
      suggestedPosition: '小仓试错',
      entryRule: '回撤后企稳且综合分未恶化时低吸',
      exitRule: '反弹无量或跌破支撑时卖出',
      stopLossRule: '跌破最近低点止损',
      takeProfitRule: '反弹至压力位减仓',
      evaluationRule: '每代按低吸成功率优化阈值',
      rationale: '系统自动生成，适合震荡和回调后的修复行情'
    }
  ]
}

function toExperiment(asset: LabAsset, strategy: CandidateStrategy, id: string, custom: boolean): LabExperiment {
  const baseScore = safeScore(strategy.score)
  const returnPct = simulateReturn(asset, strategy.style, baseScore, custom)
  const drawdownPct = simulateDrawdown(asset, strategy.style, baseScore)
  const score = scoreExperiment(baseScore, returnPct, drawdownPct, strategy.sentimentFitScore)
  return {
    id,
    assetId: asset.id,
    assetType: asset.type,
    assetCode: asset.code,
    assetName: asset.name,
    strategyName: strategy.name,
    style: strategy.style,
    signal: strategy.signal,
    score,
    returnPct,
    drawdownPct,
    winRate: safeScore(48 + returnPct * 3 - drawdownPct * 1.5),
    position: positionFor(score, strategy.signal),
    rank: rankOf(score, returnPct, drawdownPct),
    generation: generation.value,
    entryRule: strategy.entryRule,
    exitRule: strategy.exitRule,
    reason: strategy.rationale,
    mutation: '初始策略入池',
    custom,
    factorScores: factorScoresFor({
      assetType: asset.type,
      assetCode: asset.code,
      assetName: asset.name,
      strategyName: strategy.name,
      style: strategy.style,
      signal: strategy.signal,
      score,
      returnPct,
      drawdownPct,
      winRate: safeScore(48 + returnPct * 3 - drawdownPct * 1.5),
      position: positionFor(score, strategy.signal),
      techScore: asset.techScore,
      sentimentScore: asset.sentimentScore,
      aiScore: asset.aiScore
    } as any)
  }
}

function customToExperiment(asset: LabAsset, custom: CustomStrategy) {
  const score = safeScore(asset.aiScore + styleBias(custom.style))
  const strategy: CandidateStrategy = {
    name: custom.name,
    style: custom.style,
    signal: score >= 62 ? 'BUY' : score <= 40 ? 'SELL' : 'HOLD',
    score,
    expectedReturnScore: score,
    riskScore: 70,
    sentimentFitScore: asset.sentimentScore,
    suggestedPosition: '按自定义规则试验',
    entryRule: custom.rule || '系统从自定义内容中提取买入条件',
    exitRule: '收益回落或综合分恶化时卖出',
    stopLossRule: '严格止损',
    takeProfitRule: '分批止盈',
    evaluationRule: '每代按收益自动调权',
    rationale: custom.rule || '用户自定义策略，由系统放入真实资产池持续实验'
  }
  return toExperiment(asset, strategy, `${custom.id}-${asset.id}`, true)
}

function evolveAgain() {
  if (!experiments.value.length) return
  evolveOnce()
}

let autoTimer: ReturnType<typeof setInterval> | null = null

function toggleAutoEvolution() {
  if (autoRunning.value) {
    stopAutoEvolution()
    return
  }
  autoRunning.value = true
  autoTimer = setInterval(() => {
    if (!experiments.value.length) {
      stopAutoEvolution()
      return
    }
    evolveOnce()
  }, intervalMinutes.value * 60 * 1000)
  ElMessage.success(`已启动自动迭代：每 ${intervalMinutes.value} 分钟运行一代`)
}

function stopAutoEvolution() {
  autoRunning.value = false
  if (autoTimer) {
    clearInterval(autoTimer)
    autoTimer = null
  }
}

function evolveOnce(shouldPersist = true) {
  generation.value += 1
  const previous = champion.value
  experiments.value = experiments.value.map((item, index) => {
    const asset = assets.value.find((target) => target.id === item.assetId)
    const assetMomentum = asset?.changePct || 0
    const learningBoost = item.returnPct > 0 ? 1.2 : -0.8
    const styleBoost = styleBias(item.style) / 5
    const nextReturn = Number((item.returnPct + assetMomentum * 0.18 + learningBoost + styleBoost - item.drawdownPct * 0.08 + ((generation.value + index) % 3 - 1) * 0.35).toFixed(2))
    const nextDrawdown = Number(Math.max(0.2, item.drawdownPct * (item.returnPct > 0 ? 0.92 : 1.06) + Math.abs(assetMomentum) * 0.08).toFixed(2))
    const nextScore = scoreExperiment(item.score, nextReturn, nextDrawdown, asset?.sentimentScore || 50)
    const nextSignal: Signal = nextScore >= 65 ? 'BUY' : nextScore <= 42 ? 'SELL' : 'HOLD'
    const nextRank = item.returnPct > nextReturn ? downgradeRank(rankOf(nextScore, nextReturn, nextDrawdown)) : rankOf(nextScore, nextReturn, nextDrawdown)
    return {
      ...item,
      signal: nextSignal,
      score: nextScore,
      returnPct: nextReturn,
      drawdownPct: nextDrawdown,
      winRate: safeScore(item.winRate * 0.65 + (nextReturn > 0 ? 68 : 42) * 0.35),
      position: positionFor(nextScore, nextSignal),
      rank: nextRank,
      generation: generation.value,
      mutation: mutationText(item, nextScore, nextReturn, nextDrawdown, nextRank),
      factorScores: factorScoresFor({ ...item, score: nextScore, returnPct: nextReturn, drawdownPct: nextDrawdown, signal: nextSignal })
    }
  })
  applyAllResearchFocuses()
  executeSimulatedTrades(previous)

  const next = champion.value
  if (next) {
    const changed = !previous || previous.id !== next.id
    evolutionLog.value.unshift({
      id: `${Date.now()}-${generation.value}`,
      title: changed ? `${next.strategyName} 登顶 ${rankName(next.rank)}` : `${next.strategyName} 稳定进化`,
      detail: `${next.assetName} 第 ${generation.value} 代收益 ${formatPercent(next.returnPct)}，回撤 ${formatPercent(next.drawdownPct)}，动作 ${signalText(next.signal)}。`
    })
    evolutionLog.value = evolutionLog.value.slice(0, 14)
  }
  if (shouldPersist) {
    void persistIteration().catch(() => {
      console.warn('AI lab iteration finished, but persistence endpoint is unavailable.')
    })
  }
}

function addCustomStrategy() {
  const title = customForm.title.trim()
  const content = customForm.content.trim()
  if (!title) {
    ElMessage.warning('请输入策略标题')
    return
  }
  if (!content) {
    ElMessage.warning('请输入自定义内容')
    return
  }
  const style = inferCustomStyle(`${title} ${content}`)
  const item: CustomStrategy = {
    id: `custom-${Date.now()}`,
    name: title,
    style,
    rule: standardizeCustomRule(title, content, style)
  }
  customStrategies.value.unshift(item)
  if (assets.value.length) {
    experiments.value = [...assets.value.map((asset) => customToExperiment(asset, item)), ...experiments.value]
  }
  saveCustomStrategies()
  customForm.title = ''
  customForm.content = ''
  void persistLabState()
  ElMessage.success(`系统已识别为「${styleText(style)}」策略，并加入实验池`)
}

function removeCustomStrategy(id: string) {
  customStrategies.value = customStrategies.value.filter((item) => item.id !== id)
  experiments.value = experiments.value.filter((item) => !item.id.startsWith(id))
  saveCustomStrategies()
}

function loadCustomStrategies() {
  try {
    customStrategies.value = JSON.parse(localStorage.getItem('ai-lab-custom-strategies') || '[]')
  } catch {
    customStrategies.value = []
  }
}

function saveCustomStrategies() {
  localStorage.setItem('ai-lab-custom-strategies', JSON.stringify(customStrategies.value))
}

function marketTechScore(changePct: number) {
  return safeScore(54 + changePct * 5)
}

function simulateReturn(asset: LabAsset, style: string, baseScore: number, custom: boolean) {
  const trend = asset.changePct * (style === 'trend' ? 1.15 : style === 'mean-reversion' ? -0.35 : 0.6)
  const scoreAlpha = (baseScore - 50) / 8
  const customAlpha = custom ? 0.5 : 0
  return Number((trend + scoreAlpha + customAlpha).toFixed(2))
}

function simulateDrawdown(asset: LabAsset, style: string, baseScore: number) {
  const riskControl = style === 'risk-control' ? 1.2 : 0
  return Number(Math.max(0.5, Math.abs(asset.changePct) * 0.55 + (100 - baseScore) / 18 - riskControl).toFixed(2))
}

function scoreExperiment(baseScore: number, returnPct: number, drawdownPct: number, sentimentScore: number) {
  return safeScore(baseScore * 0.42 + (50 + returnPct * 4) * 0.34 + (100 - drawdownPct * 5) * 0.16 + sentimentScore * 0.08)
}

function rankOf(score: number, returnPct = 0, drawdownPct = 0): RankKey {
  if (score >= 88 && returnPct >= 8 && drawdownPct <= 4) return 'king'
  if (score >= 75 && returnPct >= 4) return 'gold'
  if (score >= 60 && returnPct >= 0) return 'platinum'
  if (score >= 45) return 'silver'
  return 'bronze'
}

function downgradeRank(rank: RankKey): RankKey {
  if (rank === 'king') return 'gold'
  if (rank === 'gold') return 'platinum'
  if (rank === 'platinum') return 'silver'
  if (rank === 'silver') return 'bronze'
  return 'bronze'
}

function mutationText(item: LabExperiment, score: number, returnPct: number, drawdownPct: number, rank: RankKey) {
  if (rank === 'king') return '王者突变：保留核心规则，提高资金利用率'
  if (returnPct > item.returnPct && drawdownPct <= item.drawdownPct) return '高效突变：收益上升且回撤受控，提升权重'
  if (returnPct > item.returnPct) return '收益突变：保留入场逻辑，强化止盈'
  if (drawdownPct > item.drawdownPct) return '防守突变：降低仓位，收紧止损'
  if (score > item.score) return '稳定突变：小幅优化阈值'
  return '淘汰压力：降低权重，等待下一代验证'
}

function factorScoresFor(item: Partial<LabExperiment> & AnyScoreSource) {
  const asset = assets.value.find((target) => target.id === item.assetId || target.code === item.assetCode)
  const techScore = safeScore(item.techScore ?? asset?.techScore ?? item.score ?? 50)
  const sentimentScore = safeScore(item.sentimentScore ?? asset?.sentimentScore ?? 50)
  const returnScore = safeScore(50 + Number(item.returnPct || 0) * 5)
  const drawdownScore = safeScore(100 - Number(item.drawdownPct || 0) * 8)
  const portfolioScore = safeScore((item.score || 0) * 0.7 + drawdownScore * 0.3)
  return [
    {
      name: '技术面',
      score: techScore,
      reason: `依据真实行情动量、趋势强弱和${item.signal === 'BUY' ? '买入' : item.signal === 'SELL' ? '卖出' : '观望'}信号评分。`
    },
    {
      name: '新闻/舆情',
      score: sentimentScore,
      reason: '结合新闻情绪、公告事件和公开社区影响力；雪球大V需授权后可提高精度。'
    },
    {
      name: '历史盈利',
      score: returnScore,
      reason: `当前策略模拟收益 ${formatPercent(Number(item.returnPct || 0))}，收益越高评分越高。`
    },
    {
      name: '回撤控制',
      score: drawdownScore,
      reason: `当前最大回撤 ${formatPercent(Number(item.drawdownPct || 0))}，回撤越低评分越高。`
    },
    {
      name: '组合效率',
      score: portfolioScore,
      reason: `综合分、仓位和风险共同决定，当前建议仓位 ${item.position || 0}%。`
    }
  ]
}

function assetFactorScores(asset: LabAsset) {
  return [
    {
      name: '技术面',
      score: asset.techScore,
      reason: `真实行情涨跌幅 ${formatPercent(asset.changePct)}，用于判断短线趋势、动量和技术强弱。`
    },
    {
      name: '新闻/舆情',
      score: asset.sentimentScore,
      reason: asset.analysis ? '已结合新闻、公告和公开社区舆情。' : '暂未拿到完整舆情，先按中性分处理，后续迭代会继续补充。'
    },
    {
      name: '综合筛选',
      score: asset.aiScore,
      reason: `${asset.reason} 数据源：${asset.source}。`
    },
    {
      name: '波动适配',
      score: safeScore(90 - Math.abs(asset.changePct) * 6),
      reason: '波动越可控，越适合进入模拟组合；高波动资产会降低初始仓位。'
    },
    {
      name: '策略扩展',
      score: safeScore(asset.aiScore * 0.7 + 18),
      reason: '该标的可同时参与趋势、低吸、事件驱动和自定义策略竞争。'
    }
  ]
}

interface AnyScoreSource {
  techScore?: number
  sentimentScore?: number
  aiScore?: number
}

function goAssetDetail(item: LabExperiment) {
  if (item.assetType === 'stock') {
    router.push(`/stock/${item.assetCode}`)
  } else if (item.assetType === 'fund') {
    router.push(`/fund/${item.assetCode}`)
  } else {
    router.push(`/gold?code=${encodeURIComponent(item.assetCode)}`)
  }
}

function goAssetDetailByAsset(asset: LabAsset) {
  if (asset.type === 'stock') {
    router.push(`/stock/${asset.code}`)
  } else if (asset.type === 'fund') {
    router.push(`/fund/${asset.code}`)
  } else {
    router.push(`/gold?code=${encodeURIComponent(asset.code)}`)
  }
}

function goPortfolioItem(item: PortfolioItem) {
  const target = experiments.value.find((experiment) => experiment.id === item.experimentId || experiment.assetCode === item.assetCode)
  if (target) {
    goAssetDetail(target)
    return
  }
  router.push(`/stock/${item.assetCode}`)
}

function goTradeItem(trade: SimulatedTrade) {
  const target = experiments.value.find((experiment) => experiment.id === trade.experimentId || experiment.assetCode === trade.assetCode)
  if (target) {
    goAssetDetail(target)
    return
  }
  router.push(`/stock/${trade.assetCode}`)
}

function goArchiveDetail(item: { code: string; type: AssetType }) {
  if (item.type === 'fund') {
    router.push(`/fund/${item.code}`)
  } else if (item.type === 'gold') {
    router.push(`/gold?code=${encodeURIComponent(item.code)}`)
  } else {
    router.push(`/stock/${item.code}`)
  }
}

function customTitleFromRule(text?: string) {
  const match = String(text || '').match(/智能标准化自定义策略「([^」]+)」/)
  return match?.[1] || ''
}

function strategyNameFromText(text?: string) {
  const title = customTitleFromRule(text)
  if (title) return title
  const value = String(text || '')
  if (value === 'Custom Strategy' || value === '自定义策略') return '自定义策略'
  return zhText(value)
}

function displayStrategyName(item?: Partial<LabExperiment> | null) {
  if (!item) return ''
  const title = customTitleFromRule(item.entryRule || item.reason || item.mutation || '')
  if (title) return title
  const fromSaved = customStrategies.value.find((strategy) => item.id?.startsWith(strategy.id))
  if (fromSaved) return fromSaved.name
  return strategyNameFromText(item.strategyName)
}

function portfolioStrategyName(item: PortfolioItem) {
  const experiment = experiments.value.find((target) => target.id === item.experimentId || target.assetCode === item.assetCode)
  return experiment ? displayStrategyName(experiment) : strategyNameFromText(item.strategyName)
}

function tradeStrategyName(trade: SimulatedTrade) {
  const experiment = experiments.value.find((target) => target.id === trade.experimentId || target.assetCode === trade.assetCode)
  return experiment ? displayStrategyName(experiment) : strategyNameFromText(trade.strategyName)
}

async function applyResearchFocus() {
  const target = researchForm.target.trim()
  const direction = researchForm.direction.trim()
  if (!target && !direction) {
    ElMessage.warning('请填写要研究的标的或学习方向')
    return
  }
  const existingIndex = target
    ? researchFocuses.value.findIndex((item) => item.target === target)
    : -1
  const existing = existingIndex >= 0 ? researchFocuses.value[existingIndex] : null
  const focus: ResearchFocus = {
    id: existing?.id || `focus-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    target,
    direction,
    updatedAt: new Date().toISOString()
  }
  if (existingIndex >= 0) researchFocuses.value.splice(existingIndex, 1, focus)
  else researchFocuses.value.unshift(focus)
  applyFocusToExperiments(focus)
  researchForm.target = ''
  researchForm.direction = ''
  await persistLabState()
  ElMessage.success(existing ? '研究任务已更新，会继续参与后续迭代' : '研究任务已加入，会参与后续每次迭代')
}

async function removeResearchFocus(focus: ResearchFocus) {
  researchFocuses.value = researchFocuses.value.filter((item) => researchFocusKey(item) !== researchFocusKey(focus))
  await persistLabState({ replaceResearchFocuses: true })
  ElMessage.success('研究任务已移除')
}

function goResearchFocus(focus: ResearchFocus) {
  const target = focus.target.trim()
  if (!target) {
    router.push('/ai-lab/research')
    return
  }
  const asset = assets.value.find((item) => item.code.includes(target) || item.name.includes(target))
  const experiment = experiments.value.find((item) => item.assetCode.includes(target) || item.assetName.includes(target))
  const code = asset?.code || experiment?.assetCode || target
  const type = asset?.type || experiment?.assetType
  if (type === 'gold') router.push(`/gold?code=${encodeURIComponent(code)}`)
  else if (type === 'fund') router.push(`/fund?code=${encodeURIComponent(code)}`)
  else if (/^\d{6}$/.test(code)) router.push(`/stock/${code}`)
  else router.push('/ai-lab/research')
}

function refreshBlacklist() {
  const existing = new Set(blacklistItems.value.map((item) => item.code))
  const additions = sortedExperiments.value
    .filter((item) => !existing.has(item.assetCode))
    .filter((item) => item.score <= 38 || (item.drawdownPct >= 12 && item.returnPct < 0))
    .slice(0, 3)
    .map((item) => ({
      code: item.assetCode,
      name: item.assetName,
      reason: `综合分${item.score}，回撤${formatPercent(item.drawdownPct)}，收益${formatPercent(item.returnPct)}，模型情绪进入恐慌/失控观察，暂停主动加仓并记录失败经验。`,
      createdAt: new Date().toISOString()
    }))
  if (additions.length) {
    blacklistItems.value = [...additions, ...blacklistItems.value].slice(0, 12)
  }
}

function inferCustomStyle(text: string) {
  if (/(回撤|低吸|超跌|反弹|支撑|便宜|跌下来)/.test(text)) return 'mean-reversion'
  if (/(新闻|公告|政策|事件|财报|利好|利空|舆情|大V)/.test(text)) return 'event-driven'
  if (/(风控|止损|回撤|仓位|保守|风险)/.test(text)) return 'risk-control'
  if (/(突破|趋势|均线|MACD|放量|强势|追涨)/i.test(text)) return 'trend'
  return 'hybrid'
}

function standardizeCustomRule(title: string, content: string, style: string) {
  const styleName = styleText(style)
  return `智能标准化自定义策略「${title}」：风格=${styleName}。用户原始意图：${content}。实验室会把它拆成技术面、新闻舆情、风险控制、历史盈利和组合效率五个评分角度，并在每代迭代中按盈利优先自动调整仓位、入场阈值和退出阈值。`
}

function positionFor(score: number, signal: Signal) {
  if (signal === 'SELL') return 0
  if (signal === 'HOLD') return score >= 60 ? 20 : 0
  if (score >= 85) return 70
  if (score >= 75) return 50
  if (score >= 65) return 30
  return 10
}

function styleBias(style?: string) {
  if (style === 'trend') return 6
  if (style === 'event-driven') return 4
  if (style === 'risk-control') return 3
  if (style === 'mean-reversion') return 2
  return 0
}

function safeScore(value: any) {
  const n = Number(value)
  return Number.isFinite(n) ? Math.max(0, Math.min(100, Math.round(n))) : 0
}

function formatPrice(value: number) {
  return Number(value || 0).toFixed(value > 100 ? 2 : 4)
}

function formatPercent(value: number) {
  const n = Number(value || 0)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
}

function formatMoney(value: number) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
}

function formatTime(value?: string) {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

function assetPriceFor(item: LabExperiment) {
  return Number(assets.value.find((asset) => asset.id === item.assetId || asset.code === item.assetCode)?.price || 0)
}

function tradePlanFor(item: LabExperiment) {
  const price = assetPriceFor(item)
  const amount = capital.value * (item.position / 100)
  const target = price > 0 ? price * (1 + Math.max(item.returnPct, 1.2) / 100) : 0
  const cycle = holdingPeriodFor(item)
  const buyTime = item.signal === 'BUY' ? `第 ${item.generation} 代形成买入，按${cycle.label}跟踪` : '暂不买入'
  const sellTime = item.signal === 'SELL' ? `第 ${item.generation} 代触发卖出` : `先持有观察，${cycle.label}，达到目标价、周期转弱或风控触发时卖出`
  const action = item.signal === 'BUY'
    ? `当前操作：用 ${formatMoney(amount)} 模拟买入，参考现价 ${formatPrice(price)}，目标卖价 ${formatPrice(target)}。`
    : item.signal === 'SELL'
      ? '当前操作：模型判断应卖出或空仓，已有模拟持仓会在本代卖出。'
      : '当前操作：暂不买入，保持观察，等待分数或趋势确认。'
  return {
    buyTime,
    sellTime,
    buyPrice: price > 0 ? formatPrice(price) : '等待真实报价',
    sellPrice: target > 0 ? formatPrice(target) : '等待真实报价',
    action,
    profitSource: `本代收益来自真实报价 ${formatPrice(price)}、建议仓位 ${item.position}%、策略模拟收益 ${formatPercent(item.returnPct)}、回撤 ${formatPercent(item.drawdownPct)} 和该标的历史交易记忆的综合测算。`
  }
}

function portfolioBucketDefs() {
  return [
    { name: '稳健产品', ratio: 30, maxPositions: 2, description: '优先基金和低回撤策略，用来稳定组合净值。' },
    { name: '激进产品', ratio: 50, maxPositions: 2, description: '优先股票和高分高收益策略，用来争取超额收益。' },
    { name: '避险产品', ratio: 20, maxPositions: 1, description: '优先黄金、白银等金属，用来对冲波动。' }
  ]
}

function bucketNameFor(item: LabExperiment) {
  if (item.assetType === 'gold') return '避险产品'
  if (item.assetType === 'fund' || (item.drawdownPct <= 3.5 && item.style !== 'trend')) return '稳健产品'
  return '激进产品'
}

function buildPortfolioPlan(): PortfolioPlan {
  const ordered = sortedExperiments.value
  const buckets = portfolioBucketDefs().map((bucket) => {
    const seenAssetCodes = new Set<string>()
    const candidates: LabExperiment[] = []
    for (const item of ordered) {
      if (bucketNameFor(item) !== bucket.name || seenAssetCodes.has(item.assetCode)) continue
      seenAssetCodes.add(item.assetCode)
      candidates.push(item)
      if (candidates.length >= bucket.maxPositions) break
    }
    const targetAmount = Number((capital.value * bucket.ratio / 100).toFixed(2))
    const perAmount = candidates.length ? Number((targetAmount / candidates.length).toFixed(2)) : 0
    return {
      ...bucket,
      targetAmount,
      items: candidates.map((item) => ({
        experimentId: item.id,
        assetCode: item.assetCode,
        assetName: item.assetName,
        strategyName: item.strategyName,
        score: item.score,
        rank: item.rank,
        signal: item.signal,
        targetAmount: perAmount,
        decision: item.signal === 'BUY' ? `按${bucket.name}预算纳入买入组合` : '未触发买入，先保留为本组观察候选'
      }))
    }
  })
  return {
    generatedAt: new Date().toISOString(),
    capital: capital.value,
    buckets,
    summary: '组合先按稳健、激进、避险三块分配资金，再在每块内部挑选冠军和候选；新冠军先比较老持仓收益、回撤和周期，再决定替换、部分保留或继续观察。'
  }
}

function executeSimulatedTrades(previousChampion: LabExperiment | null) {
  const now = new Date().toISOString()
  const maxOpenPositions = 5
  const plan = buildPortfolioPlan()
  portfolioPlan.value = plan
  const planItems = plan.buckets.flatMap((bucket) => bucket.items.map((item) => ({
    ...item,
    bucketName: bucket.name,
    bucketRatio: bucket.ratio
  })))

  for (const trade of simulatedTrades.value) {
    if (trade.status !== '持仓中') continue
    const item = experiments.value.find((target) => target.id === trade.experimentId)
    const price = item ? assetPriceFor(item) : 0
    let shouldClose = !item
    let closeReason = item ? '' : '策略已被淘汰'
    if (item && price > 0) {
      const cycle = holdingPeriodFor(item)
      const holdingGenerations = Math.max(0, generation.value - trade.generation)
      const floatingProfit = Number(((price - trade.buyPrice) * trade.quantity - Number(trade.fee || 5)).toFixed(2))
      const floatingProfitPct = trade.buyPrice > 0 ? Number((((price - trade.buyPrice) / trade.buyPrice) * 100).toFixed(2)) : 0
      trade.currentPrice = price
      trade.floatingProfit = floatingProfit
      trade.floatingProfitPct = floatingProfitPct
      trade.holdingGenerations = holdingGenerations
      trade.holdingPeriod = cycle.label
      trade.bucketName = trade.bucketName || bucketNameFor(item)
      const sameBucketNewChampion = planItems.find((target) => target.bucketName === trade.bucketName && target.assetCode !== trade.assetCode && target.score >= item.score + 8)
      if (item.signal === 'SELL' && holdingGenerations >= cycle.min) {
        shouldClose = true
        closeReason = '模型触发卖出信号'
      } else if (trade.plannedSellPrice > 0 && price >= trade.plannedSellPrice && holdingGenerations >= cycle.min) {
        shouldClose = true
        closeReason = '达到计划卖出价'
      } else if (holdingGenerations >= cycle.target && item.score < 55 && item.returnPct < 1) {
        shouldClose = true
        closeReason = '到达策略周期后综合分转弱'
      } else if (holdingGenerations >= cycle.max) {
        shouldClose = true
        closeReason = '达到策略最长周期，落袋复盘'
      } else if (item.drawdownPct >= 8 && holdingGenerations >= cycle.min) {
        shouldClose = true
        closeReason = '回撤扩大触发风控'
      } else if (sameBucketNewChampion && holdingGenerations >= cycle.min) {
        shouldClose = true
        closeReason = '同组新冠军优势明显，调仓释放资金'
      }
    }
    if (shouldClose && price > 0) {
      trade.status = item ? '已卖出' : '已淘汰卖出'
      trade.action = '卖出'
      trade.sellPrice = price
      trade.closedAt = now
      trade.closedGeneration = generation.value
      trade.profit = Number(((price - trade.buyPrice) * trade.quantity - Number(trade.fee || 5) * 2).toFixed(2))
      trade.closeReason = closeReason
      if (item) applyTradeExperience(item, trade, closeReason)
    }
  }

  repairOverAllocatedTrades(now)
  let availableCapital = Math.max(0, capital.value - simulatedTrades.value
    .filter((trade) => trade.status === '持仓中')
    .reduce((sum, trade) => sum + Number(trade.amount || 0), 0))

  for (const target of planItems) {
    const item = experiments.value.find((experiment) => experiment.id === target.experimentId)
    if (!item) continue
    if (item.signal !== 'BUY' || item.position <= 0) continue
    const openCount = simulatedTrades.value.filter((trade) => trade.status === '持仓中').length
    if (openCount >= maxOpenPositions) continue
    if (simulatedTrades.value.some((trade) => trade.experimentId === item.id && trade.status === '持仓中')) continue
    if (simulatedTrades.value.some((trade) => trade.assetCode === item.assetCode && trade.status === '持仓中')) continue
    if (simulatedTrades.value.some((trade) => trade.assetCode === item.assetCode && trade.status !== '持仓中' && generation.value - (trade.closedGeneration || trade.generation) < 24)) continue
    const price = assetPriceFor(item)
    if (price <= 0) continue
    const bucket = plan.buckets.find((itemBucket) => itemBucket.name === target.bucketName)
    const bucketUsed = simulatedTrades.value
      .filter((trade) => trade.status === '持仓中' && trade.bucketName === target.bucketName)
      .reduce((sum, trade) => sum + Number(trade.amount || 0), 0)
    const bucketAvailable = bucket ? Math.max(0, bucket.targetAmount - bucketUsed) : availableCapital
    const amount = Math.min(Number(target.targetAmount || 0), availableCapital, bucketAvailable)
    if (amount < 1000) continue
    const quantity = amount / price
    const cycle = holdingPeriodFor(item)
    simulatedTrades.value.unshift({
      id: `${Date.now()}-${item.id}`,
      experimentId: item.id,
      assetCode: item.assetCode,
      assetName: item.assetName,
      strategyName: item.strategyName,
      action: '买入',
      status: '持仓中',
      generation: generation.value,
      buyPrice: price,
      plannedSellPrice: Number((price * (1 + Math.max(item.returnPct, 1.2) / 100)).toFixed(4)),
      sellPrice: 0,
      amount,
      quantity,
      fee: 5,
      profit: 0,
      currentPrice: price,
      floatingProfit: 0,
      floatingProfitPct: 0,
      holdingGenerations: 0,
      holdingPeriod: cycle.label,
      bucketName: target.bucketName,
      bucketRatio: target.bucketRatio,
      closeReason: '',
      createdAt: now
    })
    availableCapital = Math.max(0, availableCapital - amount)
  }
  simulatedTrades.value = simulatedTrades.value.slice(0, 40)
  refreshBlacklist()
}

function repairOverAllocatedTrades(now: string) {
  const openTrades = simulatedTrades.value
    .filter((trade) => trade.status === '持仓中')
    .sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)))
  const usedCapital = openTrades.reduce((sum, trade) => sum + Number(trade.amount || 0), 0)
  if (usedCapital <= capital.value) return

  let keptCapital = 0
  const keepIds = new Set<string>()
  for (const trade of openTrades) {
    const amount = Number(trade.amount || 0)
    if (keptCapital + amount <= capital.value) {
      keptCapital += amount
      keepIds.add(trade.id)
    }
  }
  for (const trade of openTrades) {
    if (keepIds.has(trade.id)) continue
    trade.status = '资金校准撤销'
    trade.action = '撤销'
    trade.closedAt = now
    trade.closedGeneration = generation.value
    trade.closeReason = '历史重复买入导致资金超限，系统按有限资金规则撤销该模拟持仓，不发送买卖邮件。'
  }
}

function holdingPeriodFor(item: LabExperiment) {
  const text = `${item.strategyName} ${item.entryRule} ${item.exitRule}`
  if (item.style === 'mean-reversion' || /低吸|回撤|反弹|短线/.test(text)) {
    return { min: 36, target: 144, max: 432, label: '低吸周期，至少36代观察，重点144代，最多432代' }
  }
  if (item.style === 'sentiment' || /新闻|舆情|公告|事件/.test(text)) {
    return { min: 24, target: 96, max: 288, label: '事件周期，至少24代观察，重点96代，最多288代' }
  }
  if (item.style === 'custom') {
    return { min: 48, target: 192, max: 576, label: '自定义周期，至少48代验证，重点192代，最多576代' }
  }
  return { min: 72, target: 288, max: 864, label: '趋势周期，至少72代持有验证，重点288代，最多864代' }
}

function applyTradeExperience(item: LabExperiment, trade: SimulatedTrade, closeReason: string) {
  const profit = Number(trade.profit || 0)
  const floatingPct = Number(trade.floatingProfitPct || 0)
  const holdingGenerations = Number(trade.holdingGenerations || 0)
  const resultText = profit > 0 ? '盈利' : profit < 0 ? '亏损' : '持平'
  const summary = `${resultText}经验：持有${holdingGenerations}代，收益率${formatPercent(floatingPct)}，卖出原因：${closeReason}。`
  item.tradeLessons = [summary, ...(item.tradeLessons || [])].slice(0, 5)
  item.lastTradeResult = summary
  if (profit > 0) {
    item.winRate = Math.min(100, item.winRate + 1)
    item.score = Math.min(100, item.score + 1)
    item.mutation = '吸收盈利经验：保留本次入场条件，下一代继续验证周期节奏。'
  } else if (profit < 0) {
    item.winRate = Math.max(0, item.winRate - 1)
    item.score = Math.max(0, item.score - 2)
    item.mutation = '吸收亏损经验：降低仓位或延后买入确认，下一代收紧卖出风控。'
  }
}

function tradeProfitFor(trade: SimulatedTrade) {
  if (trade.status === '持仓中') return Number(trade.floatingProfit || 0)
  return Number(trade.profit || 0)
}

function strategyMemoryFor(item: LabExperiment) {
  const lessons = item.tradeLessons || []
  if (lessons.length) return lessons.map(zhText).join(' ')
  const cycle = holdingPeriodFor(item)
  return `${item.assetName} 正在建立标的记忆：当前采用${cycle.label}，结合历史曲线、实时行情、新闻舆情、资金流和技术面持续学习。`
}

function zhText(value?: string) {
  if (!value) return ''
  return [
    ['AI Trend Breakout', '智能趋势突破'],
    ['AI Pullback Buy', '智能回撤低吸'],
    ['News Sentiment Fusion', '新闻舆情融合'],
    ['Custom Strategy', '自定义策略'],
    ['Local scheduler seeded strategy pool', '本地定时任务已建立策略池'],
    ['Local MySQL scheduled iteration: profit-first tuning, lower drawdown, keep high-score strategy variants.', '本地定时任务迭代：以盈利为主，压低回撤，保留高分策略变体。'],
    ['Initial local scheduled strategy seed.', '本地定时任务初始建池。'],
    ['local scheduled iteration', '本地定时迭代'],
    ['Generated by local scheduler from real public market data.', '由本地定时任务基于真实公开行情生成。'],
    ['Seeded by local scheduler from live public quote API.', '由本地定时任务基于实时公开报价纳入资产池。'],
    ['Live quote plus technical momentum, sentiment weight and risk control confirmation.', '结合实时报价、技术动量、舆情权重和风控确认。'],
    ['Exit when score drops below 50, drawdown expands or live quote momentum reverses.', '当综合分低于50、回撤扩大或实时动量反转时退出。'],
    ['Technical', '技术面'],
    ['Sentiment', '舆情面'],
    ['Risk', '风险控制'],
    ['Based on live quote change percent.', '根据实时涨跌幅判断。'],
    ['Scheduler sentiment weight for public news/sentiment integration.', '定时任务为新闻和舆情融合预留的权重。'],
    ['Lower drawdown receives higher risk score.', '回撤越低，风险分越高。'],
    ['generation', '第'],
    ['return', '收益'],
    ['drawdown', '回撤'],
    ['signal', '动作'],
    ['BUY', '买入'],
    ['SELL', '卖出'],
    ['HOLD', '观望']
  ].reduce((text, pair) => text.split(pair[0]).join(pair[1]), String(value))
}

function rankName(rank?: RankKey | string) {
  if (rank === 'king') return '王者'
  if (rank === 'platinum') return '铂金'
  if (rank === 'gold') return '黄金'
  if (rank === 'silver') return '白银'
  return '青铜'
}

function rankTag(rank?: RankKey) {
  if (rank === 'king') return 'danger'
  if (rank === 'platinum') return 'primary'
  if (rank === 'gold') return 'warning'
  if (rank === 'silver') return 'info'
  return 'success'
}

function assetTag(type: AssetType) {
  if (type === 'stock') return 'primary'
  if (type === 'gold') return 'warning'
  return 'success'
}

function assetTypeText(type?: AssetType) {
  if (type === 'stock') return '股票'
  if (type === 'gold') return '黄金'
  if (type === 'fund') return '基金'
  return '-'
}

function signalText(signal?: Signal | string) {
  if (signal === 'BUY') return '买入'
  if (signal === 'SELL') return '卖出'
  return '观望'
}

function signalClass(signal?: Signal | string) {
  if (signal === 'BUY') return 'up'
  if (signal === 'SELL') return 'down'
  return ''
}

function strategyExplain(style?: string) {
  if (style === 'trend') return '趋势跟随：顺着价格和资金方向做，适合突破和强趋势。'
  if (style === 'mean-reversion') return '回撤低吸：等价格回调到相对便宜区域，确认企稳后试买。'
  if (style === 'event-driven') return '事件驱动：重点看新闻、公告、政策、行业催化带来的短期机会。'
  if (style === 'risk-control') return '风控优先：先控制回撤和仓位，再追求收益。'
  if (style === 'hybrid') return '自定义混合：系统从用户文本中抽取多角度规则，纳入统一迭代标准。'
  return '混合策略：综合技术、新闻、舆情和资产表现动态调权。'
}

function styleText(style?: string) {
  if (style === 'trend') return '趋势跟随'
  if (style === 'mean-reversion') return '低吸反转'
  if (style === 'event-driven') return '事件驱动'
  if (style === 'risk-control') return '风控优先'
  if (style === 'hybrid') return '自定义混合'
  return '混合'
}
</script>

<style scoped lang="scss">
.ai-lab {
  max-width: 1440px;
  display: grid;
  gap: 16px;
}

.lab-header,
.panel,
.rank-track,
.lab-entry,
.kpi {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.lab-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;

  h2 {
    margin: 0 0 4px;
    font-size: 22px;
    color: #1f2d3d;
  }

  p {
    margin: 0;
    color: #606266;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.lab-view-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.lab-entry-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.lab-entry {
  padding: 14px;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;

  strong,
  span {
    display: block;
  }

  strong {
    margin-bottom: 6px;
    color: #1f2d3d;
  }

  span {
    color: #606266;
    line-height: 1.55;
  }

  &:hover {
    border-color: #409eff;
    box-shadow: 0 8px 20px rgba(31, 45, 61, 0.08);
    transform: translateY(-1px);
  }
}

.control-label {
  color: #606266;
  font-size: 12px;
  white-space: nowrap;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kpi {
  padding: 14px;

  span,
  small {
    display: block;
    color: #909399;
  }

  strong {
    display: block;
    margin: 7px 0;
    font-size: 26px;
    color: #1f2d3d;
  }
}

.rank-track {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 12px;
  gap: 10px;
}

.rank-step {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px;
  border-radius: 8px;
  background: #f7f9fc;
  border: 1px solid transparent;

  &.active {
    border-color: #409eff;
    background: #eef6ff;
  }

  small {
    margin-left: auto;
    color: #909399;
  }
}

.rank-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.rank-bronze { background: #9a6a42; }
.rank-silver { background: #8a99ad; }
.rank-gold { background: #d59b2d; }
.rank-platinum { background: #5d7fbf; }
.rank-king { background: #d84d4d; }

.main-grid,
.bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(360px, 0.75fr);
  gap: 16px;
}

.portfolio-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.portfolio-bucket {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  background: #fbfcfe;
}

.bucket-head,
.bucket-money,
.bucket-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.bucket-head {
  margin-bottom: 10px;

  strong {
    display: block;
    color: #303133;
  }

  span {
    display: block;
    margin-top: 4px;
    color: #606266;
    font-size: 12px;
    line-height: 1.45;
  }

  b {
    color: #c2410c;
    font-size: 20px;
  }
}

.bucket-money {
  padding: 8px 0;
  border-top: 1px solid #eef0f4;
  border-bottom: 1px solid #eef0f4;

  span {
    color: #909399;
    font-size: 12px;
  }
}

.bucket-items {
  display: grid;
  gap: 8px;
  margin-top: 10px;

  small {
    color: #909399;
  }
}

.bucket-item {
  align-items: flex-start;

  strong,
  span {
    display: block;
  }

  strong {
    color: #303133;
  }

  span {
    margin-top: 3px;
    color: #606266;
    font-size: 12px;
  }

  em {
    color: #303133;
    font-style: normal;
    white-space: nowrap;
  }
}

.panel {
  padding: 16px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;

  h3 {
    margin: 0 0 4px;
    font-size: 16px;
    color: #303133;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.champion {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e4e7ed;

  h4 {
    margin: 0 0 8px;
    font-size: 22px;
    color: #1f2d3d;
  }

  p {
    margin: 0;
    color: #606266;
    line-height: 1.6;
  }
}

.champion-score {
  text-align: right;

  strong {
    display: block;
    font-size: 42px;
    line-height: 1;
    color: #d84d4d;
  }

  small {
    color: #909399;
  }
}

.trade-plan {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;

  div {
    padding: 12px;
    border-radius: 8px;
    background: #fff;
    border: 1px solid #ebeef5;
  }

  .wide {
    grid-column: span 2;
  }

  span {
    display: block;
    color: #909399;
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 6px;
    color: #303133;
  }

  p {
    margin: 6px 0 0;
    color: #303133;
    line-height: 1.5;
  }
}

.top-five-list,
.trade-ledger {
  display: grid;
  gap: 10px;
}

.top-five-item,
.trade-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;

  b {
    display: grid;
    place-items: center;
    width: 26px;
    height: 26px;
    border-radius: 50%;
    background: #eef6ff;
    color: #2563eb;
  }

  strong,
  span,
  small {
    display: block;
  }

  span,
  small {
    color: #606266;
    line-height: 1.5;
  }

  small {
    font-size: 12px;
  }

  em {
    font-style: normal;
    font-weight: 700;
    white-space: nowrap;
  }
}

.top-five-item {
  cursor: pointer;

  &:hover {
    border-color: #409eff;
    background: #f7fbff;
  }
}

.trade-row {
  grid-template-columns: minmax(0, 1fr) auto;

  small {
    grid-column: 1 / -1;
  }
}

.custom-form {
  display: grid;
  gap: 4px;
}

.custom-list {
  display: grid;
  gap: 8px;
  margin-top: 8px;
}

.custom-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;

  strong,
  span {
    display: block;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.asset-grid,
.experiment-board {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.asset-card,
.experiment-card {
  padding: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.asset-card {
  display: grid;
  gap: 10px;

  &.clickable {
    cursor: pointer;
    transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;

    &:hover {
      border-color: #409eff;
      box-shadow: 0 8px 24px rgba(31, 45, 61, 0.1);
      transform: translateY(-1px);
    }
  }

  strong,
  span {
    display: block;
  }

  p {
    margin: 0;
    color: #606266;
    line-height: 1.5;
    font-size: 12px;
  }
}

.asset-price {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.experiment-card {
  border-left: 4px solid #9a6a42;

  &.clickable {
    cursor: pointer;
    transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;

    &:hover {
      box-shadow: 0 8px 24px rgba(31, 45, 61, 0.1);
      transform: translateY(-1px);
    }
  }

  &.card-silver { border-left-color: #8a99ad; }
  &.card-gold { border-left-color: #d59b2d; }
  &.card-platinum { border-left-color: #5d7fbf; }
  &.card-king { border-left-color: #d84d4d; }

  p {
    margin: 10px 0;
    color: #606266;
    line-height: 1.6;
  }
}

.experiment-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;

  strong,
  span {
    display: block;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.score-row,
.mini-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.score-row div,
.mini-metrics span {
  padding: 7px 8px;
  border-radius: 6px;
  background: #f5f7fa;
  text-align: center;
}

.score-row span {
  display: block;
  color: #909399;
  font-size: 12px;
}

.score-row strong {
  display: block;
  margin-top: 3px;
  color: #1f2d3d;
}

.mini-metrics {
  margin-top: 8px;

  span {
    color: #606266;
    font-size: 12px;
  }
}

.mutation {
  padding: 8px 10px;
  border-radius: 6px;
  background: #f8fafc;
  color: #4b5b70;
  font-size: 12px;
}

.strategy-note {
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fff8e8;
  color: #7a5a1e;
  font-size: 12px;
  line-height: 1.5;
}

.score-detail {
  display: grid;
  gap: 8px;

  h4 {
    margin: 0 0 4px;
    color: #303133;
    font-size: 15px;
  }
}

.score-detail-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px;
  border-radius: 6px;
  background: #f7f9fc;

  strong,
  span {
    display: block;
  }

  strong {
    color: #303133;
  }

  span {
    margin-top: 2px;
    color: #606266;
    font-size: 12px;
    line-height: 1.45;
  }

  b {
    color: #d84d4d;
    white-space: nowrap;
  }
}

.log-list,
.source-list {
  display: grid;
  gap: 8px;
}

.log-item,
.source-list div {
  padding: 10px 12px;
  border-left: 3px solid #409eff;
  background: #f6faff;

  strong,
  span {
    display: block;
  }

  span {
    color: #606266;
    font-size: 12px;
  }
}

.decision-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 16px;
}

.decision-main {
  display: grid;
  gap: 8px;

  strong {
    color: #1f2d3d;
    font-size: 18px;
  }

  p {
    margin: 0;
    color: #4b5563;
    line-height: 1.7;
  }
}

.decision-points,
.learning-list,
.research-task-list,
.blacklist {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.decision-points span,
.learning-item,
.blacklist span {
  padding: 9px 10px;
  border-radius: 6px;
  background: #f6f8fb;
  color: #4b5563;
  line-height: 1.55;
}

.learning-item strong,
.learning-item span {
  display: block;
}

.learning-item strong {
  margin-bottom: 4px;
  color: #1f2d3d;
}

.research-form {
  display: grid;
  gap: 10px;
}

.research-task {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  background: #f9fbfd;
}

.research-task-body {
  display: grid;
  gap: 4px;
  cursor: pointer;

  strong {
    color: #1f2d3d;
  }

  span {
    color: #4b5563;
    line-height: 1.55;
  }

  small {
    color: #909399;
  }
}

.research-task-actions {
  display: flex;
  gap: 6px;
  white-space: nowrap;
}

.blacklist strong {
  color: #d84d4d;
}

.archive-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.archive-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fbfcfe;
  cursor: pointer;

  p {
    margin: 0;
    color: #4b5563;
    line-height: 1.6;
  }

  small {
    color: #606266;
    line-height: 1.5;
  }
}

.archive-head,
.archive-scores {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.archive-head strong {
  color: #1f2d3d;
}

.archive-scores span {
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #4b5563;
  font-size: 12px;
}

.linkish {
  cursor: pointer;
  color: #2b6cb0;
}

.linkish:hover {
  text-decoration: underline;
}

.up { color: #d84d4d !important; }
.down { color: #1f9d66 !important; }

@media (max-width: 1200px) {
  .asset-grid,
  .archive-grid,
  .experiment-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .main-grid,
  .bottom-grid,
  .portfolio-grid,
  .decision-grid,
  .lab-entry-grid,
  .kpi-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .lab-header,
  .champion {
    flex-direction: column;
    align-items: stretch;
  }

  .rank-track,
  .asset-grid,
  .experiment-board,
  .trade-plan,
  .score-row,
  .mini-metrics {
    grid-template-columns: 1fr;
  }

  .research-task {
    grid-template-columns: 1fr;
  }

  .research-task-actions {
    justify-content: flex-start;
  }

  .trade-plan .wide {
    grid-column: auto;
  }
}
</style>
