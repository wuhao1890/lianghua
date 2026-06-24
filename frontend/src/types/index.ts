// ==================== 用户相关 ====================
export interface User {
  id: number
  username: string
  nickname?: string
  role?: string
  email?: string
  phone?: string
  availableCash?: number
  initialCapital?: number
  createdAt?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  confirmPassword: string
  email: string
}

export interface LoginResponse {
  token: string
  user?: User
  userId: number
  username: string
  nickname?: string
  role?: string
  email?: string
  availableCash?: number
  initialCapital?: number
}

// ==================== 股票相关 ====================
export interface StockInfo {
  code: string
  name: string
  market: 'A' | 'US'
  currentPrice: number
  openPrice: number
  closePrice: number
  highPrice: number
  lowPrice: number
  volume: number
  turnover: number
  changePercent: number
  changeAmount: number
  turnoverRate: number
  pe: number
  pb: number
  marketCap: number
  totalShares: number
  circulateShares: number
}

export interface StockDaily {
  code: string
  date: string
  open: number
  close: number
  high: number
  low: number
  volume: number
  turnover: number
  changePercent: number
}

export interface KlineData {
  dates: string[]
  prices: number[][]
  volumes: number[]
  turnover: number[]
}

// ==================== 交易相关 ====================
export type OrderDirection = 'BUY' | 'SELL'
export type OrderType = 'MARKET' | 'LIMIT'
export type OrderStatus = 'PENDING' | 'FILLED' | 'CANCELLED' | 'REJECTED'

export interface TradeOrder {
  id: number
  userId: number
  stockCode: string
  stockName: string
  direction: OrderDirection
  orderType: OrderType
  price: number
  quantity: number
  amount: number
  fee: number
  status: OrderStatus
  createdAt: string
  updatedAt: string
}

export interface Position {
  id: number
  userId: number
  stockCode: string
  stockName: string
  assetType?: 'stock' | 'fund' | 'gold'
  market: 'A' | 'US'
  quantity: number
  availableQuantity: number
  costPrice: number
  currentPrice: number
  marketValue: number
  profit: number
  profitPercent: number
  todayProfit: number
  todayProfitPercent: number
}

export interface TradeLog {
  id: number
  orderId: number
  userId: number
  stockCode: string
  stockName: string
  direction: OrderDirection
  price: number
  quantity: number
  amount: number
  fee: number
  status: OrderStatus
  createdAt: string
}

// ==================== 请求/响应 ====================
export interface BuyRequest {
  stockCode: string
  stockName?: string
  market?: string
  direction: 'BUY'
  orderType: OrderType
  price: number
  quantity: number
}

export interface SellRequest {
  stockCode: string
  stockName?: string
  market?: string
  direction: 'SELL'
  orderType: OrderType
  price: number
  quantity: number
}

// ==================== 账户与分析 ====================
export interface AccountOverview {
  totalAssets: number
  availableCash: number
  marketValue: number
  totalProfit: number
  totalProfitPercent: number
  todayProfit: number
  todayProfitPercent: number
  positionCount: number
}

export interface ProfitAnalysis {
  totalTradeCount: number
  winCount: number
  loseCount: number
  winRate: number
  totalProfit: number
  totalLoss: number
  avgProfit: number
  avgLoss: number
  profitLossRatio: number
  maxDrawdown: number
  sharpeRatio: number
}

export interface ProfitRecord {
  date: string
  totalAssets: number
  profit: number
  profitPercent: number
}

// ==================== 技术指标 ====================
export interface TechnicalIndicator {
  name: string
  values: number[][]
  signals: TradeSignal[]
}

export interface TradeSignal {
  date: string
  type: 'BUY' | 'SELL' | 'HOLD'
  strength: number
  indicator: string
  message: string
}

export interface BacktestResult {
  startDate: string
  endDate: string
  initialCapital: number
  finalCapital: number
  totalReturn: number
  annualizedReturn: number
  maxDrawdown: number
  sharpeRatio: number
  winRate: number
  totalTrades: number
  trades: TradeLog[]
}

// ==================== AI Agent ====================
export interface AiAnalysisResponse {
  stockCode: string
  stockName: string
  signal: 'BUY' | 'SELL' | 'HOLD'
  score: number
  techScore: number
  sentimentScore: number
  targetPrice: string
  analysis: string
  modelUsed: string
  quantDecision?: QuantDecision
  factors?: QuantFactor[]
  scenarios?: QuantScenario[]
  risks?: string[]
  actions?: string[]
  modelAvailable?: boolean
  failureReason?: string
  daVOpinions: DaVOpinion[]
  daVMajority: DaVMajorityConsensus
  newsItems?: NewsItem[]
  moneyFlow?: MoneyFlowAnalysis
  candidateStrategies?: CandidateStrategy[]
  selectedStrategy?: CandidateStrategy
  evolution?: StrategyEvolution
}

export interface MoneyFlowAnalysis {
  bigOrderBuyAmount: number
  bigOrderSellAmount: number
  netBigOrderAmount: number
  bigOrderDirection: string
  expectedVolume: number
  expectedVolumeChangePercent: number
  basis: string
}

export interface QuantDecision {
  signal: 'BUY' | 'SELL' | 'HOLD'
  confidence: number
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  trendState: string
  suggestedPosition: string
  stopLoss: number
  takeProfit: number
  targetRange: string
  summary: string
}

export interface QuantFactor {
  name: string
  score: number
  direction: string
  weight: number
  reason: string
}

export interface QuantScenario {
  name: string
  probability: number
  trigger: string
  action: string
}

export interface DaVOpinion {
  name: string
  type: 'bullish' | 'bearish' | 'neutral'
  view: string
  detail: string
  influence: number
  publishTime: string
}

export interface DaVMajorityConsensus {
  consensus: 'bullish' | 'bearish' | 'neutral'
  summary: string
  bullishCount: number
  bearishCount: number
  neutralCount: number
}

export interface NewsItem {
  title: string
  source: string
  url: string
  publishTime: string
  sentiment: 'bullish' | 'bearish' | 'neutral'
  impactScore: number
  reason: string
}

export interface CandidateStrategy {
  name: string
  style: string
  signal: 'BUY' | 'SELL' | 'HOLD'
  score: number
  expectedReturnScore: number
  riskScore: number
  sentimentFitScore: number
  suggestedPosition: string
  entryRule: string
  exitRule: string
  stopLossRule: string
  takeProfitRule: string
  evaluationRule: string
  rationale: string
}

export interface StrategyEvolution {
  generation: number
  status: string
  lastLearning: string
  nextMutation: string
  outcomeJudgement: string
  historySamples: number
}

// ==================== 通用 ====================
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

export interface PageParams {
  page: number
  pageSize: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}
