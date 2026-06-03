import request from './request'

export interface GoldQuote {
  price: number
  changePercent: number
  high: number
  low: number
  openPrice: number
  tradeDate: string
}

export interface GoldProduct {
  [code: string]: string
}

export interface GoldPriceDTO {
  price: number
  changePercent: number
  high: number
  low: number
  openPrice: number
  tradeDate: string
  productCode: string
  productName: string
}

export function getGoldProducts() {
  return request.get('/stock/gold/products')
}

export function getGoldLatest(code: string = 'hf_GC', options: { silentError?: boolean } = {}) {
  return request.get('/stock/gold/latest', {
    params: { code },
    silentError: options.silentError
  } as any)
}

export function getGoldHistory(code: string = 'hf_GC', days: number = 30) {
  return request.get('/stock/gold/history', { params: { code, days } })
}
