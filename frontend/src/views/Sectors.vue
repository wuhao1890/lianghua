<template>
  <div class="sectors-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <span class="page-title">板块分析</span>
      <div class="header-actions">
        <el-button
          type="primary"
          :loading="aiLoading"
          @click="triggerAiAnalysis"
          :disabled="!sectors.length"
        >
          <el-icon><Refresh /></el-icon>
          刷新AI分析
        </el-button>
      </div>
    </div>

    <!-- AI分析 Banner -->
    <div v-if="aiAnalysis" class="ai-banner">
      <div class="ai-banner-header">
        <div class="ai-title">
          <el-icon color="#33b86a" :size="20"><TrendCharts /></el-icon>
          <span>AI 板块精选 Top 5</span>
        </div>
        <span class="ai-time">分析时间: {{ aiAnalysis.analysisTime }}</span>
      </div>

      <div class="ai-sector-grid">
        <div
          v-for="(sector, idx) in aiAnalysis.topSectors"
          :key="sector.sectorCode"
          class="ai-sector-card"
          :class="{ expanded: expandedAiCard === idx }"
          @click="toggleAiCard(idx)"
        >
          <div class="ai-card-header">
            <span class="ai-rank">{{ idx + 1 }}</span>
            <div class="ai-card-info">
              <span class="ai-sector-name">{{ sector.sectorName }}</span>
              <span
                class="ai-change"
                :class="safeScore(sector.changePercent) >= 0 ? 'up' : 'down'"
              >
                {{ safeScore(sector.changePercent) >= 0 ? '+' : '' }}{{ safeScore(sector.changePercent).toFixed(2) }}%
              </span>
            </div>
          </div>
          <p class="ai-reason">{{ sector.aiReason }}</p>

          <!-- 展开的龙头股列表 -->
          <div v-if="expandedAiCard === idx" class="ai-leader-list" @click.stop>
            <div class="leader-header">龙头股分析</div>
            <div
              v-for="stock in sector.leaderStocks"
              :key="stock.code"
              class="leader-item"
              @click="goToStock(stock.code)"
            >
              <div class="leader-main">
                <span class="leader-name">{{ stock.name }}</span>
                <span
                  class="leader-change"
                  :class="safeScore(stock.changePercent) >= 0 ? 'up' : 'down'"
                >
                  {{ safeScore(stock.changePercent) >= 0 ? '+' : '' }}{{ safeScore(stock.changePercent).toFixed(2) }}%
                </span>
                <el-tag
                  size="small"
                  :type="trendTagType(stock.aiTrend)"
                  class="trend-tag"
                >
                  {{ stock.aiTrend }}
                </el-tag>
              </div>
              <p class="leader-reason">{{ stock.aiReason }}</p>
            </div>
          </div>

          <div v-if="expandedAiCard !== idx" class="expand-hint">
            <el-icon><ArrowDown /></el-icon>
            <span>查看龙头股</span>
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
    <el-card shadow="never" class="sectors-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">全部板块 ({{ filteredSectors.length }})</span>
          <el-input
            v-model="searchQuery"
            placeholder="搜索板块名称"
            prefix-icon="Search"
            clearable
            style="width: 240px"
          />
        </div>
      </template>

      <el-table
        :data="filteredSectors"
        v-loading="loading"
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
              {{ row.leaderName }}
              <span class="leader-code" v-if="row.leaderStock">({{ row.leaderStock }})</span>
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
    </el-card>

    <!-- 板块详情对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="currentSector?.sectorName || '板块详情'"
      width="700px"
      :close-on-click-modal="false"
      class="sector-dialog"
    >
      <template v-if="currentSectorDetail">
        <div class="detail-summary">
          <div class="detail-stat">
            <span class="stat-label">板块代码</span>
            <span class="stat-value">{{ currentSectorDetail.sectorCode }}</span>
          </div>
          <div class="detail-stat">
            <span class="stat-label">涨跌幅</span>
            <span
              class="stat-value"
              :class="safeScore(currentSectorDetail.changePercent) >= 0 ? 'up' : 'down'"
            >
              {{ currentSectorDetail.changePercent != null ? (safeScore(currentSectorDetail.changePercent) >= 0 ? '+' : '') + safeScore(currentSectorDetail.changePercent).toFixed(2) + '%' : '-' }}
            </span>
          </div>
          <div class="detail-stat">
            <span class="stat-label">成分股数量</span>
            <span class="stat-value">{{ currentSectorDetail.stockCount ?? '-' }}</span>
          </div>
          <div class="detail-stat">
            <span class="stat-label">平均涨幅</span>
            <span
              class="stat-value"
              :class="safeScore(currentSectorDetail.avgChange) >= 0 ? 'up' : 'down'"
            >
              {{ currentSectorDetail.avgChange != null ? (safeScore(currentSectorDetail.avgChange) >= 0 ? '+' : '') + safeScore(currentSectorDetail.avgChange).toFixed(2) + '%' : '-' }}
            </span>
          </div>
          <div class="detail-stat" v-if="currentSectorDetail.leaderName">
            <span class="stat-label">龙头股</span>
            <span class="stat-value leader-link" @click="goToStock(currentSectorDetail.leaderStock!)">
              {{ currentSectorDetail.leaderName }}
              <span class="leader-code" v-if="currentSectorDetail.leaderStock">({{ currentSectorDetail.leaderStock }})</span>
            </span>
          </div>
        </div>

        <el-divider />

        <div class="stock-list-header">
          <span class="card-title">成分股列表</span>
        </div>
        <el-table
          :data="currentSectorDetail.stocks"
          stripe
          style="width: 100%"
          @row-click="(row: any) => goToStock(row.stockCode)"
          highlight-current-row
          class="stock-table-dialog"
        >
          <el-table-column prop="stockCode" label="代码" width="100" />
          <el-table-column prop="stockName" label="名称" min-width="120">
            <template #default="{ row }">
              <span class="stock-name">{{ row.stockName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="currentPrice" label="现价" width="100" align="right">
            <template #default="{ row }">
              <span>{{ row.currentPrice != null ? row.currentPrice.toFixed(2) : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="changePercent" label="涨跌幅" width="100" align="right">
            <template #default="{ row }">
              <span class="change-tag" :class="safeScore(row.changePercent) >= 0 ? 'up' : 'down'">
                {{ row.changePercent != null ? (safeScore(row.changePercent) >= 0 ? '+' : '') + safeScore(row.changePercent).toFixed(2) + '%' : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="marketCap" label="市值" min-width="110" align="right">
            <template #default="{ row }">
              {{ formatMarketCap(row.marketCap) }}
            </template>
          </el-table-column>
        </el-table>
      </template>
      <div v-else class="detail-loading">
        <el-skeleton :rows="4" animated />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, TrendCharts, ArrowDown, Loading, Search } from '@element-plus/icons-vue'
import {
  getAllSectors,
  aiAnalyzeSectors,
  getAiSectorReport,
  getSectorDetail,
  type SectorInfo,
  type AiSectorAnalysis,
  type AiTopSector,
  type SectorStock,
  type SectorDetail,
} from '@/api/sector'

const router = useRouter()

// 状态
const loading = ref(false)
const aiLoading = ref(false)
const sectors = ref<SectorInfo[]>([])
const aiAnalysis = ref<AiSectorAnalysis | null>(null)
const searchQuery = ref('')
const expandedAiCard = ref<number | null>(null)

// 对话框
const dialogVisible = ref(false)
const currentSector = ref<SectorInfo | null>(null)
const currentSectorDetail = ref<SectorDetail | null>(null)

/** 安全数值转换 */
function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

/** 过滤板块 */
const filteredSectors = computed(() => {
  if (!searchQuery.value.trim()) return sectors.value
  const q = searchQuery.value.trim().toLowerCase()
  return sectors.value.filter(s => s.sectorName.toLowerCase().includes(q))
})

/** AI趋势标签颜色 */
function trendTagType(trend: string): string {
  if (trend === '看涨') return 'danger'
  if (trend === '看跌') return 'success'
  return 'info'
}

/** 格式化市值 */
function formatMarketCap(cap: number | null): string {
  if (cap == null) return '-'
  if (cap >= 100000000) return (cap / 100000000).toFixed(2) + '亿'
  if (cap >= 10000) return (cap / 10000).toFixed(2) + '万'
  return cap.toFixed(2)
}

/** 展开/收起AI卡片 */
function toggleAiCard(idx: number) {
  expandedAiCard.value = expandedAiCard.value === idx ? null : idx
}

/** 跳转到股票详情 */
function goToStock(code: string) {
  router.push(`/stock/${code}`)
}

/** 打开板块详情对话框 */
async function openSectorDialog(row: SectorInfo) {
  currentSector.value = row
  dialogVisible.value = true
  currentSectorDetail.value = null
  try {
    const res = await getSectorDetail(row.sectorCode)
    currentSectorDetail.value = res.data?.data || null
  } catch {
    ElMessage.error('加载板块详情失败')
    currentSectorDetail.value = null
  }
}

/** 加载全部板块 */
async function loadSectors() {
  loading.value = true
  try {
    const res = await getAllSectors()
    sectors.value = res.data?.data || []
  } catch {
    ElMessage.error('加载板块数据失败')
    sectors.value = []
  } finally {
    loading.value = false
  }
}

/** 触发AI分析 */
async function triggerAiAnalysis() {
  aiLoading.value = true
  expandedAiCard.value = null
  try {
    const res = await aiAnalyzeSectors()
    aiAnalysis.value = res.data?.data || null
    if (aiAnalysis.value) {
      ElMessage.success('AI 板块分析完成')
    }
  } catch {
    ElMessage.error('AI 分析失败，请稍后重试')
    // 尝试获取最近的分析报告
    try {
      const reportRes = await getAiSectorReport()
      aiAnalysis.value = reportRes.data?.data || null
    } catch {
      aiAnalysis.value = null
    }
  } finally {
    aiLoading.value = false
  }
}

/** 初始化 */
onMounted(async () => {
  await loadSectors()
  if (sectors.value.length > 0) {
    triggerAiAnalysis()
  }
})
</script>

<style scoped lang="scss">
.sectors-page {
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

/* ===== AI 分析 Banner ===== */
.ai-banner {
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  border: 1px solid #e0f0e5;
  box-shadow: 0 2px 12px rgba(51, 184, 106, 0.08);
}

.ai-banner-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .ai-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }

  .ai-time {
    font-size: 12px;
    color: #909399;
  }
}

.ai-sector-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;

  @media (max-width: 1200px) {
    grid-template-columns: repeat(3, 1fr);
  }
  @media (max-width: 768px) {
    grid-template-columns: repeat(2, 1fr);
  }
  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
}

.ai-sector-card {
  background: #fff;
  border: 1px solid #e0f0e5;
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.25s ease;
  position: relative;

  &:hover {
    box-shadow: 0 4px 16px rgba(51, 184, 106, 0.12);
    border-color: #8ae6aa;
    transform: translateY(-2px);
  }

  &.expanded {
    border-color: #33b86a;
    box-shadow: 0 4px 20px rgba(51, 184, 106, 0.15);
    grid-column: span 1;

    @media (min-width: 1201px) {
      &:nth-child(1),
      &:nth-child(2) {
        grid-column: span 1;
      }
    }
  }
}

.ai-card-header {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;

  .ai-rank {
    width: 24px;
    height: 24px;
    border-radius: 6px;
    background: linear-gradient(135deg, #33b86a, #5cd689);
    color: #fff;
    font-size: 12px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .ai-card-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .ai-sector-name {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .ai-change {
    font-size: 13px;
    font-weight: 600;

    &.up { color: #f56c6c; }
    &.down { color: #67c23a; }
  }
}

.ai-reason {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  margin: 0 0 8px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.expand-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 11px;
  color: #909399;
  padding-top: 6px;
  border-top: 1px solid #f0f7f2;
}

/* AI 龙头股列表 */
.ai-leader-list {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e0f0e5;

  .leader-header {
    font-size: 12px;
    font-weight: 600;
    color: #33b86a;
    margin-bottom: 8px;
    padding-left: 4px;
  }
}

.leader-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  margin-bottom: 4px;

  &:hover {
    background: #f0faf4;
  }

  &:last-child {
    margin-bottom: 0;
  }

  .leader-main {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }

  .leader-name {
    font-size: 13px;
    font-weight: 500;
    color: #303133;
    min-width: 0;
    flex-shrink: 1;
  }

  .leader-change {
    font-size: 12px;
    font-weight: 600;
    flex-shrink: 0;

    &.up { color: #f56c6c; }
    &.down { color: #67c23a; }
  }

  .trend-tag {
    flex-shrink: 0;
    margin-left: auto;
  }

  .leader-reason {
    font-size: 11px;
    color: #909399;
    line-height: 1.4;
    margin: 0;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

/* ===== AI 加载状态 ===== */
.ai-loading {
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
  border: 1px solid #e0f0e5;

  .loading-hint {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 16px;
    font-size: 14px;
    color: #909399;
  }
}

/* ===== 全部板块表格卡片 ===== */
.sectors-card {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .card-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.sector-table {
  cursor: pointer;

  :deep(.el-table__row) {
    &:hover {
      .sector-name {
        color: #33b86a;
      }
    }
  }
}

.sector-name {
  font-weight: 500;
  color: #303133;
  transition: color 0.3s;
}

.leader-link {
  color: #409eff;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #66b1ff;
  }

  .leader-code {
    font-size: 12px;
    color: #909399;
  }
}

.change-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  min-width: 70px;
  text-align: center;

  &.up {
    background: rgba(245, 108, 108, 0.1);
    color: #f56c6c;
  }

  &.down {
    background: rgba(103, 194, 58, 0.1);
    color: #67c23a;
  }
}

.no-data {
  color: #c0c4cc;
}

.up { color: #f56c6c; }
.down { color: #67c23a; }

/* ===== 板块详情对话框 ===== */
.sector-dialog {
  :deep(.el-dialog__body) {
    padding: 20px;
  }
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;

  .detail-stat {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px;
    background: #f5fcf8;
    border-radius: 8px;
    border: 1px solid #e0f0e5;

    .stat-label {
      font-size: 12px;
      color: #909399;
    }

    .stat-value {
      font-size: 15px;
      font-weight: 600;
      color: #303133;

      &.up { color: #f56c6c; }
      &.down { color: #67c23a; }
    }

    .leader-link {
      color: #409eff;
      cursor: pointer;

      &:hover {
        color: #66b1ff;
      }

      .leader-code {
        font-size: 12px;
        color: #909399;
        font-weight: 400;
      }
    }
  }
}

.detail-loading {
  padding: 20px 0;
}

.stock-list-header {
  margin-bottom: 12px;

  .card-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.stock-table-dialog {
  cursor: pointer;

  :deep(.el-table__row) {
    &:hover {
      .stock-name {
        color: #409eff;
      }
    }
  }
}

.stock-name {
  font-weight: 500;
  color: #303133;
  transition: color 0.3s;
}
</style>
