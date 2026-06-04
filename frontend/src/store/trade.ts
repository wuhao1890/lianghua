import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Position, TradeOrder, AccountOverview, ProfitAnalysis, ProfitRecord, BuyRequest, SellRequest } from '@/types'
import { buy as buyApi, sell as sellApi, getPositions as getPositionsApi, getOrders as getOrdersApi, cancelOrder as cancelOrderApi, getAccountOverview as getOverviewApi, getProfitAnalysis as getProfitApi, getProfitRecords } from '@/api/trade'

export const useTradeStore = defineStore('trade', () => {
  const positions = ref<Position[]>([])
  const orders = ref<TradeOrder[]>([])
  const accountOverview = ref<AccountOverview | null>(null)
  const profitAnalysis = ref<ProfitAnalysis | null>(null)
  const profitRecords = ref<ProfitRecord[]>([])
  const loading = ref(false)
  const total = ref(0)

  async function buy(data: BuyRequest) {
    const res = await buyApi(data)
    return res.data.data
  }

  async function sell(data: SellRequest) {
    const res = await sellApi(data)
    return res.data.data
  }

  async function getPositions() {
    loading.value = true
    try {
      const res = await getPositionsApi()
      // 后端字段映射到前端字段
      positions.value = (res.data.data || []).map((p: any) => ({
        id: p.id,
        userId: p.userId,
        stockCode: p.stockCode,
        stockName: p.stockName || p.stockCode, // 兼容
        market: p.market === 'A_STOCK' || p.market === 'A' || /^\d{6}$/.test(String(p.stockCode || '')) ? 'A' : 'US',
        quantity: Number(p.quantity || 0),
        availableQuantity: Number(p.availableQuantity ?? p.quantity ?? 0),
        costPrice: Number(p.costPrice ?? p.avgCost ?? 0),
        currentPrice: Number(p.currentPrice || 0),
        marketValue: Number(p.marketValue ?? (Number(p.currentPrice || 0) * Number(p.quantity || 0))),
        profit: Number(p.profit ?? p.profitLoss ?? 0),
        profitPercent: Number(p.profitPercent ?? p.profitLossPercent ?? 0),
        todayProfit: Number(p.todayProfit ?? p.profit ?? p.profitLoss ?? 0),
        todayProfitPercent: Number(p.todayProfitPercent ?? p.profitPercent ?? p.profitLossPercent ?? 0),
        strategyName: p.strategyName,
        bucketName: p.bucketName,
        source: p.source,
        assetType: p.assetType
      }))
    } catch (error) {
      positions.value = []
    } finally {
      loading.value = false
    }
  }

  async function getOrders(params: { page: number; pageSize: number; direction?: string; status?: string; startDate?: string; endDate?: string }) {
    loading.value = true
    try {
      const res = await getOrdersApi(params)
      const data = res.data.data as any
      const list = Array.isArray(data) ? data : (data?.list || [])
      orders.value = list.map((o: any) => ({
        ...o,
        createdAt: o.createdAt || o.createTime,
        updatedAt: o.updatedAt || o.updateTime
      }))
      total.value = Array.isArray(data) ? data.length : (data?.total || list.length)
    } catch (error) {
      orders.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function cancelOrder(orderId: number) {
    const res = await cancelOrderApi(orderId)
    return res.data.data
  }

  async function getAccountOverview() {
    try {
      const res = await getOverviewApi()
      accountOverview.value = res.data.data
      return res.data.data
    } catch (error) {
      accountOverview.value = null
      return null
    }
  }

  async function getProfitAnalysis() {
    try {
      const res = await getProfitApi()
      const data = res.data.data as any
      const mapped = {
        ...data,
        totalTradeCount: data.totalTradeCount ?? data.totalTrades ?? 0,
        winCount: data.winCount ?? data.winTrades ?? 0,
        loseCount: data.loseCount ?? data.loseTrades ?? 0,
        winRate: Number(data.winRate || 0),
        totalLoss: data.totalLoss ?? 0,
        avgProfit: data.avgProfit ?? 0,
        avgLoss: data.avgLoss ?? 0,
        profitLossRatio: data.profitLossRatio ?? 0,
        maxDrawdown: data.maxDrawdown ?? 0,
        sharpeRatio: data.sharpeRatio ?? 0
      }
      profitAnalysis.value = mapped
      return mapped
    } catch (error) {
      profitAnalysis.value = null
      return null
    }
  }

  async function fetchProfitRecords(range: string = '1m') {
    try {
      const res = await getProfitRecords(range)
      profitRecords.value = res.data.data || []
      return res.data.data
    } catch (error) {
      profitRecords.value = []
      return []
    }
  }

  return {
    positions,
    orders,
    accountOverview,
    profitAnalysis,
    profitRecords,
    loading,
    total,
    buy,
    sell,
    getPositions,
    getOrders,
    cancelOrder,
    getAccountOverview,
    getProfitAnalysis,
    fetchProfitRecords
  }
})
