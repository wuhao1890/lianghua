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

export interface FundNavRecord {
  date: string
  nav: number
  accNav: number
}

export function getFundList(params: { keyword?: string; fundType?: string; page?: number; pageSize?: number }, options: { silentError?: boolean } = {}) {
  return request.get('/stock/fund/list', {
    params,
    silentError: options.silentError
  } as any)
}

export function getFundDetail(code: string) {
  return request.get(`/stock/fund/${code}`)
}

export function getFundNavHistory(code: string, days: number = 30) {
  return request.get(`/stock/fund/${code}/nav`, { params: { days } })
}
