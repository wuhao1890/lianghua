import request from './request'

export interface AiModelConfig {
  id: number
  name: string
  provider: string
  apiKey: string
  baseUrl: string
  modelName: string
  enabled: boolean
  createTime: string
}

export interface AiModelConfigRequest {
  name: string
  provider: string
  apiKey: string
  baseUrl: string
  modelName: string
}

export function getModelConfigs() {
  return request.get('/ai/configs')
}

export function createModelConfig(data: AiModelConfigRequest) {
  return request.post('/ai/configs', data)
}

export function updateModelConfig(id: number, data: AiModelConfigRequest) {
  return request.put(`/ai/configs/${id}`, data)
}

export function deleteModelConfig(id: number) {
  return request.delete(`/ai/configs/${id}`)
}

export function testModelConfig(id: number) {
  return request.post(`/ai/configs/${id}/test`)
}

export function analyzeStock(data: { stockCode: string; configId: number; customPrompt?: string }, options: { silentError?: boolean } = {}) {
  return request.post('/ai/analyze', data, {
    timeout: 120000,
    silentError: options.silentError
  } as any)
}

export function getStockNews(stockCode: string) {
  return request.get('/ai/news', { params: { stockCode } })
}

export function getNewsFeed(params: { stockCode?: string; keyword?: string; category?: string }) {
  return request.get('/ai/news', { params })
}

export function getWechatArticles(options: { silentError?: boolean } = {}) {
  return request.get('/ai/wechat/articles', { silentError: options.silentError } as any)
}

export function getWechatLearning(options: { silentError?: boolean } = {}) {
  return request.get('/ai/wechat/learning', { silentError: options.silentError } as any)
}

export function getWechatRssConfig(options: { silentError?: boolean } = {}) {
  return request.get('/ai/wechat/rss-config', { silentError: options.silentError } as any)
}

export function saveWechatRssConfig(data: any) {
  return request.post('/ai/wechat/rss-config', data)
}

export function syncWechatRss(options: { silentError?: boolean } = {}) {
  return request.post('/ai/wechat/sync', undefined, { silentError: options.silentError } as any)
}

export function subscribeWechatSource(data?: any, options: { silentError?: boolean } = {}) {
  return request.post('/ai/wechat/subscribe', data || {}, { silentError: options.silentError } as any)
}

export function importWechatArticle(data: {
  title: string
  content: string
  url?: string
  stockCodes?: string[]
  keywords?: string[]
  publishTime?: string
}) {
  return request.post('/ai/wechat/articles', data)
}

export function getAnalysisHistory() {
  return request.get('/ai/history')
}

export function getAiLabState(options: { silentError?: boolean } = {}) {
  return request.get('/ai/lab/state', { silentError: options.silentError } as any)
}

export function saveAiLabState(data: any, options: { silentError?: boolean } = {}) {
  return request.post('/ai/lab/state', data, { silentError: options.silentError } as any)
}

export function saveAiLabIteration(data: any, options: { silentError?: boolean } = {}) {
  return request.post('/ai/lab/iteration', data, { silentError: options.silentError } as any)
}

export function getAiLabIterations() {
  return request.get('/ai/lab/iterations')
}
