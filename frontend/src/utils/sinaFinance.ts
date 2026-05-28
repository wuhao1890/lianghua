import axios from 'axios'

// 新浪财经实时行情接口（使用Vite代理）
const SINA_API = '/sina-api/list='

// A股常用股票列表（沪深300成分股示例）
const A_STOCK_LIST = [
  'sh600519', 'sh600036', 'sh601318', 'sh600276', 'sh601888',
  'sh600887', 'sh600009', 'sh601166', 'sh601328', 'sh601398',
  'sh600030', 'sh600016', 'sh601288', 'sh601628', 'sh600028',
  'sh601857', 'sh600050', 'sh600031', 'sh600585', 'sh601088',
  'sh600048', 'sh601012', 'sh600900', 'sh600104', 'sh601311',
  'sh600690', 'sh601658', 'sh601899', 'sh600588', 'sh600837',
  'sh601319', 'sh601601', 'sh601336', 'sh601818', 'sh600000',
  'sh600015', 'sh600018', 'sh600023', 'sh600027', 'sh600029',
  'sh600031', 'sh600089', 'sh600109', 'sh600111', 'sh600115',
  'sh600150', 'sh600170', 'sh600176', 'sh600183', 'sh600196',
  'sh600208', 'sh600233', 'sh600276', 'sh600297', 'sh600309',
  'sh600332', 'sh600345', 'sh600398', 'sh600406', 'sh600436',
  'sh600438', 'sh600460', 'sh600470', 'sh600482', 'sh600487',
  'sh600498', 'sh600521', 'sh600547', 'sh600570', 'sh600585',
  'sh600606', 'sh600637', 'sh600660', 'sh600703', 'sh600745',
  'sh600760', 'sh600809', 'sh600862', 'sh600893', 'sh600905',
  'sh600918', 'sh600926', 'sh600941', 'sh600989', 'sh600999',
  'sh601006', 'sh601012', 'sh601016', 'sh601021', 'sh601066',
  'sh601117', 'sh601138', 'sh601155', 'sh601166', 'sh601169',
  'sh601186', 'sh601198', 'sh601211', 'sh601225', 'sh601236',
  'sh601288', 'sh601319', 'sh601336', 'sh601390', 'sh601398',
  'sh601601', 'sh601628', 'sh601658', 'sh601668', 'sh601688',
  'sh601698', 'sh601728', 'sh601766', 'sh601799', 'sh601816',
  'sh601818', 'sh601857', 'sh601877', 'sh601888', 'sh601898',
  'sh601899', 'sh601919', 'sh601985', 'sh601988', 'sh601989',
  'sh601995', 'sh603259', 'sh603288', 'sh603501', 'sh603799',
  'sh603986', 'sz000001', 'sz000002', 'sz000063', 'sz000066',
  'sz000100', 'sz000333', 'sz000338', 'sz000425', 'sz000568',
  'sz000651', 'sz000661', 'sz000725', 'sz000768', 'sz000858',
  'sz000876', 'sz000895', 'sz000938', 'sz001965', 'sz002027',
  'sz002044', 'sz002050', 'sz002142', 'sz002230', 'sz002236',
  'sz002252', 'sz002304', 'sz002311', 'sz002352', 'sz002371',
  'sz002410', 'sz002415', 'sz002459', 'sz002460', 'sz002475',
  'sz002493', 'sz002594', 'sz002601', 'sz002607', 'sz002714',
  'sz002736', 'sz002812', 'sz002841', 'sz002920', 'sz300015',
  'sz300059', 'sz300122', 'sz300124', 'sz300142', 'sz300274',
  'sz300347', 'sz300364', 'sz300408', 'sz300450', 'sz300496',
  'sz300529', 'sz300750', 'sz300896'
]

// 美股列表
const US_STOCK_LIST = [
  'gb_aapl', 'gb_msft', 'gb_googl', 'gb_amzn', 'gb_nvda',
  'gb_tsla', 'gb_meta', 'gb_brk.b', 'gb_jpm', 'gb_v',
  'gb_unh', 'gb_hd', 'gb_proct', 'gb_ma', 'gb_dis',
  'gb_pfe', 'gb_ko', 'gb_pep', 'gb_abt', 'gb_t',
  'gb_xom', 'gb_cvx', 'gb_wmt', 'gb_bac', 'gb_c',
  'gb_ms', 'gb_gs', 'gb_nke', 'gb_mcd', 'gb_inTC'
]

