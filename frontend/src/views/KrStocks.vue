<template>
  <div class="market-hub">
    <div class="page-header">
      <div class="header-left">
        <el-icon :size="22" color="#7c4dff"><TrendCharts /></el-icon>
        <span class="page-title">韩股市场</span>
        <el-tag size="small" type="warning" effect="light" style="margin-left: 8px;">行情为模拟数据</el-tag>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="market-tabs">
      <!-- 大盘指数 (默认) -->
      <el-tab-pane label="大盘指数" name="indices">
        <div class="indices-section">
          <div class="section-header">
            <span class="section-title">韩国主要指数</span>
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
                  <span class="price-value">{{ formatNumber(idx.current) }}</span>
                  <div class="change-box">
                    <span class="change-amount">
                      {{ idx.change >= 0 ? '+' : '' }}{{ formatNumber(idx.change) }}
                    </span>
                    <span class="change-percent">
                      {{ idx.changePercent >= 0 ? '+' : '' }}{{ safeScore(idx.changePercent).toFixed(2) }}%
                    </span>
                  </div>
                </div>
                <div class="index-details">
                  <div class="detail-row">
                    <div class="detail-item">
                      <span class="label">今开</span>
                      <span class="value">{{ formatNumber(idx.open) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">最高</span>
                      <span class="value text-danger">{{ formatNumber(idx.high) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">昨收</span>
                      <span class="value">{{ formatNumber(idx.prevClose) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">最低</span>
                      <span class="value text-success">{{ formatNumber(idx.low) }}</span>
                    </div>
                  </div>
                  <div class="detail-row">
                    <div class="detail-item">
                      <span class="label">涨幅</span>
                      <span class="value" :class="idx.changePercent >= 0 ? 'text-danger' : 'text-success'">
                        {{ idx.changePercent >= 0 ? '+' : '' }}{{ safeScore(idx.changePercent).toFixed(2) }}%
                      </span>
                    </div>
                    <div class="detail-item">
                      <span class="label">涨跌额</span>
                      <span class="value" :class="idx.change >= 0 ? 'text-danger' : 'text-success'">
                        {{ idx.change >= 0 ? '+' : '' }}{{ formatNumber(idx.change) }}
                      </span>
                    </div>
                    <div class="detail-item">
                      <span class="label">最高</span>
                      <span class="value">{{ formatNumber(idx.high) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">最低</span>
                      <span class="value">{{ formatNumber(idx.low) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 行情列表 -->
      <el-tab-pane label="行情列表" name="list">
        <div class="empty-placeholder">
          <el-empty description="暂无真实行情数据" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getGlobalIndices } from '@/api/global'
import { formatPrice, formatPercent, formatNumber, getColor } from '@/utils/format'

const router = useRouter()

// Tab
const activeTab = ref('indices')

// 大盘指数
const indicesLoading = ref(false)
const indices = ref<any[]>([])

function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

async function loadIndices() {
  indicesLoading.value = true
  try {
    const res = await getGlobalIndices('KR')
    indices.value = res.data?.data || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载韩股指数数据失败')
    indices.value = []
  } finally {
    indicesLoading.value = false
  }
}

onMounted(() => {
  loadIndices()
})
</script>

<style scoped lang="scss">
.market-hub {
  max-width: 1400px;
}

.page-header {
  display: flex;
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

.market-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}

.market-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;

  .header-actions {
    display: flex;
    align-items: center;
  }
}

.stock-table {
  cursor: default;

  :deep(.el-table__row) {
    &:hover {
      .stock-name {
        color: #7c4dff;
      }
    }
  }
}

.stock-name {
  font-weight: 500;
  color: #303133;
  transition: color 0.3s;
}

.currency-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: #f5f7fa;
  color: #909399;
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

.simulate-notice {
  margin-top: 16px;
}

// 大盘指数
.indices-section {
  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }
  }
}

.loading-placeholder,
.empty-placeholder {
  padding: 40px 0;
}

.indices-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

.index-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
  border-left: 4px solid #909399;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }

  &.positive {
    border-left-color: #f56c6c;
  }

  &.negative {
    border-left-color: #67c23a;
  }
}

.index-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;

  .index-name {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }

  .index-code {
    font-size: 12px;
    color: #909399;
    background: #f5f7fa;
    padding: 2px 8px;
    border-radius: 4px;
  }
}

.index-price {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 14px;

  .price-value {
    font-size: 32px;
    font-weight: 700;
    color: #303133;
  }

  .change-box {
    text-align: right;

    .change-amount {
      display: block;
      font-size: 16px;
      font-weight: 500;
    }

    .change-percent {
      display: block;
      font-size: 14px;
    }
  }

  .positive & .price-value,
  .positive & .change-amount,
  .positive & .change-percent {
    color: #f56c6c;
  }

  .negative & .price-value,
  .negative & .change-amount,
  .negative & .change-percent {
    color: #67c23a;
  }
}

.index-details {
  padding: 12px;
  background: #fafbfc;
  border-radius: 8px;
}

.detail-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;

  & + & {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid #f0f0f0;
  }
}

.detail-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;

  .label {
    font-size: 11px;
    color: #909399;
  }

  .value {
    font-size: 13px;
    color: #606266;
    font-weight: 500;
  }
}

.text-danger { color: #f56c6c; }
.text-success { color: #67c23a; }
</style>
