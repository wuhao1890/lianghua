import request from './request'

export interface FundInfo {
  code: string
  name: string
  nav: number
  accNav: number
  navDate: string
  changePercent: number
  fundType: string
}

export function getFundList() {
  return request.get('/stock/fund/list')
}

export function getFundDetail(code: string) {
  return request.get(`/stock/fund/${code}`)
}
