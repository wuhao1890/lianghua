<template>
  <div class="wechat-channel">
    <section class="page-head">
      <div>
        <h2>微信公众号渠道</h2>
        <p>使用 cooderl/wewe-rss 作为采集服务，系统负责同步、入库、分析和融入 AI 实验室。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="refreshAll">刷新</el-button>
    </section>

    <section class="channel-grid">
      <article class="panel">
        <div class="panel-head">
          <div>
            <h3>WeWe RSS 接入</h3>
            <span>推荐使用 JSON Feed：/feeds/公众号ID.json，系统同步时会自动追加 fulltext 和 update 参数。</span>
          </div>
          <el-tag type="success" effect="dark">主通道</el-tag>
        </div>
        <el-form label-position="top">
          <el-form-item label="嘟嘟地瓜 Feed 地址">
            <el-input v-model="rssForm.feedUrl" placeholder="例如 http://127.0.0.1:4000/feeds/MP_WXS_xxx.json" />
          </el-form-item>
          <el-form-item label="渠道名称">
            <el-input v-model="rssForm.account" placeholder="嘟嘟地瓜" />
          </el-form-item>
          <div class="actions">
            <el-button :loading="savingRss" @click="saveRss">保存配置</el-button>
            <el-button type="primary" :loading="syncing" @click="syncRss">同步并学习</el-button>
          </div>
        </el-form>
        <p class="hint">
          WeWe RSS 已经写好了公众号订阅、历史文章和全文输出逻辑；本系统只读取它的真实输出，不自己编造文章。
        </p>
      </article>

      <article class="panel status-panel">
        <div class="panel-head">
          <div>
            <h3>渠道状态</h3>
            <span>{{ report.sourceStatus || '等待 WeWe RSS 同步' }}</span>
          </div>
          <el-tag type="primary">{{ report.total || 0 }} 篇</el-tag>
        </div>
        <p class="summary">{{ report.summary || '保存 WeWe RSS 地址后点击同步。' }}</p>
        <div class="code-cloud">
          <button v-for="code in report.relatedCodes || []" :key="code" type="button" @click="goTarget(code)">
            {{ code }}
          </button>
        </div>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h3>学习成果</h3>
          <span>这些结论会进入 AI 实验室，作为新闻舆情和大 V 影响力证据。</span>
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
      <el-empty v-else description="还没有学习成果。请先同步 WeWe RSS。" />
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h3>文章库</h3>
          <span>来自 WeWe RSS 的真实文章会持续参与分析。</span>
        </div>
      </div>
      <el-table :data="articles" stripe>
        <el-table-column prop="title" label="标题" min-width="260" />
        <el-table-column label="关联代码" width="190">
          <template #default="{ row }">{{ (row.stockCodes || []).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="130">
          <template #default="{ row }">{{ row.source || 'WeWe RSS' }}</template>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getWechatArticles,
  getWechatLearning,
  getWechatRssConfig,
  saveWechatRssConfig,
  syncWechatRss
} from '@/api/ai'

const router = useRouter()
const loading = ref(false)
const savingRss = ref(false)
const syncing = ref(false)
const articles = ref<any[]>([])
const report = ref<any>({})
const rssForm = reactive({
  feedUrl: '',
  account: '嘟嘟地瓜'
})

const lessons = computed(() => Array.isArray(report.value.lessons) ? report.value.lessons : [])

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
      getWechatRssConfig().catch(() => ({ data: { data: {} } })),
      getWechatArticles(),
      getWechatLearning()
    ])
    const config = configRes.data?.data || {}
    rssForm.feedUrl = config.feedUrl || rssForm.feedUrl
    rssForm.account = config.account || rssForm.account
    articles.value = articleRes.data?.data || []
    report.value = learningRes.data?.data || {}
  } finally {
    loading.value = false
  }
}

async function saveRss() {
  if (!rssForm.feedUrl.trim()) {
    ElMessage.warning('请填写 WeWe RSS Feed 地址')
    return
  }
  savingRss.value = true
  try {
    await saveWechatRssConfig({
      feedUrl: rssForm.feedUrl.trim(),
      account: rssForm.account.trim() || '嘟嘟地瓜'
    })
    ElMessage.success('WeWe RSS 配置已保存')
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    savingRss.value = false
  }
}

async function syncRss() {
  if (!rssForm.feedUrl.trim()) {
    ElMessage.warning('请先填写并保存 WeWe RSS Feed 地址')
    return
  }
  syncing.value = true
  try {
    await saveWechatRssConfig({
      feedUrl: rssForm.feedUrl.trim(),
      account: rssForm.account.trim() || '嘟嘟地瓜'
    })
    const res = await syncWechatRss()
    const data = res.data?.data || {}
    ElMessage.success(`同步完成：新增读取 ${data.imported || 0} 篇，文章库共 ${data.total || 0} 篇`)
    await refreshAll()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '同步失败')
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
.panel-head {
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

.channel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
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

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.hint,
.summary {
  color: #30443a;
  line-height: 1.7;
}

.code-cloud,
.target-row,
.word-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.code-cloud button,
.target-row button {
  border: 1px solid #b9d8c4;
  border-radius: 6px;
  background: #f4fbf6;
  color: #17663a;
  cursor: pointer;
  padding: 6px 10px;
}

.lesson-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.lesson-card {
  padding: 14px;
  border: 1px solid #e4eee8;
  border-radius: 8px;
  background: #fbfdfb;

  p {
    color: #3b4d43;
    line-height: 1.7;
  }
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
  .channel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
