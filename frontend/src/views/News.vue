<template>
  <div class="news-page">
    <section class="page-head">
      <div>
        <h2>新闻中心</h2>
        <p>支持分类、关键词、股票代码搜索；央视新闻作为重点源，智能判断可能关联的股票或板块。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadNews">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </section>

    <section class="filters">
      <el-select v-model="category" placeholder="新闻分类" clearable>
        <el-option label="宏观" value="宏观" />
        <el-option label="金融" value="金融" />
        <el-option label="科技" value="科技" />
        <el-option label="消费" value="消费" />
        <el-option label="能源" value="能源" />
        <el-option label="医药" value="医药" />
        <el-option label="地产" value="地产" />
      </el-select>
      <el-input v-model="keyword" placeholder="关键词，如 人工智能、银行、能源" clearable @keyup.enter="loadNews" />
      <el-input v-model="stockCode" placeholder="股票代码，如 600036" clearable @keyup.enter="loadNews" />
      <el-button @click="clearFilters">清空</el-button>
    </section>

    <section class="news-list" v-if="newsList.length">
      <a v-for="item in newsList" :key="item.url || item.title" class="news-item" :href="item.url" target="_blank" rel="noreferrer">
        <div class="news-main">
          <div class="title-row">
            <el-tag size="small" :type="item.source === '央视新闻' ? 'danger' : 'info'">{{ item.source || '新闻源' }}</el-tag>
            <el-tag size="small" effect="plain">{{ (item as any).category || '财经' }}</el-tag>
            <strong>{{ item.title }}</strong>
          </div>
          <p>{{ item.reason }}</p>
          <div class="meta">
            <span>{{ item.publishTime || '时间未知' }}</span>
            <span>智能关联：{{ (item as any).relatedStockHint || stockCode || '待识别' }}</span>
          </div>
        </div>
        <div class="news-side">
          <el-tag :type="tagType(item.sentiment)">{{ sentimentText(item.sentiment) }}</el-tag>
          <span>{{ item.impactScore ?? 50 }}/100</span>
        </div>
      </a>
    </section>

    <el-empty v-else :image-size="80" :description="loading ? '正在获取真实新闻...' : '暂无新闻，换个关键词或股票代码试试'" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getNewsFeed } from '@/api/ai'
import type { NewsItem } from '@/types'

const route = useRoute()
const stockCode = ref(String(route.query.code || ''))
const keyword = ref('')
const category = ref('')
const loading = ref(false)
const newsList = ref<NewsItem[]>([])

async function loadNews() {
  loading.value = true
  try {
    const res = await getNewsFeed({
      stockCode: stockCode.value.trim() || undefined,
      keyword: keyword.value.trim() || undefined,
      category: category.value || undefined
    })
    newsList.value = res.data?.data || []
  } catch (error: any) {
    ElMessage.error(error?.message || '新闻加载失败')
    newsList.value = []
  } finally {
    loading.value = false
  }
}

function clearFilters() {
  stockCode.value = ''
  keyword.value = ''
  category.value = ''
  loadNews()
}

function tagType(sentiment?: string) {
  if (sentiment === 'bullish') return 'danger'
  if (sentiment === 'bearish') return 'success'
  return 'info'
}

function sentimentText(sentiment?: string) {
  if (sentiment === 'bullish') return '偏多'
  if (sentiment === 'bearish') return '偏空'
  return '中性'
}

onMounted(loadNews)
</script>

<style scoped lang="scss">
.news-page {
  max-width: 1200px;
  display: grid;
  gap: 14px;
}

.page-head,
.filters,
.news-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;

  h2 {
    margin: 0 0 4px;
    color: #1f2d3d;
    font-size: 22px;
  }

  p {
    margin: 0;
    color: #606266;
  }
}

.filters {
  display: grid;
  grid-template-columns: 160px minmax(180px, 1fr) 220px auto;
  gap: 10px;
  padding: 14px;
}

.news-list {
  display: grid;
  gap: 10px;
}

.news-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  color: inherit;
  text-decoration: none;

  &:hover {
    border-color: #33b86a;
  }
}

.news-main {
  min-width: 0;

  p {
    margin: 8px 0;
    color: #606266;
    line-height: 1.6;
  }
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;

  strong {
    color: #303133;
    line-height: 1.5;
  }
}

.meta {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  color: #909399;
  font-size: 12px;
}

.news-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  color: #909399;
  font-size: 12px;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .page-head,
  .news-item {
    flex-direction: column;
    align-items: stretch;
  }

  .filters {
    grid-template-columns: 1fr;
  }

  .news-side {
    align-items: flex-start;
  }
}
</style>
