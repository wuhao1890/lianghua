<template>
  <div class="home-page">
    <section class="page-head">
      <div>
        <h2>首页</h2>
        <p>大盘指数在上，行业板块在下，用真实行情快速判断市场温度。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadData">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </section>

    <section class="market-console">
      <article class="console-main">
        <span>今日市场驾驶舱</span>
        <strong>{{ marketTone }}</strong>
        <p>{{ marketSummary }}</p>
      </article>
      <button class="console-tile" type="button" @click="router.push('/market')">
        <span>股票行情</span>
        <strong>A股 / 美股</strong>
      </button>
      <button class="console-tile" type="button" @click="router.push('/news')">
        <span>新闻雷达</span>
        <strong>央视 / 东方财富 / 新浪</strong>
      </button>
      <button class="console-tile" type="button" @click="router.push('/ai-lab')">
        <span>智能实验室</span>
        <strong>策略迭代 / 模拟收益</strong>
      </button>
      <button class="console-tile" type="button" @click="router.push('/gold')">
        <span>资产</span>
        <strong>基金 / 金属</strong>
      </button>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>智能推荐五大板块</h3>
        <span>每个板块精选五只股票，按板块强度、个股动量和波动稳定性打分。</span>
      </div>
      <div class="ai-sector-grid" v-loading="aiLoading">
        <article v-for="sector in aiTopSectors" :key="sector.sectorCode" class="ai-sector-card">
          <div class="ai-sector-head">
            <div>
              <strong>{{ sector.sectorName }}</strong>
              <span :class="num(sector.changePercent) >= 0 ? 'up' : 'down'">{{ formatPercent(sector.changePercent) }}</span>
            </div>
            <b>{{ sector.aiScore }}分</b>
          </div>
          <p>{{ sector.aiReason }}</p>
          <div class="pick-list">
            <el-tooltip
              v-for="stock in sector.leaderStocks"
              :key="stock.code"
              placement="top"
              :content="stock.aiReason"
            >
              <button class="pick-item" type="button" @click="router.push(`/stock/${stock.code}`)">
                <span>{{ stock.name }} {{ stock.code }}</span>
                <strong>{{ stock.aiScore }}分</strong>
              </button>
            </el-tooltip>
          </div>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>大盘指数</h3>
        <span>上证、深证、创业板、沪深300</span>
      </div>
      <div class="index-grid">
        <article v-for="item in indices" :key="item.code" class="index-card" :class="num(item.changePercent) >= 0 ? 'up-card' : 'down-card'">
          <div>
            <strong>{{ indexName(item) }}</strong>
            <span>{{ item.code }}</span>
          </div>
          <div class="price">{{ formatPrice(item.current) }}</div>
          <div class="change" :class="num(item.changePercent) >= 0 ? 'up' : 'down'">
            {{ formatSigned(item.change) }} / {{ formatPercent(item.changePercent) }}
          </div>
          <div class="meta">
            <span>高 {{ formatPrice(item.high) }}</span>
            <span>低 {{ formatPrice(item.low) }}</span>
            <span>量 {{ formatVolume(item.volume) }}</span>
          </div>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>行业板块</h3>
        <span>按真实成分股涨跌幅聚合，点击龙头股可进入详情。</span>
      </div>
      <el-table :data="sectors" stripe class="sector-table" v-loading="loading">
        <el-table-column prop="sectorName" label="板块" min-width="140" sortable />
        <el-table-column prop="changePercent" label="板块涨跌" width="120" align="right" sortable>
          <template #default="{ row }">
            <span :class="num(row.changePercent) >= 0 ? 'up' : 'down'">{{ formatPercent(row.changePercent) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="leaderName" label="龙头股" min-width="150">
          <template #default="{ row }">
            <el-button v-if="row.leaderStock" text type="primary" @click="router.push(`/stock/${row.leaderStock}`)">
              {{ row.leaderName }} {{ row.leaderStock }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="leaderChangePercent" label="龙头涨跌" width="120" align="right" sortable>
          <template #default="{ row }">
            <span :class="num(row.leaderChangePercent) >= 0 ? 'up' : 'down'">{{ formatPercent(row.leaderChangePercent) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stockCount" label="成分数" width="100" align="right" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getSinaIndices } from '@/api/stock'
import { getAiTopSectorPicks, getAllSectors } from '@/api/sector'

const router = useRouter()
const loading = ref(false)
const aiLoading = ref(false)
const indices = ref<any[]>([])
const sectors = ref<any[]>([])
const aiTopSectors = ref<any[]>([])

const marketTone = computed(() => {
  const upCount = indices.value.filter((item) => num(item.changePercent) >= 0).length
  if (upCount >= 3) return '指数偏强，优先跟踪热点延续'
  if (upCount <= 1) return '指数承压，优先控制仓位'
  return '指数分化，等待主线确认'
})

const marketSummary = computed(() => {
  const topSector = aiTopSectors.value[0]
  if (!topSector) return '正在聚合真实指数、行业板块和智能推荐。'
  return `${topSector.sectorName} 当前智能评分 ${topSector.aiScore} 分，优先观察其高分个股和新闻催化。`
})

const indexMap: Record<string, string> = {
  '000001': '上证指数',
  '399001': '深证成指',
  '399006': '创业板指',
  '000300': '沪深300'
}

async function loadData() {
  loading.value = true
  aiLoading.value = true
  try {
    const [indexRes, sectorRes, aiRes] = await Promise.all([getSinaIndices(), getAllSectors(), getAiTopSectorPicks()])
    indices.value = indexRes.data?.data || []
    sectors.value = sectorRes.data?.data || []
    aiTopSectors.value = aiRes.data?.data?.topSectors || []
  } finally {
    loading.value = false
    aiLoading.value = false
  }
}

function indexName(item: any) {
  return indexMap[item.code] || item.name || item.code
}

function num(value: any) {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

function formatPrice(value: any) {
  return num(value).toFixed(2)
}

function formatSigned(value: any) {
  const n = num(value)
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)}`
}

function formatPercent(value: any) {
  const n = num(value)
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)}%`
}

function formatVolume(value: any) {
  const n = num(value)
  if (n >= 100000000) return `${(n / 100000000).toFixed(2)}亿`
  if (n >= 10000) return `${(n / 10000).toFixed(2)}万`
  return `${Math.round(n)}`
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.home-page {
  max-width: 1400px;
  display: grid;
  gap: 16px;
}

.page-head,
.panel {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;

  h2 {
    margin: 0 0 4px;
    color: #1f2d3d;
    font-size: 22px;
  }

  p {
    margin: 0;
    color: #606266;
  }
}

.panel {
  padding: 16px;
}

.market-console {
  display: grid;
  grid-template-columns: minmax(280px, 1.6fr) repeat(4, minmax(150px, 1fr));
  gap: 12px;
}

.console-main,
.console-tile {
  min-height: 94px;
  padding: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.console-main {
  border-left: 4px solid #d84d4d;

  span,
  p {
    color: #606266;
  }

  strong {
    display: block;
    margin: 6px 0;
    color: #1f2d3d;
    font-size: 20px;
  }

  p {
    margin: 0;
    line-height: 1.5;
  }
}

.console-tile {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  color: #303133;
  cursor: pointer;
  text-align: left;

  &:hover {
    border-color: #409eff;
    background: #eef6ff;
  }

  span {
    color: #909399;
    font-size: 12px;
  }

  strong {
    font-size: 14px;
  }
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;

  h3 {
    margin: 0;
    color: #303133;
    font-size: 17px;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.index-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.ai-sector-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  min-height: 160px;
}

.ai-sector-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-left: 4px solid #409eff;
  border-radius: 8px;
  background: #fff;

  p {
    margin: 0;
    min-height: 52px;
    color: #606266;
    font-size: 12px;
    line-height: 1.45;
  }
}

.ai-sector-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;

  strong,
  span {
    display: block;
  }

  b {
    color: #d84d4d;
    font-size: 18px;
  }
}

.pick-list {
  display: grid;
  gap: 6px;
}

.pick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border: 1px solid #edf0f5;
  border-radius: 6px;
  background: #f8fafc;
  color: #303133;
  cursor: pointer;
  text-align: left;

  &:hover {
    border-color: #409eff;
    background: #eef6ff;
  }

  span {
    overflow: hidden;
    color: #303133;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1f9d66;
    white-space: nowrap;
  }
}

.index-card {
  padding: 14px;
  border: 1px solid #ebeef5;
  border-left: 4px solid #909399;
  border-radius: 8px;
  background: #fff;

  &.up-card {
    border-left-color: #d84d4d;
  }

  &.down-card {
    border-left-color: #1f9d66;
  }

  strong,
  span {
    display: block;
  }

  span {
    color: #909399;
    font-size: 12px;
  }
}

.price {
  margin-top: 10px;
  color: #1f2d3d;
  font-size: 28px;
  font-weight: 700;
}

.change {
  margin-top: 4px;
  font-weight: 600;
}

.meta {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 10px;

  span {
    padding: 6px;
    border-radius: 6px;
    background: #f5f7fa;
    text-align: center;
  }
}

.up {
  color: #d84d4d;
}

.down {
  color: #1f9d66;
}

.sector-table {
  width: 100%;
}

@media (max-width: 1100px) {
  .market-console {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .console-main {
    grid-column: 1 / -1;
  }

  .index-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ai-sector-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }

  .index-grid {
    grid-template-columns: 1fr;
  }

  .market-console {
    grid-template-columns: 1fr;
  }

  .ai-sector-grid {
    grid-template-columns: 1fr;
  }
}
</style>
