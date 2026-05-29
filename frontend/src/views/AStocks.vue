<template>
  <div class="market-hub">
    <div class="page-header">
      <div class="header-left">
        <el-icon :size="22" color="#f56c6c"><TrendCharts /></el-icon>
        <span class="page-title">A股市场</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="market-tabs">
      <!-- 行情列表 -->
      <el-tab-pane label="行情列表" name="list">
        <el-card shadow="hover" class="market-card">
          <template #header>
            <div class="card-header">
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

          <el-table
            :data="stockList"
            v-loading="loading"
            stripe
            style="width: 100%"
            @row-click="goToDetail"
            highlight-current-row
            class="stock-table"
          >
            <el-table-column prop="code" label="代码" width="110" />
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
            <el-table-column label="港股对标" width="170" align="right">
              <template #default="{ row }">
                <div v-if="AH_MAP[row.code]" class="hk-compare">
                  <span class="hk-name">{{ AH_MAP[row.code].hkName }}</span>
                  <span v-if="hkPriceCache[AH_MAP[row.code].hkCode]" class="hk-price">
                    HK${{ hkPriceCache[AH_MAP[row.code].hkCode]?.price?.toFixed(2) }}
                  </span>
                  <span v-else class="hk-loading">加载中...</span>
                  <span v-if="getPremium(row.code, row.currentPrice) !== null"
                    class="premium-tag"
                    :class="getPremium(row.code, row.currentPrice)! > 0 ? 'up' : 'down'"
                  >
                    {{ getPremium(row.code, row.currentPrice)!.toFixed(1) }}%
                  </span>
                </div>
              </template>
            </el-table-column>
          </el-table>

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
      </el-tab-pane>

      <!-- 大盘指数 -->
      <el-tab-pane label="大盘指数" name="indices">
        <div class="indices-section">
          <div class="section-header">
            <span class="section-title">A股大盘指数</span>
            <el-button text type="primary" @click="loadIndices" :loading="indicesLoading">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>

          <div v-if="indicesLoading" class="loading-placeholder">
            <el-skeleton :rows="3" animated />
          </div>

          <div v-else-if="indices.length === 0" class="empty-placeholder">
            <el-empty description="暂无指数数据" />
          </div>

          <div v-else class="indices-grid">
            <div
              v-for="idx in indices"
              :key="idx.code"
              class="index-card"
              :class="{ positive: idx.change > 0, negative: idx.change < 0 }"
            >
              <div class="index-main">
                <div class="index-header">
                  <span class="index-name">{{ idx.name }}</span>
                  <span class="index-code">{{ idx.code }}</span>
                </div>
                <div class="index-price">
                  <span class="price-value">{{ formatNumber(idx.price || idx.current) }}</span>
                  <div class="change-box">
                    <span class="change-amount">
                      {{ (idx.change || 0) >= 0 ? '+' : '' }}{{ formatNumber(idx.change || 0) }}
                    </span>
                    <span class="change-percent">
                      {{ (idx.changePercent || 0) >= 0 ? '+' : '' }}{{ safeScore(idx.changePercent || 0).toFixed(2) }}%
                    </span>
                  </div>
                </div>
                <div class="index-details">
                  <div class="detail-row">
                    <div class="detail-item">
                      <span class="label">今开</span>
                      <span class="value">{{ formatNumber(idx.open || idx.openPrice || '-') }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">最高</span>
                      <span class="value text-danger">{{ formatNumber(idx.high || idx.highPrice || '-') }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">昨收</span>
                      <span class="value">{{ formatNumber(idx.prevClose || idx.closePrice || '-') }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">最低</span>
                      <span class="value text-success">{{ formatNumber(idx.low || idx.lowPrice || '-') }}</span>
                    </div>
                  </div>
                  <div class="detail-row" style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #f0f0f0;">
                    <div class="detail-item">
                      <span class="label">振幅</span>
                      <span class="value">{{ calcAmplitude(idx).toFixed(2) }}%</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">成交量</span>
                      <span class="value">{{ formatVolume(idx.volume) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">成交额</span>
                      <span class="value">{{ formatAmount(idx.turnover) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">涨跌额</span>
                      <span class="value" :class="(idx.change || 0) >= 0 ? 'text-danger' : 'text-success'">
                        {{ (idx.change || 0) >= 0 ? '+' : '' }}{{ formatNumber2(idx.change || 0) }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- K线图 -->
              <div class="chart-wrapper">
                <div :ref="el => setChartRef(idx.code, el as HTMLElement)" class="kline-chart"></div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 板块 -->
      <el-tab-pane label="板块" name="sectors">
        <div class="sectors-section">
          <!-- AI 板块分析 Banner -->
          <div v-if="aiAnalysis" class="ai-banner">
            <div class="ai-banner-header">
              <div class="ai-title">
                <el-icon color="#33b86a" :size="20"><TrendCharts /></el-icon>
                <span>AI 板块精选 Top 5</span>
              </div>
              <div class="header-actions">
                <span class="ai-time" v-if="aiAnalysis.analysisTime">分析时间: {{ aiAnalysis.analysisTime }}</span>
                <el-button text type="primary" size="small" :loading="aiLoading" @click="triggerAiAnalysis" style="margin-left: 12px;">
                  <el-icon><Refresh /></el-icon> 刷新分析
                </el-button>
              </div>
            </div>

            <div class="ai-sector-grid">
              <div
                v-for="(sector, idx) in aiAnalysis.topSectors"
                :key="sector.sectorCode"
                class="ai-sector-card"
              >
                <div class="ai-card-header">
                  <span class="ai-rank">{{ idx + 1 }}</span>
                  <div class="ai-card-info">
                    <span class="ai-sector-name">{{ sector.sectorName }}</span>
                    <span class="ai-change" :class="safeScore(sector.changePercent) >= 0 ? 'up' : 'down'">
                      {{ safeScore(sector.changePercent) >= 0 ? '+' : '' }}{{ safeScore(sector.changePercent).toFixed(2) }}%
                    </span>
                  </div>
                </div>
                <p class="ai-reason">{{ sector.aiReason }}</p>
                <div class="ai-leader-list">
                  <div class="leader-header">龙头股分析</div>
                  <div v-for="stock in sector.leaderStocks" :key="stock.code" class="leader-item" @click="goToStock(stock.code)">
                    <div class="leader-main">
                      <span class="leader-name">{{ stock.name }}</span>
                      <span class="leader-change" :class="safeScore(stock.changePercent) >= 0 ? 'up' : 'down'">
                        {{ safeScore(stock.changePercent) >= 0 ? '+' : '' }}{{ safeScore(stock.changePercent).toFixed(2) }}%
                      </span>
                      <el-tag size="small" :type="trendTagType(stock.aiTrend)" class="trend-tag">{{ stock.aiTrend }}</el-tag>
                    </div>
                    <p class="leader-reason">{{ stock.aiReason }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- AI 分析加载中 -->
          <div v-else-if="aiLoading" class="ai-loading">
            <el-skeleton :rows="3" animated />
            <div class="loading-hint">
              <el-icon class="is-loading" :size="18"><Loading /></el-icon>
              <span>AI 正在分析板块数据，请稍候...</span>
            </div>
          </div>

          <!-- 全部板块表格 -->
          <div class="section-header" style="margin-top: 16px;">
            <span class="section-title">全部板块 ({{ filteredSectors.length }})</span>
            <div class="header-actions">
              <el-input
                v-model="sectorSearchQuery"
                placeholder="搜索板块名称"
                prefix-icon="Search"
                clearable
                style="width: 240px; margin-right: 12px"
              />
              <el-button type="primary" @click="loadSectors" :loading="sectorsLoading">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
            </div>
          </div>

          <el-table
            :data="filteredSectors"
            v-loading="sectorsLoading"
            stripe
            style="width: 100%"
            @row-click="openSectorDialog"
            highlight-current-row
            class="sector-table"
            :default-sort="{ prop: 'changePercent', order: 'descending' }"
          >
            <el-table-column prop="sectorName" label="板块名称" min-width="140" sortable>
              <template #default="{ row }">
                <span class="sector-name">{{ row.sectorName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="changePercent" label="涨跌幅" width="110" align="right" sortable>
              <template #default="{ row }">
                <span class="change-tag" :class="safeScore(row.changePercent) >= 0 ? 'up' : 'down'">
                  {{ row.changePercent != null ? (safeScore(row.changePercent) >= 0 ? '+' : '') + safeScore(row.changePercent).toFixed(2) + '%' : '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="leaderName" label="龙头股" min-width="130">
              <template #default="{ row }">
                <span class="leader-link" v-if="row.leaderName">
                  {{ row.leaderName }} <span class="leader-code" v-if="row.leaderStock">({{ row.leaderStock }})</span>
                </span>
                <span v-else class="no-data">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="stockCount" label="成分股数量" width="110" align="right" sortable>
              <template #default="{ row }">
                {{ row.stockCount ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="avgChange" label="平均涨幅" width="110" align="right" sortable>
              <template #default="{ row }">
                <span v-if="row.avgChange != null" :class="safeScore(row.avgChange) >= 0 ? 'up' : 'down'">
                  {{ safeScore(row.avgChange) >= 0 ? '+' : '' }}{{ safeScore(row.avgChange).toFixed(2) }}%
                </span>
                <span v-else class="no-data">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 板块详情对话框 -->
        <el-dialog v-model="sectorDialogVisible" :title="currentSector?.sectorName || '板块详情'" width="700px" :close-on-click-modal="false" class="sector-dialog">
          <template v-if="currentSectorDetail">
            <div class="detail-summary">
              <div class="detail-stat"><span class="stat-label">板块代码</span><span class="stat-value">{{ currentSectorDetail.sectorCode }}</span></div>
              <div class="detail-stat"><span class="stat-label">涨跌幅</span><span class="stat-value" :class="safeScore(currentSectorDetail.changePercent) >= 0 ? 'up' : 'down'">{{ currentSectorDetail.changePercent != null ? (safeScore(currentSectorDetail.changePercent) >= 0 ? '+' : '') + safeScore(currentSectorDetail.changePercent).toFixed(2) + '%' : '-' }}</span></div>
              <div class="detail-stat"><span class="stat-label">成分股数量</span><span class="stat-value">{{ currentSectorDetail.stockCount ?? '-' }}</span></div>
              <div class="detail-stat"><span class="stat-label">平均涨幅</span><span class="stat-value" :class="safeScore(currentSectorDetail.avgChange) >= 0 ? 'up' : 'down'">{{ currentSectorDetail.avgChange != null ? (safeScore(currentSectorDetail.avgChange) >= 0 ? '+' : '') + safeScore(currentSectorDetail.avgChange).toFixed(2) + '%' : '-' }}</span></div>
              <div class="detail-stat" v-if="currentSectorDetail.leaderName"><span class="stat-label">龙头股</span><span class="stat-value leader-link" @click="goToStock(currentSectorDetail.leaderStock!)">{{ currentSectorDetail.leaderName }} <span class="leader-code" v-if="currentSectorDetail.leaderStock">({{ currentSectorDetail.leaderStock }})</span></span></div>
            </div>
            <el-divider />
            <div class="stock-list-header"><span class="card-title">成分股列表</span></div>
            <el-table :data="currentSectorDetail.stocks" stripe style="width: 100%" @row-click="(row: any) => goToStock(row.stockCode)" highlight-current-row class="stock-table-dialog">
              <el-table-column prop="stockCode" label="代码" width="100" />
              <el-table-column prop="stockName" label="名称" min-width="120"><template #default="{ row }"><span class="stock-name">{{ row.stockName }}</span></template></el-table-column>
              <el-table-column prop="currentPrice" label="现价" width="100" align="right"><template #default="{ row }"><span>{{ row.currentPrice != null ? row.currentPrice.toFixed(2) : '-' }}</span></template></el-table-column>
              <el-table-column prop="changePercent" label="涨跌幅" width="100" align="right"><template #default="{ row }"><span class="change-tag" :class="safeScore(row.changePercent) >= 0 ? 'up' : 'down'">{{ row.changePercent != null ? (safeScore(row.changePercent) >= 0 ? '+' : '') + safeScore(row.changePercent).toFixed(2) + '%' : '-' }}</span></template></el-table-column>
              <el-table-column prop="marketCap" label="市值" min-width="110" align="right"><template #default="{ row }">{{ formatMarketCap(row.marketCap) }}</template></el-table-column>
            </el-table>
          </template>
          <div v-else class="detail-loading"><el-skeleton :rows="4" animated /></div>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, TrendCharts, SortDown, SortUp, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getStockList } from '@/api/stock'
import { getGlobalIndices } from '@/api/global'
import { getAllSectors, getSectorDetail, aiAnalyzeSectors, getAiSectorReport } from '@/api/sector'
import type { SectorInfo, SectorDetail, AiSectorAnalysis } from '@/api/sector'
import { formatPrice, formatPercent, formatNumber, getColor } from '@/utils/format'
import type { StockInfo } from '@/types'
import request from '@/api/request'

// A+H 股对照表：A股代码 → { hkCode, hkName }
const AH_MAP: Record<string, { hkCode: string; hkName: string }> = {
  '002594': { hkCode: '01211', hkName: '比亚迪股份' },
  '601318': { hkCode: '02318', hkName: '中国平安' },
  '600585': { hkCode: '00914', hkName: '海螺水泥' },
}
// 缓存已获取的港股价格
const hkPriceCache = ref<Record<string, { price: number; changePercent: number }>>({})

const router = useRouter()
const activeTab = ref('list')

// ============ 股票列表 ============
const loading = ref(false)
const stockList = ref<StockInfo[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const sortField = ref('changePercent')
const sortOrder = ref('desc')
let searchTimer: ReturnType<typeof setTimeout> | null = null

function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { currentPage.value = 1; loadData() }, 300)
}

async function loadData() {
  loading.value = true
  try {
    const res = await getStockList({ market: 'A_STOCK', page: currentPage.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined })
    const rawData = res.data.data
    if (rawData) {
      const listData = Array.isArray(rawData) ? rawData : (rawData.list || [])
      listData.sort((a: any, b: any) => { const va = safeScore(a[sortField.value]); const vb = safeScore(b[sortField.value]); return sortOrder.value === 'desc' ? vb - va : va - vb })
      stockList.value = listData
      total.value = Array.isArray(rawData) ? rawData.length : (rawData.total || 0)
      // 异步获取港股对标价格
      fetchHkPrices(listData)
    }
  } catch (e: any) { ElMessage.error(e.message || '加载A股数据失败'); stockList.value = []; total.value = 0 }
  finally { loading.value = false }
}

/** 获取港股对标价格 */
async function fetchHkPrices(aStocks: any[]) {
  const codesToFetch = aStocks.map(s => AH_MAP[s.code]).filter(Boolean)
  for (const item of codesToFetch) {
    try {
      const res = await request.get(`/stock/global/realtime/${item.hkCode}`, { params: { market: 'HK' } })
      if (res.data?.code === 200 && res.data.data) {
        hkPriceCache.value[item.hkCode] = {
          price: res.data.data.currentPrice || res.data.data.price || 0,
          changePercent: res.data.data.changePercent || 0
        }
      }
    } catch { /* ignore */ }
  }
}

/** 获取港股溢价率 = (A股 - 港股*汇率) / (港股*汇率) */
function getPremium(aCode: string, aPrice: number): number | null {
  const ah = AH_MAP[aCode]
  if (!ah) return null
  const hk = hkPriceCache.value[ah.hkCode]
  if (!hk || !hk.price || !aPrice) return null
  // 假设汇率 1HKD=0.92CNY（近似），港股1手=1股折算
  const rate = 0.92
  const hkCny = hk.price * rate
  if (hkCny <= 0) return null
  return (aPrice - hkCny) / hkCny * 100
}

function goToDetail(row: StockInfo) { router.push(`/stock/${row.code}`) }

// ============ 大盘指数 (含ECharts K线) ============
const indicesLoading = ref(false)
const indices = ref<any[]>([])
const chartInstances = new Map<string, echarts.ECharts>()
const chartElements = new Map<string, HTMLElement>()
let refreshTimer: ReturnType<typeof setInterval> | null = null
const INDEX_NAME_MAP: Record<string, string> = { 'SSE': '上证指数', 'SZI': '深证成指', 'CYB': '创业板指' }

function setChartRef(code: string, el: HTMLElement | null) {
  if (el) { chartElements.set(code, el); nextTick(() => initChart(code)) }
}

function calcAmplitude(idx: any): number {
  if (!idx.prevClose && !idx.closePrice) return 0
  const prev = idx.prevClose || idx.closePrice || 1
  const high = idx.high || idx.highPrice || 0
  const low = idx.low || idx.lowPrice || 0
  return prev > 0 ? ((high - low) / prev) * 100 : 0
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

function formatNumber2(num: number | undefined): string {
  if (num == null) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function calcMA(data: number[], period: number): (number | null)[] {
  const result: (number | null)[] = []
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) { result.push(null) }
    else {
      let sum = 0
      for (let j = i - period + 1; j <= i; j++) sum += data[j]
      result.push(Math.round((sum / period) * 100) / 100)
    }
  }
  return result
}

function initChart(code: string) {
  const el = chartElements.get(code); if (!el) return
  const old = chartInstances.get(code); if (old) old.dispose()
  const index = indices.value.find((i: any) => i.code === code); if (!index) return
  const chart = echarts.init(el); chartInstances.set(code, chart)
  // 不生成模拟K线数据，显示暂无K线提示
  chart.setOption({
    graphic: {
      type: 'text',
      left: 'center',
      top: 'center',
      style: {
        text: '暂无K线',
        textAlign: 'center',
        fill: '#c0c4cc',
        fontSize: 14
      }
    }
  })
  const resizeHandler = () => chart.resize(); window.addEventListener('resize', resizeHandler); (chart as any)._resizeHandler = resizeHandler
}

async function loadIndices() {
  indicesLoading.value = true
  try {
    const res = await getGlobalIndices('CN')
    indices.value = res.data?.data || []
    indices.value.forEach((idx: any) => { idx.name = INDEX_NAME_MAP[idx.code] || idx.code })
    await nextTick()
    indices.value.forEach((idx: any) => { const el = chartElements.get(idx.code); if (el) initChart(idx.code) })
  } catch { indices.value = [] }
  finally { indicesLoading.value = false }
}

// ============ 板块 (含AI分析) ============
const sectorsLoading = ref(false)
const sectors = ref<SectorInfo[]>([])
const sectorSearchQuery = ref('')
const sectorDialogVisible = ref(false)
const currentSector = ref<SectorInfo | null>(null)
const currentSectorDetail = ref<SectorDetail | null>(null)
const aiAnalysis = ref<AiSectorAnalysis | null>(null)
const aiLoading = ref(false)

const filteredSectors = computed(() => {
  if (!sectorSearchQuery.value.trim()) return sectors.value
  const q = sectorSearchQuery.value.trim().toLowerCase()
  return sectors.value.filter(s => s.sectorName.toLowerCase().includes(q))
})

function trendTagType(trend: string): string {
  if (trend === '看涨') return 'danger'
  if (trend === '看跌') return 'success'
  return 'info'
}

async function loadSectors() {
  sectorsLoading.value = true
  try {
    const res = await getAllSectors()
    sectors.value = res.data?.data || []
  } catch { sectors.value = [] }
  finally { sectorsLoading.value = false }
}

async function triggerAiAnalysis() {
  aiLoading.value = true
  try {
    const res = await aiAnalyzeSectors()
    aiAnalysis.value = res.data?.data || null
    if (aiAnalysis.value) ElMessage.success('AI 板块分析完成')
  } catch {
    ElMessage.error('AI 分析失败，请稍后重试')
    try { const reportRes = await getAiSectorReport(); aiAnalysis.value = reportRes.data?.data || null } catch { aiAnalysis.value = null }
  } finally { aiLoading.value = false }
}

async function openSectorDialog(row: SectorInfo) {
  currentSector.value = row; sectorDialogVisible.value = true; currentSectorDetail.value = null
  try {
    const res = await getSectorDetail(row.sectorCode)
    const raw = res.data?.data
    if (raw) {
      const sec = raw.sector || {}
      const tops = (raw.topStocks || []).map((s: any) => ({
        stockCode: s.code,
        stockName: s.name,
        currentPrice: s.currentPrice,
        changePercent: s.changePercent,
        marketCap: s.marketCap
      }))
      currentSectorDetail.value = {
        sectorCode: sec.sectorCode || row.sectorCode,
        sectorName: sec.sectorName || row.sectorName,
        changePercent: sec.changePercent ?? row.changePercent,
        stockCount: sec.stockCount ?? row.stockCount,
        avgChange: sec.avgChange ?? row.avgChange,
        leaderName: sec.leaderName || row.leaderName,
        leaderStock: sec.leaderStock || row.leaderStock,
        stocks: tops
      }
    }
  } catch { ElMessage.error('加载板块详情失败'); currentSectorDetail.value = null }
}

function goToStock(code: string) { router.push(`/stock/${code}`) }

function formatMarketCap(cap: number | null): string {
  if (cap == null) return '-'
  if (cap >= 100000000) return (cap / 100000000).toFixed(2) + '亿'
  if (cap >= 10000) return (cap / 10000).toFixed(2) + '万'
  return cap.toFixed(2)
}

onMounted(() => {
  loadData()
  loadIndices()
  loadSectors()
  refreshTimer = setInterval(loadIndices, 30000) // 30秒自动刷新指数
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

watch(activeTab, (tab) => {
  if (tab === 'indices' && indices.value.length === 0) loadIndices()
  if (tab === 'sectors') {
    if (sectors.value.length === 0) loadSectors()
    if (!aiAnalysis.value && !aiLoading.value) triggerAiAnalysis()
  }
})
</script>

<style scoped lang="scss">
.market-hub { max-width: 1400px; }

.page-header { display: flex; align-items: center; margin-bottom: 16px;
  .header-left { display: flex; align-items: center; gap: 8px; }
  .page-title { font-size: 18px; font-weight: 600; color: #303133; }
}

.market-tabs { :deep(.el-tabs__header) { margin-bottom: 16px; } }

.market-card { border-radius: 8px; }
.card-header { display: flex; align-items: center; justify-content: flex-end;
  .header-actions { display: flex; align-items: center; }
}

.rank-bar { display: flex; align-items: center; margin-bottom: 16px;
  .rank-label { font-size: 13px; color: #909399; margin-right: 8px; white-space: nowrap; }
}

.stock-table { cursor: pointer;
  :deep(.el-table__row) { &:hover { .stock-name { color: #33b86a; } } }
}
.stock-name { font-weight: 500; color: #303133; transition: color 0.3s; }

/* A+H 对比 */
.hk-compare { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; font-size: 12px;
  .hk-name { color: #909399; font-size: 11px; }
  .hk-price { color: #303133; font-weight: 500; }
  .hk-loading { color: #c0c4cc; font-size: 11px; }
  .premium-tag { font-weight: 600; font-size: 12px;
    &.up { color: #f56c6c; }
    &.down { color: #67c23a; }
  }
}

.change-tag { display: inline-block; padding: 2px 10px; border-radius: 4px; font-size: 13px; font-weight: 600; min-width: 70px; text-align: center;
  &.up { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
  &.down { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
}

.pagination-container { display: flex; justify-content: flex-end; margin-top: 16px; }

// 大盘指数
.indices-section { .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
    .section-title { font-size: 16px; font-weight: 600; color: #303133; }
  }
}
.loading-placeholder, .empty-placeholder { padding: 40px 0; }

.indices-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;
  @media (max-width: 1200px) { grid-template-columns: 1fr; }
}

.index-card { background: #fff; border-radius: 12px; padding: 20px; border: 1px solid #ebeef5; border-left: 4px solid #909399; box-shadow: 0 2px 8px rgba(0,0,0,0.04); transition: box-shadow 0.2s;
  &:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.1); }
  &.positive { border-left-color: #f56c6c; }
  &.negative { border-left-color: #67c23a; }
}

.index-header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px;
  .index-name { font-size: 18px; font-weight: 600; color: #303133; }
  .index-code { font-size: 12px; color: #909399; background: #f5f7fa; padding: 2px 8px; border-radius: 4px; }
}

.index-price { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 14px;
  .price-value { font-size: 32px; font-weight: 700; color: #303133; }
  .change-box { text-align: right;
    .change-amount { display: block; font-size: 16px; font-weight: 500; }
    .change-percent { display: block; font-size: 14px; }
  }
  .positive & .price-value, .positive & .change-amount, .positive & .change-percent { color: #f56c6c; }
  .negative & .price-value, .negative & .change-amount, .negative & .change-percent { color: #67c23a; }
}

.index-details { margin-bottom: 12px; padding: 12px; background: #fafbfc; border-radius: 8px; }

.detail-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px;
  & + & { margin-top: 8px; padding-top: 8px; border-top: 1px solid #f0f0f0; }
}

.detail-item { display: flex; flex-direction: column; align-items: center; gap: 2px;
  .label { font-size: 11px; color: #909399; }
  .value { font-size: 13px; color: #606266; font-weight: 500; }
}

.chart-wrapper { border-top: 1px solid #ebeef5; padding-top: 12px; animation: fadeIn 0.4s ease; }
@keyframes fadeIn { from { opacity: 0.3; } to { opacity: 1; } }
.kline-chart { width: 100%; height: 320px; }

// 板块
.sectors-section { .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
    .section-title { font-size: 16px; font-weight: 600; color: #303133; }
  }
}

// AI Banner
.ai-banner { background: rgba(255,255,255,0.92); backdrop-filter: blur(8px); border-radius: 12px; padding: 20px; margin-bottom: 20px; border: 1px solid #e0f0e5; box-shadow: 0 2px 12px rgba(51,184,106,0.08); }
.ai-banner-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
  .ai-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; color: #303133; }
  .ai-time { font-size: 12px; color: #909399; }
}
.ai-sector-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px;
  @media (max-width: 1200px) { grid-template-columns: repeat(3, 1fr); }
  @media (max-width: 768px) { grid-template-columns: repeat(2, 1fr); }
  @media (max-width: 480px) { grid-template-columns: 1fr; }
}
.ai-sector-card { background: #fff; border: 1px solid #e0f0e5; border-radius: 10px; padding: 14px; cursor: pointer; transition: all 0.25s ease;
  &:hover { box-shadow: 0 4px 16px rgba(51,184,106,0.12); border-color: #8ae6aa; transform: translateY(-2px); }
}
.ai-card-header { display: flex; gap: 10px; margin-bottom: 8px;
  .ai-rank { width: 24px; height: 24px; border-radius: 6px; background: linear-gradient(135deg, #33b86a, #5cd689); color: #fff; font-size: 12px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
  .ai-card-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .ai-sector-name { font-size: 14px; font-weight: 600; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .ai-change { font-size: 13px; font-weight: 600; &.up { color: #f56c6c; } &.down { color: #67c23a; } }
}
.ai-reason { font-size: 12px; color: #606266; line-height: 1.5; margin: 0 0 8px 0; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.ai-leader-list { margin-top: 10px; padding-top: 10px; border-top: 1px solid #e0f0e5;
  .leader-header { font-size: 12px; font-weight: 600; color: #33b86a; margin-bottom: 8px; padding-left: 4px; }
}
.leader-item { padding: 8px 10px; border-radius: 6px; cursor: pointer; transition: background 0.2s; margin-bottom: 4px;
  &:hover { background: #f0faf4; }
  &:last-child { margin-bottom: 0; }
  .leader-main { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
  .leader-name { font-size: 13px; font-weight: 500; color: #303133; min-width: 0; flex-shrink: 1; }
  .leader-change { font-size: 12px; font-weight: 600; flex-shrink: 0; &.up { color: #f56c6c; } &.down { color: #67c23a; } }
  .trend-tag { flex-shrink: 0; margin-left: auto; }
  .leader-reason { font-size: 11px; color: #909399; line-height: 1.4; margin: 0; }
}

.ai-loading { background: rgba(255,255,255,0.92); backdrop-filter: blur(8px); border-radius: 12px; padding: 24px; margin-bottom: 20px; border: 1px solid #e0f0e5;
  .loading-hint { display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 16px; font-size: 14px; color: #909399; }
}

.sector-table { cursor: pointer;
  :deep(.el-table__row) { &:hover { .sector-name { color: #33b86a; } } }
}
.sector-name { font-weight: 500; color: #303133; transition: color 0.3s; }
.leader-link { color: #409eff; cursor: pointer; transition: color 0.2s;
  &:hover { color: #66b1ff; }
  .leader-code { font-size: 12px; color: #909399; }
}
.no-data { color: #c0c4cc; }
.up { color: #f56c6c; } .down { color: #67c23a; }

.sector-dialog { :deep(.el-dialog__body) { padding: 20px; } }
.detail-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px;
  .detail-stat { display: flex; flex-direction: column; gap: 4px; padding: 12px; background: #f5fcf8; border-radius: 8px; border: 1px solid #e0f0e5;
    .stat-label { font-size: 12px; color: #909399; }
    .stat-value { font-size: 15px; font-weight: 600; color: #303133; &.up { color: #f56c6c; } &.down { color: #67c23a; } }
    .leader-link { color: #409eff; cursor: pointer; &:hover { color: #66b1ff; } .leader-code { font-size: 12px; color: #909399; font-weight: 400; } }
  }
}
.detail-loading { padding: 20px 0; }
.stock-list-header { margin-bottom: 12px; .card-title { font-size: 15px; font-weight: 600; color: #303133; } }
.stock-table-dialog { cursor: pointer;
  :deep(.el-table__row) { &:hover { .stock-name { color: #409eff; } } }
}
</style>