export interface SinaStockData {
  code: string
  name: string
  open: number
  close: number  // 昨收
  current: number // 现价
  high: number
  low: number
  volume: number  // 成交量
  amount: number  // 成交额
  changePercent: number  // 涨跌幅
  changeAmount: number   // 涨跌额
  turnoverRate: number   // 换手率
  marketCap: number     // 总市值
  pe: number           // 市盈率
}

export async function fetchAStocks(page: number = 1, pageSize: number = 20): Promise<{ list: SinaStockData[], total: number }> {
  const start = (page - 1) * pageSize
  const end = start + pageSize
  const codes = A_STOCK_LIST.slice(start, end).join(',')
  
  const response = await axios.get(`${SINA_API}${codes}`)
  
  const list: SinaStockData[] = []
  const lines = response.data.split('\n')
  
  for (const line of lines) {
    if (!line.trim()) continue
    
    const match = line.match(/hq_str_(\w+)="(.+)"/)
    if (match) {
      const code = match[1]
      const fields = match[2].split(',')
      
      if (fields.length >= 32) {
        const name = fields[0]
        const open = parseFloat(fields[1]) || 0
        const close = parseFloat(fields[2]) || 0  // 昨收
        const current = parseFloat(fields[3]) || 0  // 现价
        const high = parseFloat(fields[4]) || 0
        const low = parseFloat(fields[5]) || 0
        const volume = parseFloat(fields[8]) || 0   // 成交量(手)
        const amount = parseFloat(fields[9]) || 0  // 成交额(元)
        const changePercent = close > 0 ? ((current - close) / close * 100) : 0
        const changeAmount = current - close
        
        list.push({
          code: code.replace(/^(sh|sz|gb_)/, ''),
          name: name,
          open,
          close,
          current,
          high,
          low,
          volume,
          amount,
          changePercent,
          changeAmount,
          turnoverRate: 0,
          marketCap: 0,
          pe: 0
        })
      }
    }
  }
  
  return {
    list,
    total: A_STOCK_LIST.length
  }
}

// 大盘指数列表
export const INDEX_LIST = [
  'sh000001',  // 上证指数
  'sz399001',  // 深证成指
  'sz399006',  // 创业板指
  'sh000300'   // 沪深300
]

export interface IndexData {
  code: string
  name: string
  current: number
  change: number
  changePercent: number
  high: number
  low: number
  open: number
  prevClose: number
  volume: number   // 成交量(万手)
  amount: number   // 成交额(亿元)
}

export async function fetchIndices(): Promise<IndexData[]> {
  const codes = INDEX_LIST.join(',')
  const response = await axios.get(`${SINA_API}${codes}`)
  
  const indices: IndexData[] = []
  const lines = response.data.split('\n')
  
  const nameMap: Record<string, string> = {
    'sh000001': '上证指数',
    'sz399001': '深证成指',
    'sz399006': '创业板指',
    'sh000300': '沪深300'
  }
  
  for (const line of lines) {
    if (!line.trim()) continue
    
    const match = line.match(/hq_str_(\w+)="(.+)"/)
    if (match) {
      const code = match[1]
      const fields = match[2].split(',')
      
      if (fields.length >= 32) {
        const name = nameMap[code] || fields[0]
        const current = parseFloat(fields[3]) || 0
        const close = parseFloat(fields[2]) || 0  // 昨收
        const change = current - close
        const changePercent = close > 0 ? (change / close * 100) : 0
        
        indices.push({
          code: code.replace(/^(sh|sz)/, ''),
          name,
          current,
          change,
          changePercent,
          high: parseFloat(fields[4]) || 0,
          low: parseFloat(fields[5]) || 0,
          open: parseFloat(fields[1]) || 0,
          prevClose: close,
          volume: parseFloat(fields[8]) || 0,
          amount: parseFloat(fields[9]) || 0
        })
      }
    }
  }
  
  return indices
}

