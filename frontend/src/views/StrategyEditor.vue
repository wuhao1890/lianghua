<template>
  <div class="strategy-page">
    <div class="page-header">
      <div class="header-left">
        <el-icon :size="22" color="#409eff"><Monitor /></el-icon>
        <span class="page-title">策略编辑器</span>
      </div>
      <el-button type="primary" @click="showCreate = true">
        <el-icon><Plus /></el-icon> 新建策略
      </el-button>
    </div>

    <!-- 策略列表 -->
    <el-card shadow="hover">
      <el-table :data="strategyList" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="name" label="策略名称" width="160" />
        <el-table-column prop="stockCode" label="股票" width="120">
          <template #default="{ row }">{{ row.stockCode }} {{ row.stockName }}</template>
        </el-table-column>
        <el-table-column label="条件" min-width="200">
          <template #default="{ row }">
            <span v-if="row.conditions" class="cond-text">
              {{ formatConditions(row.conditions) }}
            </span>
            <span v-else class="no-data">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总收益率" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.result" :class="safeScore(row.result.totalReturn) >= 0 ? 'up' : 'down'">
              {{ safeScore(row.result.totalReturn).toFixed(2) }}%
            </span>
            <span v-else class="no-data">-</span>
          </template>
        </el-table-column>
        <el-table-column label="交易次数" width="100" align="right">
          <template #default="{ row }">{{ row.result?.totalTrades || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleRun(row.id)" :loading="runningId === row.id">运行</el-button>
            <el-button size="small" @click="handlePause(row.id)" v-if="row.status === 'RUNNING'">暂停</el-button>
            <el-button size="small" @click="handleStop(row.id)" v-if="row.status === 'RUNNING' || row.status === 'PAUSED'">停止</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && strategyList.length === 0" description="暂无策略，点击「新建策略」开始" :image-size="60" />
    </el-card>

    <!-- 新建策略对话框 -->
    <el-dialog v-model="showCreate" title="新建策略" width="600px" top="5vh">
      <el-form :model="form" label-width="100px" label-position="left">
        <el-form-item label="策略名称">
          <el-input v-model="form.name" placeholder="如：MA5金叉买入" />
        </el-form-item>
        <el-form-item label="股票代码">
          <el-input v-model="form.stockCode" placeholder="如 600519" style="width:200px" />
        </el-form-item>
        <el-form-item label="条件列表">
          <div class="conditions-editor">
            <div v-for="(cond, idx) in form.conditions" :key="idx" class="condition-row">
              <el-select v-model="cond.indicator" size="small" style="width:120px">
                <el-option label="PRICE(价格)" value="PRICE" />
                <el-option label="MA5" value="MA5" />
                <el-option label="MA10" value="MA10" />
                <el-option label="MA20" value="MA20" />
                <el-option label="MA60" value="MA60" />
                <el-option label="RSI" value="RSI" />
                <el-option label="MACD.DIF" value="MACD_DIF" />
                <el-option label="MACD.DEA" value="MACD_DEA" />
                <el-option label="KDJ.K" value="KDJ_K" />
                <el-option label="KDJ.D" value="KDJ_D" />
                <el-option label="VOLUME(成交量)" value="VOLUME" />
              </el-select>
              <el-select v-model="cond.operator" size="small" style="width:100px">
                <el-option label=">" value=">" />
                <el-option label=">=" value=">=" />
                <el-option label="<" value="<" />
                <el-option label="<=" value="<=" />
                <el-option label="==" value="==" />
                <el-option label="金叉上穿" value="CROSS_ABOVE" />
                <el-option label="死叉下穿" value="CROSS_BELOW" />
              </el-select>
              <el-select v-model="cond.value" size="small" style="width:120px" v-if="cond.operator !== 'CROSS_ABOVE' && cond.operator !== 'CROSS_BELOW'">
                <el-option label="数值" value="" disabled />
                <el-option v-for="i in 10" :key="i" :label="String(i*10)" :value="String(i*10)" />
                <el-option v-for="v in ['PRICE','MA5','MA10','MA20','MA60']" :key="v" :label="v" :value="v" />
              </el-select>
              <el-input v-model="cond.customValue" size="small" placeholder="自定义值" style="width:100px" v-if="cond.operator !== 'CROSS_ABOVE' && cond.operator !== 'CROSS_BELOW'" />
              <el-select v-model="cond.connector" size="small" style="width:70px">
                <el-option label="AND" value="AND" />
                <el-option label="OR" value="OR" />
              </el-select>
              <el-button text size="small" type="danger" @click="form.conditions.splice(idx, 1)">✕</el-button>
            </div>
            <el-button size="small" @click="addCondition" style="margin-top:6px">+ 添加条件</el-button>
          </div>
        </el-form-item>
        <el-form-item label="回测期间">
          <el-date-picker v-model="form.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="初始资金">
          <el-input-number v-model="form.capital" :min="10000" :step="100000" controls-position="right" style="width:200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating">创建并运行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Monitor } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const loading = ref(false)
