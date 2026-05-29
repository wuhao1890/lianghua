import request from './request'
import type { ApiResponse, BuyRequest, SellRequest, Position, TradeOrder, AccountOverview, ProfitAnalysis, ProfitRecord } from '@/types'

export function buy(data: BuyRequest) {
  return request.post<ApiResponse<null>>('/trade/buy', data)
}

export function sell(data: SellRequest) {
  return request.post<ApiResponse<null>>('/trade/sell', data)
}

export function getPositions() {
  return request.get<ApiResponse<Position[]>>('/trade/positions')
}

export function getOrders(params?: { page?: number; pageSize?: number }) {
  return request.get<ApiResponse<any>>('/trade/orders', { params })
}

export function cancelOrder(id: number) {
  return request.delete<ApiResponse<null>>(`/trade/order/${id}`)
}

export function getAccountOverview() {
  return request.get<ApiResponse<AccountOverview>>('/trade/account')
}

export function getProfitAnalysis() {
  return request.get<ApiResponse<ProfitAnalysis>>('/trade/profit-analysis')
}

export function getProfitRecords(range: string = '1m') {
  return request.get<ApiResponse<ProfitRecord[]>>('/trade/profit-records', { params: { range } })
}

export function placeOrder(data: any) { return request.post('/trade/order', data) }
export function getTradeMode() { return request.get('/trade/mode') }
export function switchTradeMode(mode: string) { return request.post(`/trade/mode?mode=${mode}`) }
export function getPaperAccount() { return request.get('/trade/paper-account') }
export function checkStopConditions(id: number, currentPrice: number) { return request.get(`/trade/order/${id}/stop-check`, { params: { currentPrice } }) }
