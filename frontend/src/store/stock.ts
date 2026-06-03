import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { StockInfo, KlineData } from '@/types'
import { getStockList as getListApi, getRealtimeQuote as getQuoteApi, getKlineData as getKlineApi, getSinaAStocks, getSinaUSStocks, getSinaRealtime, getSinaIndices } from '@/api/stock'

function mapSinaStock(s: any): StockInfo {
  return {
    code: s.code,
    name: s.name,
    market: 'A' as const,
    currentPrice: s.current,
    openPrice: s.open,
    closePrice: s.prevClose,
    highPrice: s.high,
    lowPrice: s.low,
    changePercent: s.changePercent,
    changeAmount: s.change,
    volume: s.volume,
    turnover: s.amount,
    marketCap: 0,
    pe: 0,
    turnoverRate: 0,
    pb: 0,
    totalShares: 0,
    circulateShares: 0
  }
}

export const useStockStore = defineStore('stock', () => {
  const stockList = ref<StockInfo[]>([])
  const currentStock = ref<StockInfo | null>(null)
  const klineData = ref<KlineData | null>(null)
  const searchResults = ref<StockInfo[]>([])
  const loading = ref(false)
  const total = ref(0)

  // 所有已加载过的股票缓存（跨页），用于搜索
  const allStocksCache = ref<StockInfo[]>([])

  async function searchStocks(keyword: string) {
    if (!keyword.trim()) {
      searchResults.value = []
      return
    }
    const kw = keyword.trim().toLowerCase()

    // 1. 先从缓存中按名称/代码模糊搜索
    const cached = allStocksCache.value.filter(
      s => s.code.toLowerCase().includes(kw) || s.name.toLowerCase().includes(kw)
    )
    if (cached.length > 0) {
      searchResults.value = cached.slice(0, 20)
      return
    }

    // 2. 缓存没有则尝试精确代码查询新浪
    try {
      let sinaCode = keyword.trim()
      if (/^\d{6}$/.test(sinaCode)) {
        if (sinaCode.startsWith('6')) sinaCode = 'sh' + sinaCode
        else sinaCode = 'sz' + sinaCode
      }
      const res = await getSinaRealtime(sinaCode)
      const list = res.data.data || []
      searchResults.value = list.map(mapSinaStock)
    } catch {
      searchResults.value = []
    }
  }

  async function getStockList(params: { market: string; page: number; pageSize: number; keyword?: string }) {
    loading.value = true
    try {
      if (params.market === 'A_STOCK') {
        const res = await getSinaAStocks(params.page, params.pageSize)
        const mapped = (res.data.data || []).map(mapSinaStock)
        stockList.value = mapped
        total.value = res.data.total || 0
        // 缓存到搜索池（去重）
        for (const s of mapped) {
          if (!allStocksCache.value.find(c => c.code === s.code)) {
            allStocksCache.value.push(s)
          }
        }
      } else if (params.market === 'NASDAQ') {
        const res = await getSinaUSStocks(params.page, params.pageSize)
        const mapped = (res.data.data || []).map(mapSinaStock)
        stockList.value = mapped
        total.value = res.data.total || 0
        for (const s of mapped) {
          if (!allStocksCache.value.find(c => c.code === s.code)) {
            allStocksCache.value.push(s)
          }
        }
      } else {
        const res = await getListApi(params)
        const data = res.data.data as any
        stockList.value = Array.isArray(data) ? data : (data?.list || [])
        total.value = Array.isArray(data) ? data.length : (data?.total || 0)
      }
    } catch (error) {
      console.error('获取股票列表失败:', error)
      stockList.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function getStockDetail(code: string) {
    try {
      // 通过后端代理获取实时数据
      let sinaCode = code
      if (code.startsWith('6')) sinaCode = 'sh' + code
      else if (code.startsWith('0') || code.startsWith('3')) sinaCode = 'sz' + code
      else sinaCode = 'sh' + code
      
      const res = await getSinaRealtime(sinaCode)
      const list = res.data.data || []
      if (list.length > 0) {
        currentStock.value = mapSinaStock(list[0])
        return currentStock.value
      }
      // 回退到后端API
      const fallback = await getQuoteApi(code, { silentError: true })
      currentStock.value = fallback.data.data
      return fallback.data.data
    } catch (error) {
      currentStock.value = null
      return null
    }
  }

  async function getKlineData(code: string, period: string = 'daily') {
    try {
      const res = await getKlineApi(code, period)
      klineData.value = res.data.data
      return res.data.data
    } catch (error) {
      klineData.value = null
      return null
    }
  }

  return {
    stockList,
    currentStock,
    klineData,
    searchResults,
    loading,
    total,
    searchStocks,
    getStockList,
    getStockDetail,
    getKlineData
  }
})
