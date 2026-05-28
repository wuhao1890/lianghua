<template>
  <div class="history-page">
    <el-card shadow="hover" class="history-card">
      <template #header>
        <div class="card-header">
          <h3>交易记录</h3>
          <div class="filters">
            <el-select v-model="filterDirection" placeholder="方向" clearable style="width: 100px" @change="loadData">
              <el-option label="买入" value="BUY" />
              <el-option label="卖出" value="SELL" />
            </el-select>
            <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 110px" @change="loadData">
              <el-option label="待成交" value="PENDING" />
              <el-option label="已成交" value="FILLED" />
              <el-option label="已撤销" value="CANCELLED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px"
              @change="loadData"
            />
          </div>
        </div>
      </template>

      <el-table
        :data="tradeStore.orders"
        v-loading="tradeStore.loading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="createdAt" label="时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="stockCode" label="代码" width="100">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/stock/${row.stockCode}`)">
              {{ row.stockCode }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="stockName" label="名称" width="100" />
        <el-table-column prop="direction" label="方向" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'BUY' ? 'danger' : 'success'" size="small">
              {{ row.direction === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderType" label="类型" width="80" align="center">
          <template #default="{ row }">
            {{ row.orderType === 'MARKET' ? '市价' : '限价' }}
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" width="100" align="right">
          <template #default="{ row }">
            {{ formatPrice(row.price) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column prop="fee" label="手续费" width="100" align="right">
          <template #default="{ row }">
            {{ formatMoney(row.fee) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              type="warning"
              size="small"
              text
              @click="handleCancel(row.id)"
            >
              撤销
            </el-button>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!tradeStore.loading && tradeStore.orders.length === 0" description="暂无交易记录" />

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="tradeStore.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useTradeStore } from '@/store/trade'
import { formatDateTime, formatPrice, formatMoney } from '@/utils/format'

const tradeStore = useTradeStore()

const currentPage = ref(1)
const pageSize = ref(20)
const filterDirection = ref('')
const filterStatus = ref('')
const dateRange = ref<string[]>([])

function statusTagType(status: string) {
  const map: Record<string, string> = {
    FILLED: 'success',
    PENDING: 'warning',
    CANCELLED: 'info',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

function statusText(status: string) {
  const map: Record<string, string> = {
    FILLED: '已成交',
    PENDING: '待成交',
    CANCELLED: '已撤销',
    REJECTED: '已拒绝'
  }
  return map[status] || status
}

function loadData() {
  tradeStore.getOrders({
    page: currentPage.value,
    pageSize: pageSize.value,
    direction: filterDirection.value || undefined,
    status: filterStatus.value || undefined,
    startDate: dateRange.value?.[0] || undefined,
    endDate: dateRange.value?.[1] || undefined
  })
}

async function handleCancel(orderId: number) {
  try {
    await ElMessageBox.confirm('确定要撤销该订单吗？', '撤销确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await tradeStore.cancelOrder(orderId)
    ElMessage.success('订单已撤销')
    loadData()
  } catch (e) {
    // 用户取消
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.history-page {
  max-width: 1400px;
}

.history-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;

  h3 {
    font-size: 16px;
    color: #303133;
    margin: 0;
  }
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.text-muted {
  color: #c0c4cc;
}
</style>
