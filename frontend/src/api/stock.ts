import request from './request'
import type { ApiResponse, StockInfo, KlineData, PageResult } from '@/types'

export function searchStocks(keyword: string) {
  return request.get<ApiResponse<StockInfo[]>>('/stock/search', { params: { keyword } })
}

export function getRealtimeQuote(code: string, options: { silentError?: boolean } = {}) {
  return request.get<ApiResponse<StockInfo>>(`/stock/realtime/${code}`, {
    silentError: options.silentError
  } as any)
}

export function getKlineData(code: string, period: string = 'daily') {
  return request.get<ApiResponse<KlineData>>(`/stock/kline/${code}`, { params: { period } })
}

export function getStockList(params: { market: string; page: number; pageSize: number; keyword?: string }) {
  return request.get<ApiResponse<PageResult<StockInfo>>>('/stock/list', { params })
}

// 新浪财经代理API（通过后端转发）
export function getSinaAStocks(page: number = 1, pageSize: number = 20, options: { silentError?: boolean } = {}) {
  return request.get('/stock/sina/a-stocks', {
    params: { page, pageSize },
    silentError: options.silentError
  } as any)
}

export function getSinaUSStocks(page: number = 1, pageSize: number = 20) {
  return request.get('/stock/sina/us-stocks', { params: { page, pageSize } })
}

export function getSinaRealtime(codes: string) {
  return request.get('/stock/sina/realtime', { params: { codes } })
}

export function getSinaIndices() {
  return request.get('/stock/sina/indices')
}
