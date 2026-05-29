<template>
  <div class="screener-page">
    <!-- 顶部筛选表单 -->
    <el-card shadow="hover" class="filter-card">
      <template #header><span>筛选条件</span></template>
      <el-form label-width="90px" label-position="left">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="价格范围">
              <div class="range-group">
                <el-input-number v-model="filter.minPrice" :min="0" :step="0.01" placeholder="最低价" controls-position="right" style="width:130px" />
                <span class="range-sep">~</span>
                <el-input-number v-model="filter.maxPrice" :min="0" :step="0.01" placeholder="最高价" controls-position="right" style="width:130px" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="涨跌幅%">
              <div class="range-group">
                <el-input-number v-model="filter.minChange" :step="0.1" placeholder="最小" controls-position="right" style="width:130px" />
                <span class="range-sep">~</span>
                <el-input-number v-model="filter.maxChange" :step="0.1" placeholder="最大" controls-position="right" style="width:130px" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="最小成交量">
              <el-input-number v-model="filter.minVolume" :min="0" :step="10000" placeholder="成交量" controls-position="right" style="width:130px" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="关键词">
              <el-input v-model="filter.keyword" placeholder="股票代码或名称" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="市场">
              <el-select v-model="filter.market" placeholder="全部市场" clearable style="width:100%">
                <el-option label="A股" value="A_STOCK" />
                <el-option label="美股" value="US" />
                <el-option label="港股" value="HK" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="排序字段">
              <div class="sort-group">
                <el-select v-model="filter.sortField" placeholder="默认" clearable style="width:150px">
                  <el-option label="价格" value="currentPrice" />
                  <el-option label="涨跌幅" value="changePercent" />
                  <el-option label="成交量" value="volume" />
                  <el-option label="成交额" value="turnover" />
                  <el-option label="市值" value="marketCap" />
                </el-select>
                <el-button-group style="margin-left:8px">
                  <el-button :type="filter.sortOrder === 'asc' ? 'primary' : 'default'" @click="filter.sortOrder = 'asc'" :icon="'SortUp'">升序</el-button>
                  <el-button :type="filter.sortOrder === 'desc' ? 'primary' : 'default'" @click="filter.sortOrder = 'desc'" :icon="'SortDown'">降序</el-button>
                </el-button-group>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <div class="filter-actions">
              <el-button type="primary" @click="handleSearch" :loading="loading">筛选</el-button>
              <el-button @click="handleReset">重置</el-button>
            </div>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 筛选结果表格 -->
    <el-card shadow="hover" class="result-card" style="margin-top:16px">
      <template #header>
        <span>筛选结果 (共 {{ total }} 条)</span>
      </template>
      <el-table :data="stockList" stripe v-loading="loading" @row-click="handleRowClick" style="cursor:pointer">
        <el-table-column prop="code" label="代码" width="100" />
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="currentPrice" label="现价" width="120" align="right">
          <template #default="{ row }">
            <span>{{ formatPrice(row.currentPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: getColor(row.changePercent) }">{{ formatPercent(row.changePercent) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="成交量" min-width="120" align="right">
          <template #default="{ row }">
            <span>{{ formatNumber(row.volume) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="turnover" label="成交额" min-width="130" align="right">
          <template #default="{ row }">
            <span>{{ formatMoney(row.turnover) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="marketCap" label="市值" width="140" align="right">
          <template #default="{ row }">
            <span>{{ formatNumber(row.marketCap) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper" style="margin-top:16px;text-align:right">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { formatPrice, formatPercent, formatMoney, formatNumber, getColor } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const stockList = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filter = reactive({
  minPrice: undefined as number | undefined,
  maxPrice: undefined as number | undefined,
  minChange: undefined as number | undefined,
  maxChange: undefined as number | undefined,
  minVolume: undefined as number | undefined,
  keyword: '',
  market: '',
  sortField: '',
  sortOrder: 'desc' as 'asc' | 'desc'
})

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.value,
      size: pageSize.value
    }
    if (filter.minPrice !== undefined && filter.minPrice !== null) params.minPrice = filter.minPrice
    if (filter.maxPrice !== undefined && filter.maxPrice !== null) params.maxPrice = filter.maxPrice
    if (filter.minChange !== undefined && filter.minChange !== null) params.minChange = filter.minChange
    if (filter.maxChange !== undefined && filter.maxChange !== null) params.maxChange = filter.maxChange
    if (filter.minVolume !== undefined && filter.minVolume !== null) params.minVolume = filter.minVolume
    if (filter.keyword) params.keyword = filter.keyword
    if (filter.market) params.market = filter.market
    if (filter.sortField) params.sortField = filter.sortField
    if (filter.sortOrder) params.sortOrder = filter.sortOrder

    const res = await request.get('/stock/screener', { params })
    const data = res.data
    if (data.code === 200) {
      const result = data.data
      stockList.value = result.list || result.records || result || []
      total.value = result.total || 0
    } else {
      ElMessage.error(data.message || '获取数据失败')
    }
  } catch (error: unknown) {
    const errMsg = error instanceof Error ? error.message : '请求失败'
    ElMessage.error(errMsg)
    stockList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  filter.minPrice = undefined
  filter.maxPrice = undefined
  filter.minChange = undefined
  filter.maxChange = undefined
  filter.minVolume = undefined
  filter.keyword = ''
  filter.market = ''
  filter.sortField = ''
  filter.sortOrder = 'desc'
  page.value = 1
  fetchData()
}

function handleRowClick(row: any) {
  router.push(`/stock/${row.code}`)
}

function handleSizeChange(val: number) {
  pageSize.value = val
  page.value = 1
  fetchData()
}

function handlePageChange(val: number) {
  page.value = val
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped lang="scss">
.screener-page {
  max-width: 1400px;
}

.filter-card {
  border-radius: 8px;

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid #f0f2f5;
    font-weight: 500;
    font-size: 15px;
    color: #303133;
  }

  :deep(.el-form-item) {
    margin-bottom: 18px;
  }
}

.range-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.range-sep {
  color: #909399;
  font-size: 14px;
}

.sort-group {
  display: flex;
  align-items: center;
}

.filter-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-start;
  padding-top: 4px;
}

.result-card {
  border-radius: 8px;

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid #f0f2f5;
    font-weight: 500;
    font-size: 15px;
    color: #303133;
  }
}
</style>
