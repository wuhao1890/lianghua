<template>
  <div class="wechat-channel">
    <section class="page-head">
      <div>
        <h2>微信公众号渠道</h2>
        <p>系统自动订阅、同步、入库和学习公众号文章，作为新闻舆情与大 V 影响力证据。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="refreshAll">刷新状态</el-button>
        <el-button type="primary" :loading="syncing" @click="syncRss">立即补跑同步</el-button>
      </div>
    </section>

    <section class="channel-grid">
      <article class="panel">
        <div class="panel-head">
          <div>
            <h3>自动同步</h3>
            <span>{{ report.sourceStatus || config.lastSyncStatus || '等待自动同步' }}</span>
          </div>
          <el-tag :type="statusType">{{ statusText }}</el-tag>
        </div>
        <div class="status-grid">
          <div>
            <b>{{ config.syncIntervalMinutes || 5 }} 分钟</b>
            <span>同步间隔</span>
          </div>
          <div>
            <b>{{ sources.length }} 个</b>
            <span>订阅源</span>
          </div>
          <div>
            <b>{{ report.total || 0 }} 篇</b>
            <span>已学习文章</span>
          </div>
        </div>
        <p class="summary">{{ report.summary || '系统会自动同步已订阅公众号；按钮只用于排错或立即补跑。' }}</p>
        <p v-if="config.lastSyncAt" class="meta-line">最近同步：{{ formatTime(config.lastSyncAt) }}</p>
        <p v-if="config.lastSyncError" class="error-line">最近问题：{{ config.lastSyncError }}</p>
      </article>

      <article class="panel">
        <div class="panel-head">
          <div>
            <h3>新增公众号</h3>
            <span>粘贴该公众号任意一篇文章分享链接，系统会识别并加入订阅源。</span>
          </div>
        </div>
        <el-form label-position="top" class="source-form">
          <el-form-item label="公众号名称">
            <el-input v-model="sourceForm.name" placeholder="例如 嘟嘟地瓜" />
          </el-form-item>
          <el-form-item label="文章分享链接">
            <el-input v-model="sourceForm.shareLink" placeholder="https://mp.weixin.qq.com/s/..." />
          </el-form-item>
          <el-button type="primary" :loading="subscribing" @click="subscribeSource">加入订阅源</el-button>
        </el-form>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h3>订阅源</h3>
          <span>嘟嘟地瓜只是当前第一条订阅源，后续公众号都会在这里统一管理。</span>
        </div>
      </div>
      <div class="source-list">
        <article v-for="item in sources" :key="item.id" class="source-card">
          <div class="source-main">
            <strong>{{ item.name }}</strong>
            <el-tag :type="item.lastError ? 'danger' : item.feedUrl ? 'success' : 'info'">
              {{ item.lastError ? '同步失败' : item.feedUrl ? '已订阅' : '待订阅' }}
            </el-tag>
          </div>
          <p>文章链接：{{ item.shareLink || '等待填写' }}</p>
          <p v-if="item.feedUrl">同步地址：{{ item.feedUrl }}</p>
          <p v-if="item.lastSyncedAt">最近读取：{{ formatTime(item.lastSyncedAt) }}</p>
          <p v-if="item.lastError" class="error-line">{{ item.lastError }}</p>
          <el-button size="small" :loading="subscribing" @click="subscribeExisting(item)">重新识别订阅</el-button>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h3>学习成果</h3>
          <span>这些结论会进入 AI 实验室，参与新闻舆情和大 V 影响力评分。</span>
        </div>
      </div>
      <div v-if="lessons.length" class="lesson-grid">
        <article v-for="item in lessons" :key="item.id" class="lesson-card">
          <div class="lesson-top">
            <strong>{{ item.title }}</strong>
            <el-tag :type="tagType(item.sentiment)">{{ item.sentimentText }}</el-tag>
          </div>
          <p>{{ item.lesson }}</p>
          <p>{{ item.labImpact }}</p>
          <div class="word-row">
            <span v-for="word in item.opportunityWords || []" :key="'o-' + item.id + word" class="good">{{ word }}</span>
            <span v-for="word in item.riskWords || []" :key="'r-' + item.id + word" class="risk">{{ word }}</span>
          </div>
          <div class="target-row">
            <button v-for="code in item.stockCodes || []" :key="code" type="button" @click="goTarget(code)">
              {{ code }}
            </button>
          </div>
          <a v-if="item.url" :href="item.url" target="_blank" rel="noreferrer">打开原文</a>
        </article>
      </div>
      <el-empty v-else description="还没有学习成果，系统会自动尝试同步已订阅公众号。" />
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h3>文章库</h3>
          <span>这里只展示真实同步或真实导入的文章，不生成虚拟文章。</span>
        </div>
      </div>
      <el-table :data="articles" stripe>
        <el-table-column prop="title" label="标题" min-width="260" />
        <el-table-column label="关联代码" width="190">
          <template #default="{ row }">{{ (row.stockCodes || []).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="150">
          <template #default="{ row }">{{ row.account || row.source || '微信公众号' }}</template>
        </el-table-column>
        <el-table-column label="导入时间" width="190">
          <template #default="{ row }">{{ formatTime(row.importedAt || row.publishTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-link v-if="row.url" :href="row.url" target="_blank">原文</el-link>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getWechatArticles,
  getWechatLearning,
  getWechatRssConfig,
  subscribeWechatSource,
  syncWechatRss
} from '@/api/ai'