const creating = ref(false)
const runningId = ref('')
const strategyList = ref<any[]>([])
const showCreate = ref(false)

const form = reactive({
  name: '',
  stockCode: '',
  conditions: [] as any[],
  dateRange: null as any,
  capital: 100000
})

function addCondition() {
  form.conditions.push({
    indicator: 'MA5',
    operator: 'CROSS_ABOVE',
    value: 'MA20',
    customValue: '',
    connector: 'AND'
  })
}

function safeScore(val: any): number { const n = Number(val); return isNaN(n) ? 0 : n }
function statusType(s: string) {
  const m: Record<string, string> = { IDLE: 'info', RUNNING: 'warning', PAUSED: '', STOPPED: 'success', ERROR: 'danger' }
  return m[s] || 'info'
}
function statusLabel(s: string) {
  const m: Record<string, string> = { IDLE: '待运行', RUNNING: '运行中', PAUSED: '已暂停', STOPPED: '已完成', ERROR: '错误' }
  return m[s] || s
}
function formatConditions(conds: any[]) {
  return conds.map((c: any) => {
    const op = c.operator === 'CROSS_ABOVE' ? '金叉' : c.operator === 'CROSS_BELOW' ? '死叉' : c.operator
    return `${c.indicator} ${op} ${c.customValue || c.value || ''}`
  }).join(' AND ')
}

async function fetchList() {
  loading.value = true
  try {
    const res = await request.get('/stock/strategy/list')
    strategyList.value = res.data?.data || []
  } catch { strategyList.value = [] }
  finally { loading.value = false }
}

async function handleCreate() {
  if (!form.name || !form.stockCode || form.conditions.length === 0) {
    ElMessage.warning('请填写完整信息'); return
  }
  creating.value = true
  try {
    const payload: any = {
      name: form.name,
      stockCode: form.stockCode,
      type: 'simple_condition',
      conditions: form.conditions.map((c: any) => ({
        indicator: c.indicator,
        operator: c.operator,
        value: c.customValue || c.value || '',
        connector: c.connector || 'AND'
      })),
      mode: 'backtest',
      backtestConfig: {
        initialCapital: form.capital
      }
    }
    if (form.dateRange && form.dateRange[0]) {
      payload.backtestConfig.startDate = form.dateRange[0]
      payload.backtestConfig.endDate = form.dateRange[1]
    }
    const res = await request.post('/stock/strategy/create', payload)
    if (res.data.code === 200) {
      ElMessage.success('策略创建成功')
      showCreate.value = false
      fetchList()
    } else { ElMessage.error(res.data.message) }
  } catch (e: any) { ElMessage.error(e.message || '创建失败') }
  finally { creating.value = false }
}

async function handleRun(id: string) {
  runningId.value = id
  try {
    const res = await request.post(`/stock/strategy/run/${id}`)
    if (res.data.code === 200) ElMessage.success('策略执行完成')
    else ElMessage.error(res.data.message)
    fetchList()
  } catch (e: any) { ElMessage.error('运行失败') }
  finally { runningId.value = '' }
}

async function handlePause(id: string) {
  try { await request.post(`/stock/strategy/pause/${id}`); ElMessage.info('已暂停'); fetchList() }
  catch { ElMessage.error('暂停失败') }
}

async function handleStop(id: string) {
  try { await request.post(`/stock/strategy/stop/${id}`); ElMessage.info('已停止'); fetchList() }
  catch { ElMessage.error('停止失败') }
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('确定删除此策略？', '确认')
    await request.delete(`/stock/strategy/${id}`)
    ElMessage.success('已删除')
    fetchList()
  } catch {}
}

onMounted(() => fetchList())
</script>

<style scoped lang="scss">
.strategy-page { max-width: 1400px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
  .header-left { display: flex; align-items: center; gap: 8px; }
  .page-title { font-size: 18px; font-weight: 600; }
}
.conditions-editor { width: 100%; }
.condition-row { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.up { color: #f56c6c; }
.down { color: #67c23a; }
.no-data { color: #c0c4cc; }
.cond-text { font-size: 12px; color: #606266; }
:deep(.el-dialog__body) { padding-top: 12px; }
</style>
