import request from './request'

export interface SectorInfo {
  sectorName: string
  sectorCode: string
  changePercent: number | null
  leaderStock: string | null
  leaderName: string | null
  stockCount: number
  avgChange: number | null
  leaderChangePercent: number | null
}

export interface SectorStock {
  stockCode: string
  stockName: string
  currentPrice: number
  changePercent: number
  marketCap: number | null
}

export interface SectorDetail {
  sectorName: string
  sectorCode: string
  changePercent: number | null
  leaderStock: string | null
  leaderName: string | null
  stockCount: number
  avgChange: number | null
  stocks: SectorStock[]
}

export interface AiSectorAnalysis {
  topSectors: AiTopSector[]
  analysisTime: string
}

export interface AiTopSector {
  sectorName: string
  sectorCode: string
  changePercent: number
  aiReason: string
  leaderStocks: AiLeaderStock[]
}

export interface AiLeaderStock {
  code: string
  name: string
  changePercent: number
  aiTrend: string
  aiReason: string
}

/** 获取所有板块 */
export function getAllSectors() {
  return request.get('/stock/sectors')
}

/** 获取板块详情 */
export function getSectorDetail(code: string) {
  return request.get(`/stock/sectors/${code}`)
}

/** 获取板块成分股 */
export function getSectorStocks(code: string) {
  return request.get(`/stock/sectors/${code}/stocks`)
}

/** AI板块分析 */
export function aiAnalyzeSectors() {
  return request.post('/ai/sector/analyze', {}, { timeout: 120000 })
}

/** 获取最新AI板块分析报告 */
export function getAiSectorReport() {
  return request.get('/ai/sector/report')
}

/** AI推荐五大板块和每个板块五只股票 */
export function getAiTopSectorPicks() {
  return request.get('/ai/sector/top-picks')
}
