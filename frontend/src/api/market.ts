/**
 * 统一市场数据源接口
 * 替代分散的 stock.ts / global.ts / gold.ts 调用
 * 提供统一的行情/产品/筛选入口
 */
import request from './request'

// ==================== 统一行情查询 ====================

/** 行情类型 */
export type MarketType = 'A_STOCK' | 'US' | 'HK' | 'JP' | 'KR'

/** 统一行情记录 */
export interface MarketQuote {
  code: string
  name: string
  market: string
  currentPrice: number
  changePercent: number
  changeAmount?: number
  openPrice?: number
  highPrice?: number
  lowPrice?: number
  closePrice?: number
  volume?: number
  turnover?: number
  marketCap?: number
  turnoverRate?: number
}

/** 指数数据 */
export interface IndexQuote {
  code: string
  name: string
  current: number
  change: number
  changePercent: number
  open: number
  high: number
  low: number
  prevClose: number
}

/** 统一行情获取 */
export function getQuote(code: string, market?: string) {
  if (!market || market === 'A_STOCK') return request.get(`/stock/realtime/${code}`)
  return request.get(`/stock/global/realtime/${code}`, { params: { market } })
}

/** 统一行情列表获取 */
export function getMarketList(market: string, params: { page?: number; size?: number; keyword?: string; sortField?: string; sortOrder?: string }) {
  if (market === 'A_STOCK') return request.get('/stock/list', { params: { market, page: params.page, size: params.size, keyword: params.keyword } })
  return request.get('/stock/global/list', { params: { market, ...params } })
}

/** 统一指数获取 */
export function getIndices(market: string) {
  return request.get('/stock/global/indices', { params: { market } })
}

/** 统一搜索 */
export function searchMarket(market: string | undefined, keyword: string) {
  if (market && market !== 'A_STOCK') return request.get('/stock/global/search', { params: { market, keyword } })
  return request.get('/stock/search', { params: { keyword, market } })
}

// ==================== 黄金 ====================

export interface GoldQuote {
  price: number
  changePercent: number
  high: number
  low: number
  openPrice: number
  tradeDate: string
}

export function getGoldProducts() {
  return request.get('/stock/gold/products')
}

export function getGoldLatest(code: string = 'hf_GC') {
  return request.get('/stock/gold/latest', { params: { code } })
}

export function getGoldHistory(code: string = 'hf_GC', days: number = 30) {
  return request.get('/stock/gold/history', { params: { code, days } })
}

// ==================== K线 ====================

export function getKline(code: string, period: string = 'daily') {
  return request.get(`/stock/kline/${code}`, { params: { period } })
}

// ==================== 筛选 ====================

export interface ScreenerParams {
  minPrice?: number
  maxPrice?: number
  minChange?: number
  maxChange?: number
  minVolume?: number
  keyword?: string
  market?: string
  sortField?: string
  sortOrder?: string
  page?: number
  size?: number
}

export function queryScreener(params: ScreenerParams) {
  return request.get('/stock/screener', { params })
}

// ==================== 技术分析 ====================

export function getTradeSignal(code: string) {
  return request.get(`/analysis/signal/${code}`)
}

export function getTechnicalIndicators(code: string) {
  return request.get(`/analysis/indicators/${code}`)
}