const router = useRouter()
const loading = ref(false)
const subscribing = ref(false)
const syncing = ref(false)
const articles = ref<any[]>([])
const report = ref<any>({})
const config = ref<any>({})
const defaultSource = {
  id: 'dududigua',
  name: '嘟嘟地瓜',
  shareLink: 'https://mp.weixin.qq.com/s/j_eNtoE8lD1ph3GNkyc16A?scene=1',
  status: '待自动订阅',
  enabled: true
}
const sourceForm = ref({
  name: '',
  shareLink: ''
})

const sources = computed(() => {
  const list = Array.isArray(config.value.sources) ? config.value.sources : []
  return list.length ? list : [defaultSource]
})
const lessons = computed(() => Array.isArray(report.value.lessons) ? report.value.lessons : [])
const statusText = computed(() => config.value.lastSyncError ? '需要处理' : config.value.lastSyncAt ? '自动运行' : '准备中')
const statusType = computed(() => config.value.lastSyncError ? 'danger' : config.value.lastSyncAt ? 'success' : 'info')

function tagType(sentiment?: string) {
  if (sentiment === 'bullish') return 'danger'
  if (sentiment === 'bearish') return 'success'
  return 'info'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function goTarget(code: string) {
  if (/^(110|159|51)/.test(code)) router.push(`/fund/${code}`)
  else router.push(`/stock/${code}`)
}

async function refreshAll() {
  loading.value = true
  try {
    const [configRes, articleRes, learningRes] = await Promise.all([
      getWechatRssConfig({ silentError: true }).catch(() => ({ data: { data: {} } })),
      getWechatArticles({ silentError: true }).catch((error: any) => ({ data: { data: [] }, error })),
      getWechatLearning({ silentError: true }).catch((error: any) => ({ data: { data: {} }, error }))
    ])
    config.value = configRes.data?.data || {}
    articles.value = articleRes.data?.data || []
    report.value = learningRes.data?.data || {}
    const loadError = (articleRes as any).error || (learningRes as any).error
    if (loadError) {
      config.value = {
        ...config.value,
        lastSyncStatus: '读取失败',
        lastSyncError: loadError?.response?.data?.message || '接口暂时不可用，页面已保留可操作状态'
      }
    }
  } finally {
    loading.value = false
  }
}

async function subscribeSource() {
  if (!sourceForm.value.shareLink.trim()) {
    ElMessage.warning('请填写公众号文章分享链接')
    return
  }
  subscribing.value = true
  try {
    await subscribeWechatSource({
      name: sourceForm.value.name.trim() || '微信公众号',
      shareLink: sourceForm.value.shareLink.trim()
    }, { silentError: true })
    sourceForm.value.name = ''
    sourceForm.value.shareLink = ''
    ElMessage.success('公众号已加入订阅源，系统会自动同步')
    await syncWechatRss({ silentError: true })
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '订阅失败，请确认采集服务已启动并已扫码登录')
  } finally {
    subscribing.value = false
  }
}

