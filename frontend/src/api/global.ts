import request from './request'

export function getGlobalStockList(market: string, params: { page?: number; size?: number; keyword?: string }) {
  return request.get(`/stock/global/list`, { params: { market, ...params } })
}

export function getGlobalRealtime(code: string, market: string) {
  return request.get(`/stock/global/realtime/${code}`, { params: { market } })
}

export function getGlobalIndices(market: string) {
  return request.get(`/stock/global/indices`, { params: { market } })
}

export function searchGlobal(market: string, keyword: string) {
  return request.get(`/stock/global/search`, { params: { market, keyword } })
}
