<template>
  <div class="backtest-page">
    <div class="page-header">
      <div class="header-left">
        <el-icon :size="22" color="#409eff"><DataAnalysis /></el-icon>
        <span class="page-title">策略回测</span>
      </div>
    </div>

    <!-- 参数配置 -->
    <el-card shadow="hover" class="param-card">
      <template #header><span>回测参数</span></template>
      <el-form :model="form" label-width="100px" size="default">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="股票代码">
              <el-input v-model="form.code" placeholder="如 600519" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="策略">
              <el-select v-model="form.strategy" style="width:100%">
                <el-option label="MA金叉死叉" value="ma_cross" />
                <el-option label="买入持有" value="buy_hold" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="初始资金">
              <el-input-number v-model="form.capital" :min="10000" :step="50000" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="短周期">
              <el-input-number v-model="form.shortPeriod" :min="2" :max="60" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="长周期">
              <el-input-number v-model="form.longPeriod" :min="5" :max="120" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label=" ">
              <div style="display:flex;gap:8px;width:100%">
                <el-button type="primary" @click="runBacktest" :loading="loading" style="flex:1">
                  <el-icon><TrendCharts /></el-icon> 执行回测
                </el-button>
                <el-button v-if="loading" type="danger" @click="cancelBacktest">
                  <el-icon><Close /></el-icon> 取消
                </el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 结果 -->
    <template v-if="result">
      <!-- 汇总指标 -->
      <el-row :gutter="12" class="stats-row">
        <el-col :span="6">
          <div class="stat-card"><div class="label">总收益率</div>
            <div class="value" :class="safeScore(result.stats.totalReturn) >= 0 ? 'up' : 'down'">
              {{ safeScore(result.stats.totalReturn).toFixed(2) }}%</div></div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="label">年化收益率</div>
            <div class="value" :class="safeScore(result.stats.annualizedReturn) >= 0 ? 'up' : 'down'">
              {{ result.stats.annualizedReturn ? safeScore(result.stats.annualizedReturn).toFixed(2) + '%' : '-' }}</div></div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="label">最大回撤</div>
            <div class="value down">{{ safeScore(result.stats.maxDrawdown).toFixed(2) }}%</div></div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="label">夏普比率</div>
            <div class="value" :class="safeScore(result.stats.sharpeRatio) >= 1 ? 'up' : (safeScore(result.stats.sharpeRatio) >= 0 ? '' : 'down')">
              {{ safeScore(result.stats.sharpeRatio).toFixed(2) }}</div></div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="label">交易次数</div>
            <div class="value">{{ result.stats.totalTrades }}</div></div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="label">胜率</div>
            <div class="value up">{{ safeScore(result.stats.winRate).toFixed(1) }}%</div></div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="label">盈亏比</div>
            <div class="value">{{ safeScore(result.stats.profitLossRatio).toFixed(2) }}</div></div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card"><div class="label">最终资产</div>
            <div class="value">{{ formatMoney(result.stats.finalCapital) }}</div></div>
        </el-col>
      </el-row>

      <!-- 权益曲线 -->
      <el-card shadow="hover" class="chart-card">
        <template #header><span>权益曲线 & 回撤</span></template>
        <div ref="equityChartRef" class="chart-container" style="height:360px"></div>
      </el-card>

      <!-- 交易记录 -->
      <el-card shadow="hover" class="trade-card" v-if="result.trades && result.trades.length > 0">
        <template #header><span>交易记录 ({{ result.trades.length }})</span></template>
        <el-table :data="result.trades" stripe style="width:100%">
          <el-table-column label="买入日期" prop="buyDate" width="120" />
          <el-table-column label="买入价" width="120" align="right">
            <template #default="{ row }">{{ formatPrice(row.buyPrice) }}</template>
          </el-table-column>
          <el-table-column label="卖出日期" prop="sellDate" width="120" />
          <el-table-column label="卖出价" width="120" align="right">
            <template #default="{ row }">{{ row.sellDate ? formatPrice(row.sellPrice) : '持仓中' }}</template>
          </el-table-column>
          <el-table-column label="盈亏" width="120" align="right">
            <template #default="{ row }">
              <span v-if="row.sellDate" :class="safeScore(row.profit) >= 0 ? 'up' : 'down'">
                {{ safeScore(row.profit) >= 0 ? '+' : '' }}{{ formatMoney(row.profit) }}
              </span>
              <span v-else class="no-data">-</span>
            </template>
          </el-table-column>
          <el-table-column label="收益率" width="110" align="right">
            <template #default="{ row }">
              <span v-if="row.sellDate" :class="safeScore(row.profitPercent) >= 0 ? 'up' : 'down'">
                {{ safeScore(row.profitPercent) >= 0 ? '+' : '' }}{{ safeScore(row.profitPercent).toFixed(2) }}%
              </span>
              <span v-else class="no-data">-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 空状态 -->
    <el-empty v-else-if="!loading" description="配置参数后点击「执行回测」" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick } from 'vue'