async function subscribeExisting(item: any) {
  subscribing.value = true
  try {
    await subscribeWechatSource(item, { silentError: true })
    ElMessage.success('订阅源已更新')
    await syncWechatRss({ silentError: true })
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '订阅源更新失败')
  } finally {
    subscribing.value = false
  }
}

async function syncRss() {
  syncing.value = true
  try {
    const res = await syncWechatRss({ silentError: true })
    const data = res.data?.data || {}
    ElMessage.success(`同步完成：读取 ${data.imported || 0} 篇，文章库共 ${data.total || 0} 篇`)
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '同步失败，请检查采集服务状态')
    await refreshAll()
  } finally {
    syncing.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped lang="scss">
.wechat-channel {
  max-width: 1400px;
}

.page-head,
.panel-head,
.source-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.page-head {
  margin-bottom: 16px;

  h2 {
    margin: 0 0 6px;
  }

  p {
    margin: 0;
    color: #607066;
  }
}

.head-actions,
.source-form {
  display: flex;
  gap: 8px;
}

.channel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.8fr);
  gap: 16px;
  margin-bottom: 16px;
}

.panel {
  padding: 18px;
  margin-bottom: 16px;
  border: 1px solid #dfeee5;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(18, 70, 43, 0.06);
}

.panel-head {
  margin-bottom: 14px;

  h3 {
    margin: 0 0 4px;
  }

  span {
    color: #7b8a82;
    font-size: 13px;
  }
}

.status-grid,
.source-list,
.lesson-grid {
  display: grid;
  gap: 12px;
}

.status-grid {
  grid-template-columns: repeat(3, 1fr);
  margin-bottom: 12px;

  div {
    padding: 12px;
    border-radius: 8px;
    background: #f6faf7;
  }

  b,
  span {
    display: block;
  }

  span {
    margin-top: 4px;
    color: #6d7b72;
    font-size: 13px;
  }
}

.source-form {
  flex-direction: column;
}

.source-list {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.source-card,
.lesson-card {
  padding: 14px;
  border: 1px solid #e4eee8;
  border-radius: 8px;
  background: #fbfdfb;

  p {
    color: #3b4d43;
    line-height: 1.7;
    word-break: break-all;
  }
}

.summary,
.meta-line {
  color: #30443a;
  line-height: 1.7;
}

.error-line {
  color: #238044;
  line-height: 1.7;
}

.target-row,
.word-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.target-row button {
  border: 1px solid #b9d8c4;
  border-radius: 6px;
  background: #f4fbf6;
  color: #17663a;
  cursor: pointer;
  padding: 6px 10px;
}

.lesson-grid {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.lesson-card p {
  color: #3b4d43;
  line-height: 1.7;
}

.lesson-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.word-row span {
  border-radius: 5px;
  padding: 4px 8px;
  font-size: 12px;
}

.word-row .good {
  background: #fdeeee;
  color: #c93c3c;
}

.word-row .risk {
  background: #eef8f0;
  color: #24864a;
}

@media (max-width: 900px) {
  .channel-grid,
  .status-grid {
    grid-template-columns: 1fr;
  }

  .page-head,
  .head-actions {
    flex-direction: column;
  }
}
</style>