export async function fetchUSStocks(page: number = 1, pageSize: number = 20): Promise<{ list: SinaStockData[], total: number }> {
  const start = (page - 1) * pageSize
  const end = start + pageSize
  const codes = US_STOCK_LIST.slice(start, end).join(',')
  
  const response = await axios.get(`${SINA_API}${codes}`)
  
  const list: SinaStockData[] = []
  const lines = response.data.split('\n')
  
  for (const line of lines) {
    if (!line.trim()) continue
    
    const match = line.match(/hq_str_(\w+)="(.+)"/)
    if (match) {
      const code = match[1]
      const fields = match[2].split(',')
      
      if (fields.length >= 31) {
        const name = fields[0]
        const open = parseFloat(fields[1]) || 0
        const close = parseFloat(fields[2]) || 0
        const current = parseFloat(fields[3]) || 0
        const high = parseFloat(fields[4]) || 0
        const low = parseFloat(fields[5]) || 0
        const volume = parseFloat(fields[6]) || 0
        const amount = parseFloat(fields[7]) || 0
        const changePercent = close > 0 ? ((current - close) / close * 100) : 0
        
        list.push({
          code: code.replace('gb_', ''),
          name,
          open,
          close,
          current,
          high,
          low,
          volume,
          amount,
          changePercent,
          changeAmount: current - close,
          turnoverRate: 0,
          marketCap: 0,
          pe: 0
        })
      }
    }
  }
  
  return {
    list,
    total: US_STOCK_LIST.length
  }
}

export async function searchSinaStocks(keyword: string): Promise<SinaStockData[]> {
  const searchList = A_STOCK_LIST.filter(code => {
    const stockCode = code.replace(/^(sh|sz)/, '')
    return stockCode.includes(keyword) || code.includes(keyword)
  }).slice(0, 10)
  
  if (searchList.length === 0) return []
  
  const codes = searchList.join(',')
  const response = await axios.get(`${SINA_API}${codes}`)
  
  const list: SinaStockData[] = []
  const lines = response.data.split('\n')
  
  for (const line of lines) {
    if (!line.trim()) continue
    
    const match = line.match(/hq_str_(\w+)="(.+)"/)
    if (match) {
      const code = match[1]
      const fields = match[2].split(',')
      
      if (fields.length >= 10) {
        const name = fields[0]
        const close = parseFloat(fields[2]) || 0
        const current = parseFloat(fields[3]) || 0
        
        list.push({
          code: code.replace(/^(sh|sz)/, ''),
          name,
          open: parseFloat(fields[1]) || 0,
          close,
          current,
          high: parseFloat(fields[4]) || 0,
          low: parseFloat(fields[5]) || 0,
          volume: parseFloat(fields[8]) || 0,
          amount: parseFloat(fields[9]) || 0,
          changePercent: close > 0 ? ((current - close) / close * 100) : 0,
          changeAmount: current - close,
          turnoverRate: 0,
          marketCap: 0,
          pe: 0
        })
      }
    }
  }
  
  return list
}

// 获取单只股票详情
export async function fetchSingleStock(code: string, market: 'A' | 'US' = 'A'): Promise<SinaStockData | null> {
  try {
    // 根据股票代码判断前缀
    let sinaCode = code
    if (code.startsWith('6')) {
      sinaCode = 'sh' + code
    } else if (code.startsWith('0') || code.startsWith('3')) {
      sinaCode = 'sz' + code
    } else if (market === 'US') {
      sinaCode = 'gb_' + code
    }
    
    const response = await axios.get(`${SINA_API}${sinaCode}`)
    const lines = response.data.split('\n')
    
    for (const line of lines) {
      if (!line.trim()) continue
      
      const match = line.match(/hq_str_\w+="(.+)"/)
      if (match) {
        const fields = match[1].split(',')
        
        if (fields.length >= 32) {
          const name = fields[0]
          const open = parseFloat(fields[1]) || 0
          const close = parseFloat(fields[2]) || 0
          const current = parseFloat(fields[3]) || 0
          const high = parseFloat(fields[4]) || 0
          const low = parseFloat(fields[5]) || 0
          const volume = parseFloat(fields[8]) || 0
          const amount = parseFloat(fields[9]) || 0
          const changePercent = close > 0 ? ((current - close) / close * 100) : 0
          
          return {
            code,
            name,
            open,
            close,
            current,
            high,
            low,
            volume,
            amount,
            changePercent,
            changeAmount: current - close,
            turnoverRate: 0,
            marketCap: 0,
            pe: 0
          }
        }
      }
    }
    return null
  } catch (error) {
    console.error('获取股票详情失败:', error)
    return null
  }
}
