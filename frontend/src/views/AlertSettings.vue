<template>
  <div class="alert-settings-page">
    <section class="settings-header">
      <div>
        <h2>邮箱告警设置</h2>
        <p>当智能实验室出现王者策略买入或卖出时，系统会把执行动作和前五名状态发到你的邮箱。</p>
      </div>
      <el-tag type="success" effect="plain">只在关键买卖时提醒</el-tag>
    </section>

    <section class="settings-panel">
      <el-form label-position="top" :model="form" class="settings-form">
        <el-form-item label="接收邮箱">
          <el-input v-model="form.email" placeholder="请输入你的邮箱" />
        </el-form-item>

        <div class="switch-row">
          <div>
            <strong>开启王者买卖告警</strong>
            <span>只有王者段位策略触发买入、卖出或淘汰卖出时才发送。</span>
          </div>
          <el-switch v-model="form.emailEnabled" />
        </div>

        <div class="switch-row">
          <div>
            <strong>邮件附带前五名状态</strong>
            <span>包含标的、策略、段位、动作、仓位、收益和回撤。</span>
          </div>
          <el-switch v-model="form.includeTopFive" />
        </div>

        <div class="actions">
          <el-button type="primary" :loading="loading" @click="save">
            <el-icon><Message /></el-icon>
            保存设置
          </el-button>
          <el-button :loading="loading" @click="load">重新读取</el-button>
        </div>
      </el-form>
    </section>

    <section class="preview-panel">
      <h3>邮件内容会包含</h3>
      <div class="preview-grid">
        <article>
          <strong>触发原因</strong>
          <span>王者策略买入、卖出或被淘汰卖出。</span>
        </article>
        <article>
          <strong>执行动作</strong>
          <span>标的、策略、价格、仓位、模拟金额、收益。</span>
        </article>
        <article>
          <strong>前五名状态</strong>
          <span>当前最优五个组合的段位、动作、收益和回撤。</span>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Message } from '@element-plus/icons-vue'
import { getAlertSettings, saveAlertSettings } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { setUser } from '@/utils/storage'

const userStore = useUserStore()
const loading = ref(false)
const form = reactive({
  email: userStore.userInfo?.email || '',
  emailEnabled: false,
  kingTradeOnly: true,
  includeTopFive: true
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await getAlertSettings()
    const data = res.data.data
    form.email = data.email || userStore.userInfo?.email || ''
    form.emailEnabled = Boolean(data.emailEnabled)
    form.kingTradeOnly = data.kingTradeOnly !== false
    form.includeTopFive = data.includeTopFive !== false
  } finally {
    loading.value = false
  }
}

async function save() {
  if (form.emailEnabled && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    ElMessage.warning('请先填写正确的邮箱')
    return
  }
  loading.value = true
  try {
    const res = await saveAlertSettings({
      email: form.email.trim(),
      emailEnabled: form.emailEnabled,
      kingTradeOnly: true,
      includeTopFive: form.includeTopFive
    })
    const data = res.data.data
    if (userStore.userInfo) {
      userStore.userInfo.email = data.email
      setUser(userStore.userInfo)
    }
    ElMessage.success('邮箱告警设置已保存')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.alert-settings-page {
  max-width: 1100px;
  display: grid;
  gap: 16px;
}

.settings-header,
.settings-panel,
.preview-panel {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  padding: 18px;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  h2 {
    margin: 0 0 6px;
    color: #1f2d3d;
  }

  p {
    margin: 0;
    color: #606266;
  }
}

.settings-form {
  max-width: 720px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-top: 1px solid #ebeef5;

  strong,
  span {
    display: block;
  }

  span {
    margin-top: 4px;
    color: #606266;
  }
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.preview-panel h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  article {
    padding: 14px;
    border-radius: 8px;
    background: #f7f9fc;
    border: 1px solid #ebeef5;
  }

  strong,
  span {
    display: block;
  }

  span {
    margin-top: 6px;
    color: #606266;
    line-height: 1.6;
  }
}

@media (max-width: 768px) {
  .settings-header,
  .switch-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
