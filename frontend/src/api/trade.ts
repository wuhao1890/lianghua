import request from './request'
import type { ApiResponse, BuyRequest, SellRequest, Position, TradeOrder, AccountOverview, ProfitAnalysis, ProfitRecord, HuabaoBrokerStatus, RealTradeAttempt } from '@/types'

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

export function getHuabaoStatus() {
  return request.get<ApiResponse<HuabaoBrokerStatus>>('/broker/huabao/status')
}

export function saveHuabaoConfig(data: Partial<HuabaoBrokerStatus>) {
  return request.post<ApiResponse<HuabaoBrokerStatus>>('/broker/huabao/config', data)
}

export function huabaoRealBuy(data: BuyRequest) {
  return request.post<ApiResponse<any>>('/broker/huabao/buy', data)
}

export function huabaoRealSell(data: SellRequest) {
  return request.post<ApiResponse<any>>('/broker/huabao/sell', data)
}

export function huabaoCashTransfer(data: { amount: number; direction: '转入证券账户' | '转出银行卡'; remark?: string }) {
  return request.post<ApiResponse<any>>('/broker/huabao/cash-transfer', data)
}

export function getHuabaoRealTradeRecords() {
  return request.get<ApiResponse<RealTradeAttempt[]>>('/broker/huabao/real-trade-records')
}
