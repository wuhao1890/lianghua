import request from './request'
import type { ApiResponse, TechnicalIndicator, TradeSignal, BacktestResult } from '@/types'

export function getIndicators(code: string, types?: string) {
  return request.get<ApiResponse<TechnicalIndicator[]>>(`/analysis/indicators/${code}`, { params: { types } })
}

export function getTradeSignal(code: string) {
  return request.get<ApiResponse<TradeSignal>>(`/analysis/signal/${code}`)
}

export function getBacktest(code: string, strategy?: string, shortPeriod?: number, longPeriod?: number) {
  return request.get<ApiResponse<BacktestResult>>(`/analysis/backtest/${code}`, {
    params: { strategy, shortPeriod, longPeriod }
  })
}
