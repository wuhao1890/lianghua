<template>
  <div class="alerts-page">
    <el-card shadow="hover" class="alerts-card">
      <template #header>
        <div class="card-header">
          <span>价格预警</span>
          <el-button type="primary" @click="showAddDialog = true">新建预警</el-button>
        </div>
      </template>
      <el-table :data="alertList" stripe v-loading="loading">
        <el-table-column prop="code" label="代码" width="100" />
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="targetPrice" label="目标价" width="120" align="right">
          <template #default="{ row }">
            <span class="target-price">{{ formatPrice(row.targetPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="direction" label="方向" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'above' ? 'danger' : 'success'" size="small">
              {{ row.direction === 'above' ? '上穿' : '下穿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              @change="handleToggle(row.id, row.enabled)"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime || row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="danger" text size="small" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && alertList.length === 0" description="暂无价格预警" style="padding:40px 0" />
    </el-card>

    <!-- 新建预警对话框 -->
    <el-dialog v-model="showAddDialog" title="新建预警" width="420px" top="25vh">
      <el-form :model="addForm" label-width="90px" label-position="left">
        <el-form-item label="股票代码">
          <el-input v-model="addForm.code" placeholder="输入股票代码" />
        </el-form-item>
        <el-form-item label="股票名称">
          <el-input v-model="addForm.name" placeholder="输入股票名称" />
        </el-form-item>
        <el-form-item label="目标价">
          <el-input-number v-model="addForm.targetPrice" :min="0" :step="0.01" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item label="方向">
          <el-radio-group v-model="addForm.direction">
            <el-radio value="above">上穿</el-radio>
            <el-radio value="below">下穿</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd" :loading="adding">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAlertList, addAlert, deleteAlert, toggleAlert } from '@/api/alert'
import { formatPrice, formatDateTime } from '@/utils/format'

const loading = ref(false)
const adding = ref(false)
const alertList = ref<any[]>([])
const showAddDialog = ref(false)

const addForm = reactive({
  code: '',
  name: '',
  targetPrice: 0,
  direction: 'above' as 'above' | 'below'
})

async function fetchAlerts() {
  loading.value = true
  try {
    const res = await getAlertList()
    const data = res.data
    if (data.code === 200) {
      alertList.value = data.data || []
    } else {
      ElMessage.error(data.message || '获取预警列表失败')
    }
  } catch (error: unknown) {
    const errMsg = error instanceof Error ? error.message : '请求失败'
    ElMessage.error(errMsg)
    alertList.value = []
  } finally {
    loading.value = false
  }
}

async function handleAdd() {
  if (!addForm.code || !addForm.name || !addForm.targetPrice) {
    ElMessage.warning('请填写完整信息')
    return
  }
  adding.value = true
  try {
    const res = await addAlert({
      code: addForm.code,
      name: addForm.name,
      targetPrice: addForm.targetPrice,
      direction: addForm.direction
    })
    if (res.data.code === 200) {
      ElMessage.success('预警创建成功')
      showAddDialog.value = false
      addForm.code = ''
      addForm.name = ''
      addForm.targetPrice = 0
      addForm.direction = 'above'
      fetchAlerts()
    } else {
      ElMessage.error(res.data.message || '创建失败')
    }
  } catch (error: unknown) {
    const errMsg = error instanceof Error ? error.message : '创建失败'
    ElMessage.error(errMsg)
  } finally {
    adding.value = false
  }
}

async function handleToggle(id: number, currentEnabled: boolean) {
  try {
    const res = await toggleAlert(id)
    if (res.data.code === 200) {
      ElMessage.success(currentEnabled ? '已禁用' : '已启用')
      fetchAlerts()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (error: unknown) {
    const errMsg = error instanceof Error ? error.message : '操作失败'
    ElMessage.error(errMsg)
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除该预警吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteAlert(id)
    if (res.data.code === 200) {
      ElMessage.success('删除成功')
      fetchAlerts()
    } else {
      ElMessage.error(res.data.message || '删除失败')
    }
  } catch (error: unknown) {
    if (error instanceof Error) {
      ElMessage.error(error.message)
    }
    // 取消操作不处理
  }
}

onMounted(() => {
  fetchAlerts()
})
</script>

<style scoped lang="scss">
.alerts-page {
  max-width: 1200px;
}

.alerts-card {
  border-radius: 8px;

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid #f0f2f5;
    font-weight: 500;
    font-size: 15px;
    color: #303133;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.target-price {
  font-weight: 600;
  color: #409eff;
}
</style>
