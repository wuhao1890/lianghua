<template>
  <div class="ai-agent-page">
    <el-card shadow="never" class="page-card">
      <el-tabs v-model="activeTab" class="ai-tabs">
        <el-tab-pane label="模型配置" name="configs">
          <!-- 模型配置区域 -->
          <div class="section-header">
            <h3>智能模型配置管理</h3>
            <el-button type="primary" @click="openAddDialog">
              <el-icon><Plus /></el-icon>
              新增配置
            </el-button>
          </div>

          <el-table :data="modelConfigs" stripe style="width: 100%" v-loading="loading.configs">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="provider" label="供应商" width="120">
              <template #default="{ row }">
                <el-tag :type="getProviderTagType(row.provider)" effect="plain">
                  {{ providerName(row.provider) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="modelName" label="模型" width="180" />
            <el-table-column prop="baseUrl" label="接口地址" min-width="240" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
                <el-button size="small" @click="handleTest(row)" :loading="testingId === row.id">测试</el-button>
                <el-popconfirm title="确定删除此配置吗？" @confirm="handleDelete(row.id)">
                  <template #reference>
                    <el-button size="small" type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <el-alert class="recommend-hint" title="推荐配置" type="info" :closable="false" show-icon>
            <template #default>
              <span>深度求索、通义千问、开放智能模型等服务；请按服务商文档填写接口信息。</span>
            </template>
          </el-alert>

          <!-- 新增/编辑配置对话框 -->
          <el-dialog
            v-model="dialogVisible"
            :title="isEditing ? '编辑配置' : '新增配置'"
            width="520px"
            :close-on-click-modal="false"
          >
            <el-form
              ref="formRef"
              :model="formData"
              :rules="formRules"
              label-width="100px"
              label-position="top"
            >
              <el-form-item label="配置名称" prop="name">
                <el-input v-model="formData.name" placeholder="例如：我的本地模型配置" />
              </el-form-item>
              <el-form-item label="供应商" prop="provider">
                <el-select v-model="formData.provider" style="width: 100%">
                  <el-option label="开放智能模型" value="openai" />
                  <el-option label="深度求索" value="deepseek" />
                  <el-option label="通义千问" value="qwen" />
                  <el-option label="自定义" value="custom" />
                </el-select>
              </el-form-item>
              <el-form-item label="接口密钥" prop="apiKey">
                <el-input v-model="formData.apiKey" type="password" show-password placeholder="请输入接口密钥" />
              </el-form-item>
              <el-form-item label="接口地址" prop="baseUrl">
                <el-input v-model="formData.baseUrl" placeholder="请输入模型服务接口地址" />
              </el-form-item>
              <el-form-item label="模型名称" prop="modelName">
                <el-input v-model="formData.modelName" placeholder="gpt-3.5-turbo" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="dialogVisible = false">取消</el-button>
              <el-button type="primary" @click="handleSave" :loading="loading.save">保存</el-button>
            </template>
          </el-dialog>
        </el-tab-pane>

        <el-tab-pane label="股票分析" name="analysis">
          <!-- 分析输入区域 -->
          <el-card shadow="never" class="analysis-input-card">
            <el-form :model="analysisForm" inline label-width="100px">
              <el-form-item label="股票代码">
                <el-input
                  v-model="analysisForm.stockCode"
                  placeholder="例如：000001"
                  style="width: 180px"
                />
              </el-form-item>
              <el-form-item label="分析模型">
                <el-select
                  v-model="analysisForm.configId"
                  placeholder="选择模型配置"
                  style="width: 220px"
                >
                  <el-option
                    v-for="config in modelConfigs"
                    :key="config.id"
                    :label="config.name"
                    :value="config.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="自定义提示">
                <el-input
                  v-model="analysisForm.customPrompt"
                  placeholder="可选：输入额外的分析要求"
                  style="width: 280px"
                  clearable
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  @click="handleAnalyze"
                  :loading="loading.analyze"
                  :disabled="!analysisForm.stockCode || !analysisForm.configId"
                >
                  开始分析
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 分析结果区域 -->
          <template v-if="analysisResult">
            <!-- 评分/信号面板 -->
            <el-card shadow="never" class="result-section">
              <div class="score-bar">
                <div class="signal-badge-area">
                  <span class="signal-label">综合信号：</span>
                  <el-tag
                    :type="getSignalType(analysisResult.signal)"
                    size="large"
                    effect="dark"
                    class="signal-tag"
                  >
                    {{ getSignalText(analysisResult.signal) }}
                  </el-tag>
                </div>
                <div class="score-items">
                  <div class="score-item">
                    <span class="score-label">综合评分</span>
                    <el-progress
                      :percentage="safeScore(analysisResult.score)"
                      :stroke-width="20"
                      :color="getScoreColor(analysisResult.score || 0)"
                      :format="() => `${safeScore(analysisResult.score)}/100`"
                    />
                  </div>
                  <div class="score-item">
                    <span class="score-label">技术面评分</span>
                    <el-progress
                      :percentage="safeScore(analysisResult.techScore) * 2"
                      :stroke-width="16"
                      color="#e6a23c"
                      :format="() => `${safeScore(analysisResult.techScore)}/50`"
                    />
                  </div>
                  <div class="score-item">
                    <span class="score-label">舆情评分</span>
                    <el-progress
                      :percentage="safeScore(analysisResult.sentimentScore) * 2"
                      :stroke-width="16"
                      color="#409eff"
                      :format="() => `${safeScore(analysisResult.sentimentScore)}/50`"
                    />
                  </div>
                </div>
                <div class="target-price-area" v-if="analysisResult.targetPrice">
                  <span class="price-label">目标价位：</span>
                  <span class="price-value">{{ analysisResult.targetPrice }}</span>
                </div>
              </div>
            </el-card>

            <!-- 大V舆情分析 -->
            <el-card shadow="never" class="result-section">
              <template #header>
                <div class="section-title">
                  <span class="title-icon">&#x1F5E3;&#xFE0F;</span>
                  <span>大V舆情分析（占综合评分50%）</span>
                  <el-tag size="small" type="info" style="margin-left:8px">以下内容由智能模型基于市场公开信息生成</el-tag>
                </div>
              </template>

              <!-- 大V共识卡片 -->
              <el-card
                v-if="analysisResult.daVMajority"
                shadow="always"
                class="consensus-card"
                :class="'consensus-' + analysisResult.daVMajority.consensus"
              >
                <div class="consensus-header">
                  <el-tag
                    :type="getConsensusTagType(analysisResult.daVMajority.consensus)"
                    effect="dark"
                    size="large"
                  >
                    {{ getConsensusText(analysisResult.daVMajority.consensus) }}
                  </el-tag>
                  <span class="consensus-summary">{{ analysisResult.daVMajority.summary }}</span>
                </div>
                <div class="consensus-counts">
                  <span class="count-item bullish-count">
                    <el-icon><Top /></el-icon>
                    看涨 {{ analysisResult.daVMajority.bullishCount || 0 }}
                  </span>
                  <span class="count-item bearish-count">
                    <el-icon><Bottom /></el-icon>
                    看跌 {{ analysisResult.daVMajority.bearishCount || 0 }}
                  </span>
                  <span class="count-item neutral-count">
                    <el-icon><Minus /></el-icon>
                    中性 {{ analysisResult.daVMajority.neutralCount || 0 }}
                  </span>
                </div>
              </el-card>

              <!-- 大V观点网格 -->
              <div class="dav-grid" v-if="analysisResult.daVOpinions && analysisResult.daVOpinions.length">
                <div
                  v-for="(opinion, index) in analysisResult.daVOpinions"
                  :key="index"
                  class="dav-card"
                  :class="'dav-' + opinion.type"
                  @click="showDavDetail(opinion)"
                >
                  <div class="dav-card-header">
                    <span class="dav-name">{{ opinion.name }}</span>
                    <el-tag
                      :type="getDaVTagType(opinion.type)"
                      size="small"
                      effect="dark"
                    >
                      {{ getDaVText(opinion.type) }}
                    </el-tag>
                  </div>
                  <div class="dav-view">{{ opinion.view }}</div>
                  <div class="dav-detail">{{ opinion.detail }}</div>
                  <div class="dav-footer">
                    <div class="dav-meta">
                      <span class="dav-time" v-if="opinion.publishTime">{{ opinion.publishTime }}</span>
                      <div class="dav-influence">
                        <span class="influence-label">影响力：</span>
                        <span v-for="s in 5" :key="s" class="star" :class="{ active: s <= (opinion.influence || 3) }">★</span>
                      </div>
                    </div>
                    <el-button text size="small" type="primary" class="source-btn">查看来源 →</el-button>
                  </div>
                </div>
              </div>
            </el-card>

            <!-- 技术面分析 -->
            <el-card shadow="never" class="result-section">
              <template #header>
                <div class="section-title">
                  <span class="title-icon">&#x1F4CA;</span>
                  <span>技术面分析（占综合评分50%）</span>
                </div>
              </template>

              <el-table :data="techSignals" stripe style="width: 100%">
                <el-table-column prop="indicator" label="指标" width="120" />
                <el-table-column prop="signal" label="信号" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getTechSignalType(row.signal)" size="small">
                      {{ row.signal }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="解读" min-width="300" />
              </el-table>
            </el-card>

            <!-- 综合操作建议 -->
            <el-card shadow="never" class="result-section">
              <template #header>
                <div class="section-title">
                  <span class="title-icon">&#x1F3AF;</span>
                  <span>综合操作建议</span>
                </div>
              </template>
              <div class="markdown-content" v-html="renderedAnalysis"></div>
            </el-card>
          </template>

          <!-- 分析历史 -->
          <el-card shadow="never" class="result-section history-section">
            <template #header>
              <div class="section-title">
                <el-icon><Clock /></el-icon>
                <span>分析历史</span>
              </div>
            </template>
            <el-table
              v-if="analysisHistory.length"
              :data="analysisHistory"
              stripe
              style="width: 100%"
              v-loading="loading.history"
            >
              <el-table-column prop="stockCode" label="股票代码" width="120" />
              <el-table-column prop="stockName" label="股票名称" width="120" />
              <el-table-column label="信号" width="100">
                <template #default="{ row }">
                  <el-tag :type="getSignalType(row.signalType)" size="small">
                    {{ getSignalText(row.signalType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="综合评分" width="120">
                <template #default="{ row }">
                  {{ row.score }}/100
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="分析时间" min-width="180" />
            </el-table>
            <el-empty v-else description="暂无分析记录" :image-size="80" />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getModelConfigs,
  createModelConfig,
  updateModelConfig,
  deleteModelConfig,
  testModelConfig,
  analyzeStock,
  getAnalysisHistory,
  AiModelConfig,
  AiModelConfigRequest
} from '@/api/ai'
import type { AiAnalysisResponse, DaVOpinion } from '@/types'

// ==================== 状态 ====================
const activeTab = ref('configs')

const modelConfigs = ref<AiModelConfig[]>([])
const analysisHistory = ref<any[]>([])
const analysisResult = ref<AiAnalysisResponse | null>(null)
const testingId = ref<number | null>(null)

const loading = reactive({
  configs: false,
  save: false,
  analyze: false,
  history: false
})

const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<any>(null)

const defaultForm: AiModelConfigRequest = {
  name: '',
  provider: 'openai',
  apiKey: '',
  baseUrl: '',
  modelName: ''
}

const formData = reactive<AiModelConfigRequest>({ ...defaultForm })

const formRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入接口密钥', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入接口地址', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
}

const analysisForm = reactive({
  stockCode: '',
  configId: null as number | null,
  customPrompt: ''
})

// ==================== 技术指标模拟数据 ====================
const techSignals = computed(() => {
  if (!analysisResult.value || !analysisResult.value.analysis) return []
  // 从analysis文本中提取常见技术指标信号
  const text = analysisResult.value.analysis
  const indicators = ['MA', 'MACD', 'RSI', 'KDJ', 'BOLL']
  const signals: { indicator: string; signal: string; description: string }[] = []

  for (const ind of indicators) {
    const regex = new RegExp(`${ind}[：:\\s]*([^。\\n]*)`, 'i')
    const match = text.match(regex)
    if (match) {
      const desc = match[1].trim()
      let signal = '中性'
      if (/买|涨|金叉|多头|向上/i.test(desc)) signal = '买入'
      else if (/卖|跌|死叉|空头|向下/i.test(desc)) signal = '卖出'
      signals.push({ indicator: ind, signal, description: desc || `${ind} 指标分析` })
    } else {
      signals.push({ indicator: ind, signal: '--', description: `${ind} 指标信号未明确提及` })
    }
  }
  return signals
})

// ==================== 渲染分析文本 ====================
const renderedAnalysis = computed(() => {
  if (!analysisResult.value?.analysis) return ''
  const text = analysisResult.value.analysis
  // 简单的 markdown 转 HTML
  return text
    .replace(/### (.*?)$/gm, '<h4>$1</h4>')
    .replace(/## (.*?)$/gm, '<h3>$1</h3>')
    .replace(/# (.*?)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/- (.*?)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br/>')
    .replace(/^(.+?)$/m, '<p>$1</p>')
})

// ==================== 生命周期 ====================
onMounted(() => {
  fetchConfigs()
  fetchHistory()
})

// ==================== 方法 - 配置管理 ====================
async function fetchConfigs() {
  loading.configs = true
  try {
    const res = await getModelConfigs()
    modelConfigs.value = res.data?.data || []
  } catch (err: any) {
    ElMessage.error(err?.message || '获取配置列表失败')
  } finally {
    loading.configs = false
  }
}

function openAddDialog() {
  isEditing.value = false
  editingId.value = null
  Object.assign(formData, defaultForm)
  dialogVisible.value = true
}

function openEditDialog(row: AiModelConfig) {
  isEditing.value = true
  editingId.value = row.id
  formData.name = row.name
  formData.provider = row.provider
  formData.apiKey = row.apiKey
  formData.baseUrl = row.baseUrl
  formData.modelName = row.modelName
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.save = true
  try {
    const data: AiModelConfigRequest = {
      name: formData.name,
      provider: formData.provider,
      apiKey: formData.apiKey,
      baseUrl: formData.baseUrl,
      modelName: formData.modelName
    }
    if (isEditing.value && editingId.value) {
      await updateModelConfig(editingId.value, data)
      ElMessage.success('配置已更新')
    } else {
      await createModelConfig(data)
      ElMessage.success('配置已创建')
    }
    dialogVisible.value = false
    await fetchConfigs()
  } catch (err: any) {
    ElMessage.error(err?.message || '保存失败')
  } finally {
    loading.save = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteModelConfig(id)
    ElMessage.success('配置已删除')
    await fetchConfigs()
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

async function handleTest(row: AiModelConfig) {
  testingId.value = row.id
  try {
    await testModelConfig(row.id)
    ElMessage.success('连接测试成功！')
  } catch (err: any) {
    ElMessage.error(err?.message || '连接测试失败')
  } finally {
    testingId.value = null
  }
}

// ==================== 方法 - 分析 ====================
async function handleAnalyze() {
  if (!analysisForm.stockCode || !analysisForm.configId) {
    ElMessage.warning('请填写股票代码并选择模型配置')
    return
  }
  loading.analyze = true
  analysisResult.value = null
  try {
    const res = await analyzeStock({
      stockCode: analysisForm.stockCode,
      configId: analysisForm.configId,
      customPrompt: analysisForm.customPrompt || undefined
    })
    analysisResult.value = res.data?.data || null
    await fetchHistory()
    ElMessage.success('分析完成')
  } catch (err: any) {
    ElMessage.error(err?.message || '分析失败，请重试')
  } finally {
    loading.analyze = false
  }
}

async function fetchHistory() {
  loading.history = true
  try {
    const res = await getAnalysisHistory()
    analysisHistory.value = res.data?.data || []
  } catch {
    // ignore
  } finally {
    loading.history = false
  }
}

// ==================== 工具方法 ====================
function getProviderTagType(provider: string) {
  const map: Record<string, string> = {
    openai: 'success',
    deepseek: 'warning',
    qwen: 'primary',
    custom: 'info'
  }
  return map[provider] || 'info'
}

function providerName(provider: string) {
  const map: Record<string, string> = {
    openai: '开放智能模型',
    deepseek: '深度求索',
    qwen: '通义千问',
    custom: '自定义'
  }
  return map[provider] || '自定义'
}

function getSignalType(signal: string) {
  if (signal === 'BUY') return 'danger'
  if (signal === 'SELL') return 'success'
  return 'info'
}

function getSignalText(signal: string) {
  if (signal === 'BUY') return '买入'
  if (signal === 'SELL') return '卖出'
  return '持有'
}

function getScoreColor(score: number) {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

function safeScore(val: any): number {
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

function getTechPercent(techScore: number) {
  return Math.min(100, Math.round((techScore / 50) * 100))
}

function getSentimentPercent(sentimentScore: number) {
  return Math.min(100, Math.round((sentimentScore / 50) * 100))
}

function getConsensusTagType(consensus: string) {
  if (consensus === 'bullish') return 'danger'
  if (consensus === 'bearish') return 'success'
  return 'info'
}

function getConsensusText(consensus: string) {
  if (consensus === 'bullish') return '看涨'
  if (consensus === 'bearish') return '看跌'
  return '中性'
}

function getDaVTagType(type: string) {
  if (type === 'bullish') return 'danger'
  if (type === 'bearish') return 'success'
  return 'info'
}

function getDaVText(type: string) {
  if (type === 'bullish') return '看涨'
  if (type === 'bearish') return '看跌'
  return '中性'
}

function showDavDetail(op: any) {
  const tagType = op.type === 'bullish' ? 'danger' : op.type === 'bearish' ? 'success' : 'info'
  const label = getDaVText(op.type)
  const infl = Math.min(5, Math.max(0, op.influence || 3))
  const stars = '★'.repeat(infl) + '☆'.repeat(5 - infl)
  const timeStr = op.publishTime ? `<div style="margin-top:6px;font-size:12px;color:#909399">发布时间：${op.publishTime}</div>` : ''
  ElMessageBox({
    title: `${op.name} — 详细观点`,
    message: `<div style="margin-bottom:12px">
      <el-tag type="${tagType}" effect="dark">${label}</el-tag>
      <span style="margin-left:8px;font-weight:600;font-size:16px">${op.view}</span>
    </div>
    <div style="font-size:14px;line-height:1.8;color:#303133">${op.detail}</div>
    ${timeStr}
    <div style="margin-top:12px;font-size:12px;color:#909399">
      影响力：${stars}
      <br/>来源：智能模型基于公开财经媒体报道和市场观点综合生成
    </div>`,
    dangerouslyUseHTMLString: true,
    confirmButtonText: '关闭'
  })
}

function getTechSignalType(signal: string) {
  if (signal === '买入') return 'danger'
  if (signal === '卖出') return 'success'
  return 'info'
}
</script>

<style scoped lang="scss">
.ai-agent-page {
  max-width: 1400px;
  margin: 0 auto;
}

.page-card {
  border-radius: 8px;
  min-height: calc(100vh - 120px);
}

.ai-tabs {
  :deep(.el-tabs__header) {
    padding: 0 8px;
    margin-bottom: 20px;
  }
}

// ==================== Section Header ====================
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }
}

// ==================== Recommend Hint ====================
.recommend-hint {
  margin-top: 16px;
}

// ==================== Analysis Input ====================
.analysis-input-card {
  margin-bottom: 20px;
  border: 1px solid #e8eaec;
  border-radius: 8px;

  :deep(.el-form) {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
  }

  :deep(.el-form-item) {
    margin-bottom: 0;
    margin-right: 12px;
  }
}

// ==================== Result Sections ====================
.result-section {
  margin-bottom: 20px;
  border: 1px solid #e8eaec;
  border-radius: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;

  .title-icon {
    font-size: 18px;
  }
}

// ==================== Score Bar ====================
.score-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 24px;
}

.signal-badge-area {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 160px;
}

.signal-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.signal-tag {
  font-size: 16px;
  font-weight: 700;
  padding: 8px 20px;
  letter-spacing: 1px;
}

.score-items {
  flex: 1;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.score-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.score-label {
  font-size: 13px;
  color: #606266;
}

.target-price-area {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 140px;
  padding: 0 16px;
  border-left: 1px solid #e8eaec;
}

.price-label {
  font-size: 13px;
  color: #909399;
}

.price-value {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
}

// ==================== Consensus Card ====================
.consensus-card {
  margin-bottom: 20px;
  border-radius: 8px;
  border-left: 4px solid #909399;

  &.consensus-bullish {
    border-left-color: #f56c6c;
  }

  &.consensus-bearish {
    border-left-color: #67c23a;
  }

  &.consensus-neutral {
    border-left-color: #909399;
  }
}

.consensus-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.consensus-summary {
  font-size: 14px;
  color: #303133;
  flex: 1;
}

.consensus-counts {
  display: flex;
  gap: 24px;
}

.count-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 500;
}

.bullish-count {
  color: #f56c6c;
}

.bearish-count {
  color: #67c23a;
}

.neutral-count {
  color: #909399;
}

// ==================== DaV Cards Grid ====================
.dav-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.dav-card {
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #e8eaec;
  border-left: 4px solid #909399;
  background: #fafafa;
  transition: box-shadow 0.2s;
  cursor: pointer;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  }

  &.dav-bullish {
    border-left-color: #f56c6c;
  }

  &.dav-bearish {
    border-left-color: #67c23a;
  }

  &.dav-neutral {
    border-left-color: #909399;
  }
}

.dav-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.source-btn {
  font-size: 12px;
}

.dav-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dav-time {
  font-size: 11px;
  color: #a0a0a0;
}

.dav-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.dav-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.dav-view {
  font-size: 13px;
  font-weight: 500;
  color: #409eff;
  margin-bottom: 6px;
}

.dav-detail {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 10px;
}

.dav-influence {
  font-size: 13px;
  color: #909399;
}

.influence-label {
  margin-right: 4px;
}

.star {
  color: #ddd;
  font-size: 14px;

  &.active {
    color: #e6a23c;
  }
}

// ==================== Markdown Content ====================
.markdown-content {
  padding: 8px 0;
  line-height: 1.8;
  font-size: 14px;
  color: #303133;

  h2, h3, h4 {
    margin: 16px 0 8px;
    color: #1a2a4a;
  }

  h2 { font-size: 18px; }
  h3 { font-size: 16px; }
  h4 { font-size: 15px; }

  p {
    margin: 8px 0;
  }

  ul {
    padding-left: 20px;
    margin: 8px 0;
  }

  li {
    margin: 4px 0;
  }

  strong {
    color: #409eff;
  }

  em {
    color: #e6a23c;
  }
}

// ==================== History Section ====================
.history-section {
  margin-top: 32px;
}
</style>
