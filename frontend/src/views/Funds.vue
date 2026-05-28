<template>
  <div class="funds-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <el-icon :size="22" color="#33b86a"><DataBoard /></el-icon>
        <span class="page-title">基金行情</span>
      </div>
      <el-button type="primary" @click="loadData" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <!-- 概览栏 -->
    <el-card shadow="hover" class="summary-card">
      <div class="summary-bar">
        <div class="summary-item">
          <span class="summary-label">基金总数</span>
          <span class="summary-value">{{ totalCount }}</span>
        </div>
        <div class="summary-divider" />
        <div class="filter-item">
          <span class="filter-label">基金类型：</span>
          <el-select v-model="selectedType" placeholder="全部类型" size="default" clearable style="width: 160px" @change="onFilterChange">
            <el-option
              v-for="item in fundTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
        <div class="filter-item search-item">
          <el-input
            v-model="keyword"
            placeholder="搜索基金名称/代码"
            clearable
            style="width: 220px"
            size="default"
            @clear="onFilterChange"
            @keyup.enter="onFilterChange"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
      </div>
    </el-card>

    <!-- 基金列表 -->
    <el-card shadow="hover" class="funds-card">
      <el-table
        :data="fundList"
        v-loading="loading"
        stripe
        style="width: 100%"
        @row-click="goToDetail"
        highlight-current-row
        class="fund-table"
      >
        <el-table-column prop="code" label="基金代码" width="120" />
        <el-table-column prop="name" label="基金名称" min-width="180">
          <template #default="{ row }">
            <span class="fund-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="nav" label="单位净值(实时)" width="140" align="right">
          <template #default="{ row }">
            <span style="font-weight: 600">
              {{ formatPrice(row.nav) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="accNav" label="累计净值" width="120" align="right">
          <template #default="{ row }">
            {{ formatPrice(row.accNav) }}
          </template>
        </el-table-column>
        <el-table-column prop="navDate" label="净值日期" width="120" align="center">
          <template #default="{ row }">
            <span class="date-text">{{ row.navDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅" width="110" align="right">
          <template #default="{ row }">
            <span class="change-tag" :class="safeScore(row.changePercent) >= 0 ? 'up' : 'down'">
              {{ formatPercent(row.changePercent) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="fundType" label="基金类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="getFundTypeTagType(row.fundType)">
              {{ row.fundType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 30, 50, 100]"
          :total="totalCount"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="onPageChange"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, DataBoard, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getFundList } from '@/api/fund'
import type { FundInfo } from '@/api/fund'
import { formatPrice, formatPercent } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const fundList = ref<FundInfo[]>([])
const totalCount = ref(0)
const selectedType = ref('')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(30)

const fundTypeOptions = [
  { value: '', label: '全部' },
  { value: '股票型', label: '股票型' },
  { value: '混合型', label: '混合型' },
  { value: '债券型', label: '债券型' },
  { value: '货币型', label: '货币型' },
  { value: '指数型', label: '指数型' },
  { value: 'QDII', label: 'QDII' }
]

function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
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

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    if (selectedType.value) {
      params.fundType = selectedType.value
    }
    if (keyword.value) {
      params.keyword = keyword.value
    }

    const res = await getFundList(params)
    const data = res.data?.data
    if (data) {
      if (Array.isArray(data.list)) {
        fundList.value = data.list
      } else if (Array.isArray(data.records)) {
        fundList.value = data.records
      } else if (Array.isArray(data)) {
        fundList.value = data
      } else {
        fundList.value = []
      }
      totalCount.value = data.total || data.totalCount || fundList.value.length
    } else if (Array.isArray(res.data)) {
      fundList.value = res.data
      totalCount.value = fundList.value.length
    } else {
      fundList.value = []
      totalCount.value = 0
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载基金数据失败')
    fundList.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  currentPage.value = 1
  loadData()
}

function onPageChange() {
  loadData()
}

function goToDetail(row: FundInfo) {
  router.push({ name: 'FundDetail', params: { code: row.code } })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.funds-page {
  max-width: 1400px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}

.summary-card {
  border-radius: 8px;
  margin-bottom: 16px;

  :deep(.el-card__body) {
    padding: 16px 20px;
  }
}

.summary-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;

  .summary-label {
    font-size: 14px;
    color: #909399;
  }

  .summary-value {
    font-size: 22px;
    font-weight: 700;
    color: #33b86a;
  }
}

.summary-divider {
  width: 1px;
  height: 32px;
  background: #e0f0e5;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;

  .filter-label {
    font-size: 14px;
    color: #909399;
    white-space: nowrap;
  }
}

.search-item {
  margin-left: auto;
}

.funds-card {
  border-radius: 8px;
}

.fund-table {
  cursor: pointer;

  :deep(.el-table__row) {
    &:hover {
      .fund-name {
        color: #33b86a;
      }
    }
  }
}

.fund-name {
  font-weight: 500;
  color: #303133;
  transition: color 0.3s;
}

.change-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 13px;
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

.date-text {
  font-size: 13px;
  color: #606266;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
</style>
