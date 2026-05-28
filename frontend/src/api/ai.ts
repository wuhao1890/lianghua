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

export function analyzeStock(data: { stockCode: string; configId: number; customPrompt?: string }) {
  return request.post('/ai/analyze', data, { timeout: 120000 })
}

export function getAnalysisHistory() {
  return request.get('/ai/history')
}
