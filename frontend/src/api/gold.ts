import request from './request'

export interface GoldQuote {
  price: number
  changePercent: number
  high: number
  low: number
  openPrice: number
  tradeDate: string
}

export function getGoldLatest() {
  return request.get('/stock/gold/latest')
}

export function getGoldHistory(days: number = 30) {
  return request.get('/stock/gold/history', { params: { days } })
}