import { TrendCharts, DataAnalysis, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import request from '@/api/request'
import { formatMoney, formatPrice } from '@/utils/format'

const loading = ref(false)
const result = ref<any>(null)
const abortController = ref<AbortController | null>(null)

const form = reactive({
  code: '600519',
  strategy: 'ma_cross',
  shortPeriod: 5,
  longPeriod: 20,
  capital: 100000
})

const equityChartRef = ref<HTMLElement>()

function safeScore(val: any): number {
  const n = Number(val); return isNaN(n) ? 0 : n
}

async function runBacktest() {
  if (!form.code.trim()) { ElMessage.warning('请输入股票代码'); return }
  loading.value = true; result.value = null
  // 创建可取消的控制器
  const ac = new AbortController(); abortController.value = ac
  try {
    const url = `/api/stock/backtest?code=${encodeURIComponent(form.code)}&strategy=${form.strategy}&shortPeriod=${form.shortPeriod}&longPeriod=${form.longPeriod}&capital=${form.capital}`
    const res = await fetch(url, { signal: ac.signal }).then(r => r.json())
    if (res.code === 200) { result.value = res.data; await nextTick(); initChart() }
    else { ElMessage.error(res.message || '回测失败') }
  } catch (e: any) {
    if (e.name === 'AbortError') { ElMessage.info('回测已取消') }
    else { ElMessage.error(e.message || '回测请求失败') }
  } finally { loading.value = false; abortController.value = null }
}

function cancelBacktest() {
  if (abortController.value) {
    abortController.value.abort()
    loading.value = false
    abortController.value = null
  }
}

function initChart() {
  if (!equityChartRef.value || !result.value) return
  const chart = echarts.init(equityChartRef.value)
  const curve = result.value.equityCurve || []
  const dates = curve.map((p: any) => p.date)
  const equities = curve.map((p: any) => p.equity)
  const drawdowns = curve.map((p: any) => safeScore(p.drawdownPercent))

  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['权益', '回撤'], bottom: 0 },
    grid: [{ left: '3%', right: '3%', top: '8%', height: '65%' }, { left: '3%', right: '3%', top: '72%', height: '20%' }],
    xAxis: [{ type: 'category', data: dates, axisLabel: { fontSize: 10, interval: Math.floor(dates.length / 8) }, axisLine: { show: false }, gridIndex: 0 },
      { type: 'category', data: dates, axisLabel: { show: false }, axisLine: { show: false }, gridIndex: 1 }],
    yAxis: [{ type: 'value', scale: true, splitLine: { lineStyle: { type: 'dashed' } }, gridIndex: 0 },
      { type: 'value', scale: true, splitLine: { show: false }, gridIndex: 1 }],
    series: [
      { name: '权益', type: 'line', data: equities, smooth: true, symbol: 'none',
        lineStyle: { width: 2, color: '#409eff' }, areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{offset:0,color:'rgba(64,158,255,0.3)'},{offset:1,color:'rgba(64,158,255,0.02)'}]) },
        xAxisIndex: 0, yAxisIndex: 0 },
      { name: '回撤', type: 'bar', data: drawdowns, barWidth: '80%',
        itemStyle: { color: (p: any) => p.value > 0 ? '#f56c6c' : '#67c23a' },
        xAxisIndex: 1, yAxisIndex: 1 }
    ]
  })
  window.addEventListener('resize', () => chart.resize())
}
</script>

<style scoped lang="scss">
.backtest-page { max-width: 1400px; }
.page-header { display: flex; align-items: center; margin-bottom: 16px;
  .header-left { display: flex; align-items: center; gap: 8px; }
  .page-title { font-size: 18px; font-weight: 600; color: #303133; }
}
.param-card { border-radius: 8px; margin-bottom: 16px; }
.stats-row { margin-bottom: 16px; }
.stat-card { background: #fff; border-radius: 8px; padding: 16px; border: 1px solid #ebeef5; margin-bottom: 12px;
  .label { font-size: 12px; color: #909399; margin-bottom: 6px; }
  .value { font-size: 20px; font-weight: 700; color: #303133; }
  .up { color: #f56c6c; } .down { color: #67c23a; }
}
.chart-card, .trade-card { border-radius: 8px; margin-bottom: 16px; }
.chart-container { width: 100%; }
.no-data { color: #c0c4cc; }
</style>
