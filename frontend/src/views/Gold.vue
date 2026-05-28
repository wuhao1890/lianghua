<template>
  <div class="gold-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <el-icon :size="22" color="#e6a23c"><Coin /></el-icon>
        <span class="page-title">黄金行情</span>
      </div>
      <el-button type="primary" @click="loadData" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <!-- 黄金价格卡片 -->
    <el-card shadow="hover" class="price-card">
      <div class="price-main">
        <div class="price-info">
          <div class="price-label">当前金价</div>
          <div class="price-value" :style="{ color: getColor(latestQuote.changePercent) }">
            ${{ formatPrice(latestQuote.price) }}
          </div>
          <div class="price-change">
            <span class="change-badge" :class="safeScore(latestQuote.changePercent) >= 0 ? 'up' : 'down'">
              {{ formatPercent(latestQuote.changePercent) }}
            </span>
          </div>
        </div>
        <div class="price-details">
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">今日最高</span>
              <span class="detail-value text-danger">
                ${{ formatPrice(latestQuote.high) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">今日最低</span>
              <span class="detail-value text-success">
                ${{ formatPrice(latestQuote.low) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">开盘价</span>
              <span class="detail-value">
                ${{ formatPrice(latestQuote.openPrice) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">数据日期</span>
              <span class="detail-value">
                {{ latestQuote.tradeDate || '-' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 历史价格表 -->
    <el-card shadow="hover" class="history-card">
      <template #header>
        <div class="card-header">
          <span class="section-title">近期金价走势</span>
        </div>
      </template>
      <el-table
        :data="historyList"
        v-loading="loading"
        stripe
        style="width: 100%"
        class="history-table"
      >
        <el-table-column prop="tradeDate" label="日期" width="140" />
        <el-table-column prop="price" label="收盘价" width="120" align="right">
          <template #default="{ row }">
            <span style="font-weight: 600">
              ${{ formatPrice(row.price) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅" width="120" align="right">
          <template #default="{ row }">
            <span class="change-tag" :class="safeScore(row.changePercent) >= 0 ? 'up' : 'down'">
              {{ formatPercent(row.changePercent) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="high" label="最高" width="120" align="right">
          <template #default="{ row }">
            <span class="text-danger">${{ formatPrice(row.high) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="low" label="最低" width="120" align="right">
          <template #default="{ row }">
            <span class="text-success">${{ formatPrice(row.low) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Refresh, Coin } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getGoldLatest, getGoldHistory } from '@/api/gold'
import type { GoldQuote } from '@/api/gold'
import { formatPrice, formatPercent, getColor } from '@/utils/format'

const loading = ref(false)

const latestQuote = reactive<GoldQuote>({
  price: 0,
  changePercent: 0,
  high: 0,
  low: 0,
  openPrice: 0,
  tradeDate: ''
})

const historyList = ref<GoldQuote[]>([])

function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

async function loadData() {
  loading.value = true
  try {
    const [latestRes, historyRes] = await Promise.all([
      getGoldLatest(),
      getGoldHistory(30)
    ])

    const latest = latestRes.data?.data
    if (latest) {
      latestQuote.price = safeScore(latest.price)
      latestQuote.changePercent = safeScore(latest.changePercent)
      latestQuote.high = safeScore(latest.high)
      latestQuote.low = safeScore(latest.low)
      latestQuote.openPrice = safeScore(latest.openPrice)
      latestQuote.tradeDate = latest.tradeDate || ''
    }

    const history = historyRes.data?.data
    if (Array.isArray(history)) {
      historyList.value = history
    } else if (history?.list) {
      historyList.value = history.list
    } else {
      historyList.value = []
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载黄金数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.gold-page {
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

.price-card {
  border-radius: 10px;
  margin-bottom: 16px;
  border-left: 4px solid #e6a23c;

  :deep(.el-card__body) {
    padding: 24px;
  }
}

.price-main {
  display: flex;
  align-items: center;
  gap: 40px;

  @media (max-width: 768px) {
    flex-direction: column;
    gap: 20px;
    align-items: flex-start;
  }
}

.price-info {
  text-align: center;
  min-width: 240px;

  .price-label {
    font-size: 14px;
    color: #909399;
    margin-bottom: 8px;
  }

  .price-value {
    font-size: 42px;
    font-weight: 700;
    color: #303133;
    line-height: 1.2;
    margin-bottom: 10px;
  }

  .price-change {
    .change-badge {
      display: inline-block;
      padding: 4px 16px;
      border-radius: 20px;
      font-size: 16px;
      font-weight: 600;

      &.up {
        background: rgba(245, 108, 108, 0.1);
        color: #f56c6c;
      }

      &.down {
        background: rgba(103, 194, 58, 0.1);
        color: #67c23a;
      }
    }
  }
}

.price-details {
  flex: 1;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  background: #f5fcf8;
  border-radius: 8px;

  .detail-label {
    font-size: 12px;
    color: #909399;
  }

  .detail-value {
    font-size: 18px;
    font-weight: 600;
    color: #303133;

    &.text-danger {
      color: #f56c6c;
    }

    &.text-success {
      color: #67c23a;
    }
  }
}

.history-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.history-table {
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
}

.text-danger {
  color: #f56c6c;
}

.text-success {
  color: #67c23a;
}
</style>
