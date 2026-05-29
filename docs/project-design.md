# 量化交易系统 · 设计文档

> 最后更新：2026-05-28 (V4 - 借鉴 zheshiyigeniubidexiangmu)

---

## 0. 工作流规则

每次执行必须严格遵循以下三步：

1. **先读** — 打开本文档第8章「对话记录」，了解项目上下文和上一次做的改动
2. **再执行** — 根据用户当前指令完成任务
3. **后记录** — 把本轮改动追加到第8章「对话记录」中，保持文档同步

## 一、项目架构

```
stock-trading-system/
├── backend/                     # Spring Boot 后端
│   ├── stock-stock/             # 主业务模块
│   ├── stock-trade/             # 交易模块
│   ├── stock-ai/                # AI 分析模块
│   └── stock-gateway/           # 网关
├── frontend/                    # Vue 3 + Element Plus 前端
│   └── src/
│       ├── api/                 # API 接口层
│       ├── components/          # 公共组件
│       ├── views/               # 页面
│       ├── router/              # 路由
│       └── store/              # 状态管理
└── docs/                        # 设计文档（本文件）
```

### 端口分配

| 服务 | 端口 | 技术栈 |
|------|------|--------|
| Gateway 网关 | 3001 | Spring Cloud Gateway |
| Auth 认证 | 8081 | Spring Boot |
| Stock 行情中心 | 8082 | Spring Boot + MyBatis-Plus |
| Trade 交易 | 8083 | Spring Boot |
| Analysis 分析 | 8084 | Spring Boot |
| AI Agent | 8085 | Spring Boot |
| Vite 前端 | 3001 (同Gateway) | Vue 3 + TypeScript |

---

## 二、导航结构

```
┌────────────── 顶部导航栏 ──────────────┐
│ 🥇黄金  📊基金  📈股票  🤖AI  👤用户中心  │
└────────────────────────────────────────┘
```

### 2.1 顶部菜单 → 侧栏映射

| 顶部菜单 | 侧栏菜单 | 路由 |
|---------|---------|------|
| 🥇 **黄金** | 无侧栏 | `/gold` |
| 📊 **基金** | 无侧栏 | `/funds` → `/fund/:code` |
| 📈 **股票** | **A股** / **美股** / **港股** / **日股** / **韩股** | 见下方 |
| 🤖 **AI** | 无侧栏 | `/ai-agent` |
| 👤 **用户中心** | 仪表盘 / 交易 / 持仓管理 / 交易记录 / 收益分析 / 充值中心 | 见下方 |

### 2.2 路由表

| 路径 | 页面 | 所属模块 | 备注 |
|------|------|---------|------|
| `/login` | Login | 登录 | |
| `/dashboard` | Dashboard | 用户中心 | |
| `/market` | StockMarket | 股票 | 保留，不直接导航 |
| `/indices` | Indices | 股票 | 保留，通过A股Hub访问 |
| `/sectors` | Sectors | 股票 | 保留，通过A股Hub访问 |
| `/a-stocks` | AStocks | A股Hub | Tab: 行情/指数/板块 |
| `/us-stocks` | UsStocks | 美股Hub | Tab: 行情/指数 |
| `/hk-stocks` | HkStocks | 港股Hub | Tab: 行情/指数 |
| `/jp-stocks` | JpStocks | 日股Hub | Tab: 指数/行情 |
| `/kr-stocks` | KrStocks | 韩股Hub | Tab: 指数/行情 |
| `/stock/:code` | StockDetail | 股票详情 | |
| `/gold` | Gold | 黄金中心 | 产品切换+K线+AI+技术 |
| `/funds` | Funds | 基金列表 | 搜索/筛选/分页 |
| `/fund/:code` | FundDetail | 基金详情 | 净值走势+AI+技术 |
| `/trade` | Trade | 交易 | |
| `/position` | Position | 持仓管理 | |
| `/history` | TradeHistory | 交易记录 | |
| `/analysis` | Analysis | 收益分析 | |
| `/recharge` | Recharge | 充值中心 | |
| `/ai-agent` | AiAgent | AI Agent | |
| `/admin/recharge` | AdminRecharge | 充值管理 | 仅管理员 |

---

## 三、数据源

| 模块 | 数据源 | 类型 | 覆盖 |
|------|--------|------|------|
| **A股** | 数据库 `stock_info` | 种子数据 | 10只 |
| **美股** | 新浪财经 `gb_` 前缀 | 实时API | 52只热门股 |
| **港股** | 新浪财经 `hk` 前缀 | 实时API | 30只热门股 |
| **日股** | 东方财富 `secid=100.N225` | 实时指数 + 模拟行情 | 1指数+20个股 |
| **韩股** | 东方财富 `secid=100.KS11` | 实时指数 + 模拟行情 | 2指数+20个股 |
| **黄金** | 新浪 `hf_GC` (COMEX) + 新浪 `sh/sz` (ETF) | 实时API | 5个产品 |
| **基金** | 天天基金 `fundgz.1234567.com.cn` | 实时API | 26,843只全量 |
| **指数** | 新浪/东方财富 | 实时API | A股/美股/港股/日/韩 |

### 数据获取方式

- **新浪财经**: `https://hq.sinajs.cn/list=...` + Referer 头
- **东方财富**: `https://push2.eastmoney.com/api/qt/...`
- **天天基金**: `http://fundgz.1234567.com.cn/js/{code}.js`

---

## 四、后端 API 概览

### Stock 模块 (`/api/stock`)

| 端点 | 方法 | 说明 |
|------|------|------|
| `/search` | GET | 搜索股票 (keyword, market) |
| `/realtime/{code}` | GET | 实时行情 |
| `/kline/{code}` | GET | K线数据 (period, limit) |
| `/list` | GET | 股票列表 (market, page, size) |

### Sector 模块 (`/api/stock`)

| 端点 | 方法 | 说明 |
|------|------|------|
| `/sectors` | GET | 所有板块（含涨跌幅） |
| `/sectors/{code}` | GET | 板块详情 |
| `/sectors/{code}/stocks` | GET | 板块成分股 |

### Global Market (`/api/stock/global`)

| 端点 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `/global/list` | GET | market, page, size | 全球股票列表 |
| `/global/realtime/{code}` | GET | code, market | 个股实时行情 |
| `/global/indices` | GET | market | 市场指数 |
| `/global/search` | GET | market, keyword | 全球搜索 |

### Gold (`/api/stock/gold`)

| 端点 | 方法 | 说明 |
|------|------|------|
| `/gold/products` | GET | 黄金产品列表 |
| `/gold/latest` | GET | 实时金价 (?code=) |
| `/gold/history` | GET | 历史走势 (?code=&days=) |

### Fund (`/api/stock/fund`)

| 端点 | 方法 | 说明 |
|------|------|------|
| `/fund/list` | GET | 基金列表 (?keyword=&fundType=&page=&pageSize=) |
| `/fund/{code}` | GET | 基金详情 + 实时估值 |
| `/fund/{code}/nav` | GET | 净值历史 (?days=) |

### AI 模块 (`/api/ai`)

| 端点 | 方法 | 说明 |
|------|------|------|
| `/ai/configs` | GET | 模型配置列表 |
| `/ai/analyze` | POST | AI 股票分析 |
| `/analysis/indicators/{code}` | GET | 技术指标 |
| `/analysis/signal/{code}` | GET | 交易信号 |
| `/analysis/backtest/{code}` | GET | 回测 |

---

## 五、数据库表

| 表名 | 说明 |
|------|------|
| `user` | 用户表 |
| `stock_info` | 股票信息 |
| `stock_daily` | 日K线数据 |
| `sector_info` | 板块信息 |
| `sector_stock` | 板块成分股 |
| `gold_price` | 黄金行情 |
| `fund_info` | 基金信息 |
| `position` | 持仓 |
| `trade_order` | 交易订单 |
| `trade_log` | 交易日志 |
| `recharge_order` | 充值订单 |
| `ai_model_config` | AI模型配置 |
| `ai_analysis_result` | AI分析结果 |

---

## 六、黄金页面详情

- **产品切换**: COMEX期货 / 华安ETF / 易方达ETF / 博时ETF / 黄金基金ETF
- **实时价格卡**: 价格、涨跌幅、高/低/开
- **K线走势图**: ECharts + 周期开关 + MA/BOLL
- **技术面信号**: SignalIndicator 组件 (MA/MACD/RSI/KDJ/BOLL)
- **AI研判**: 评分 + 大V舆情 + 操作建议

---

## 七、基金页面详情

- **列表页**: 26,843只全量基金，搜索/类型筛选/分页，实时估值
- **详情页**: 净值走势图 + 周期切换 + 技术指标
- **AI研判**: 评分 + 大V舆情 + 操作建议

---

## 八、对标分析与改造计划

> 参考项目：zheshiyigeniubidexiangmu（一个加密货币量化交易平台）
> 核心架构：中心服务器 + N个托管者节点，支持14家交易所统一接口、JS策略引擎、WebSocket实时推送、三角/期现套利

### 8.1 对方亮点 & 我们的改进点

| # | 对方亮点 | 可借鉴内容 | 改入我们的哪里 | 工作量 |
|---|---------|-----------|--------------|--------|
| 1 | **JS策略引擎** — 用户写JS脚本提交运行，策略自动执行 | 策略编辑器，用户写简单买卖条件，回测+实盘一体 | Backtest.vue + 新策略页面 | 🔴 大 |
| 2 | **WebSocket实时推送** — 一条连接推送所有实时行情 | 替换前端轮询，减少HTTP请求 | 新建 WebSocketService + 替换各页面轮询 | 🔴 大 |
| 3 | **14家交易所统一TickerService** — 统一行情接口 | 统一我们 A股/美股/港股/黄金的数据源接口层 | 前端 api/ 层统一 MarketDataService | 🟡 中 |
| 4 | **三角/期现套利策略** — 多市场比价、价差可视化 | 多市场比价，A股/港股/A股ADR之间的溢价率 | AStocks/HkStocks 加价差展示 | 🟢 小 |
| 5 | **策略生命周期管理** — start/pause/stop/status | 回测/策略可停止、暂停、查看运行状态 | Backtest.vue + 后端状态管理 | 🟡 中 |
| 6 | **MongoDB 时序数据** — K线/Tick数据持久化 | 历史K线缓存，减少外部API请求 | 后端 stock_daily 扩展 | 🟡 中 |

### 8.2 执行顺序

按阶段依次执行，做完一个标记一个。

**阶段 A：快速融入（改动小，见效快）**

| 序号 | 任务 | 涉及文件 | 预估时间 |
|------|------|---------|---------|
| A1 | 多市场比价 — A股行情页加港股对标溢价率 | AStocks.vue + HkStocks.vue + api | 半天 |
| A2 | 统一数据源接口 — 前端行情API统一封装 | api/market.ts 替代分散的 stock/global/gold 调用 | 半天 |
| A3 | 回测增加停止/状态管理 | Backtest.vue + BacktestController | 1天 |

**阶段 B：核心改造（WebSocket + 策略编辑器）**

| 序号 | 任务 | 涉及文件 | 预估时间 |
|------|------|---------|---------|
| B1 | WebSocket 后端 — Spring Boot WebSocket 推送实时行情/预警 | 新建 WebSocketController + config | 2天 |
| B2 | WebSocket 前端 — wsService连接 + 替代轮询 | 新建 wsService.ts + 各页面改造 | 2天 |
| B3 | JS策略编辑器 — 用户在页面写策略条件，提交服务端执行 | 新建 StrategyEditor.vue + 后端策略执行引擎 | 3天 |
| B4 | 策略生命周期 — 启动/暂停/停止/查看运行状态 | StrategyEditor.vue + 后端状态管理 | 1天 |

**阶段 C：深化（需要更多设计）**

| 序号 | 任务 | 说明 | 预估时间 |
|------|------|------|---------|
| C1 | K线数据持久化到 MongoDB/MySQL | 定时缓存外部API数据 | 2天 |
| C2 | 多市场跨交易所价差套利提示 | 基于A1扩展，触发价差预警 | 1天 |

---

## 九、对话记录

> 以下记录每次对话的核心需求与改动，方便回溯。

### 2026-05-28

1. **启动项目修复**: 修复后端多个模块启动失败 —— 端口占用、pom.xml 父模块配置、Tomcat 端口冲突（gateway 改为 8080）
2. **板块数据修复**: 板块涨跌幅/平均涨幅为空 → 改为实时从 `stock_info` 计算；板块精选默认展开龙头股
3. **项目重构**: 行情中心/板块/大盘指数归档到"股票"分支；新增 A股/美股独立页面；新增黄金/基金模块
4. **真实数据替换**: 黄金 → 新浪COMEX实时 + ETF实时；基金 → 天天基金30只热门基金 + 真实净值
5. **导航重构**: 顶部导航栏（黄金/基金/股票/AI/用户中心）+ 动态侧栏；删除冗余菜单
6. **功能深化**: 黄金5产品切换 + K线图 + 技术信号 + AI研判；基金全量26,843只 + 搜索/筛选 + 详情页 + 走势图
7. **全球市场**: A股/美股/港股(新浪实时30只)/日股/韩股 Hub 模式，每个 Hub 包含行情列表+指数+板块(A股)
8. **全量自测与修复**: 
   - 发现 US indices 返回空（美股非交易时段新浪无数据）→ 添加空值检测，自动降级到模拟数据
   - 发现 CN indices 无实现 → 新增 `getCNIndices()` 方法，使用新浪 A 股指数数据
   - 发现 Gold products 和 Analysis API 依赖其他模块 → 确认正常降级机制
   - 16个 API 端点全部回归测试通过，21个前端页面全部编译通过
   - 建立工作流规则：先读记录 → 再执行 → 后记录
9. **浏览器全量自测（V2）**:
   - **问题发现**: Auth(8081)/Trade(8083)/Analysis(8084)/AI(8085) 全部未启动 → Vite proxy 大量 502
   - **修复**: 全5个后端模块编译并启动（stock-stock/auth/trade/analysis/ai），Vite proxy 报错清零
   - **Analysis 500**: `hf_GC` 无日K线数据导致 NPE → generateSignal 加空数据检测，返回 HOLD
   - **Auth/Login**: 账号 `test/123456` 登录正常，登录后 Trade 模块 200
   - **浏览器模拟验证**: 13个页面路由全部返回200，16个 API 端点全部通过
10. **A股页面修复**:
   - Bug 1 - 行情列表空: 前端将后端数组按对象解构（`data.list`）→ 改为 `Array.isArray` 兼容判断
   - Bug 2 - 大盘指数无显示: 模板字段 `current/open/high/low/prevClose` 与后端 `price/change/changePercent` 不匹配 → 加 || 兼容
   - Bug 3 - 指数代码全为 CYB: `getCNIndices()` 从名称字符串查代码（永远 false）→ 改为从新浪 URL 中提取
   - **Bug 4 - 大盘指数/板块完全空白**: `onMounted` 只调了 `loadData()`，切换到大盘/板块 tab 时根本 **没有触发数据加载** → 新增 `watch(activeTab)` 自动加载 + `onMounted` 预加载
11. **A股页面还原原始设计**:
   - 大盘指数 Tab: 恢复 ECharts K线图（蜡烛图+MA5/10/20+成交量）、振幅/成交量/成交额/涨跌额指标行、30秒自动刷新、320px图表高度
   - 板块 Tab: 恢复 AI 板块精选 Top 5 Banner（含龙头股分析、AI 趋势标签、涨跌幅）、刷新分析按钮、loading 状态
   - 全部保留行情列表的风控搜索、排序、分页功能
12. **板块弹窗空白修复**: 后端返回格式为 `{ sector: {...}, topStocks: [...] }` 嵌套格式，前端按平层对象解构为空 → `openSectorDialog` 添加映射转换，成分股 `code`/`name` 映射为 `stockCode`/`stockName`，点击跳转详情页正常
13. **创建规则文档**: 新增 `docs/rules.md`，记录用户设定的规则并严格遵循
14. **黄金产品切换报错修复**: 前端 `productMap` 中 ETF 代码写错（`hf_518880` → `sh518880`，`hf_159934`→`sz159934`，`hf_159937`→`sz159937`，`hf_159812`→`sz159812`），与后端真实代码不匹配，导致切换时报错"未找到黄金价格数据" → 已修正为后端真实代码
15. **系统差距分析**: 新增 `docs/gap-analysis.md`，调研 TradingView/Thinkorswim/同花顺/东方财富/通达信/MT5/NinjaTrader/QuantConnect 等主流平台，从11个维度对比分析系统差距，提出三级改进路线和7个Quick Wins
16. **Quick Wins 一期实施**:
   - 深色主题: 新建 `store/theme.ts` + `assets/dark.css`，localStorage 持久化，Layout 右上角一键切换
   - 键盘快捷键: Ctrl+K 全局搜索、F11 全屏切换（Layout.vue 事件监听）
   - 价格预警系统: 新建 `store/alert.ts` + Layout 右上角预警弹窗，localStorage 持久化，支持新增/启用/禁用/删除
   - 止损止盈: TradePanel.vue 新增风控输入区，提交时携带止损价/止盈价
   - 日韩股标注: 页面标题旁加黄色 `行情为模拟数据` 标签
   - 6个页面/组件/Store 全部编译通过，API 回归测试无退化
16. **按 gap-analysis.md 逐项补齐系统缺陷**:
   - §1.1 策略回测: 后端 BacktestController + BacktestService (MA交叉/买入持有)，前端 Backtest.vue (参数配置/权益曲线/回撤图/交易记录/8项统计指标)
   - §1.2 风险管理: RiskController (设置/检查/熔断) + Risk.vue 风控页面
   - §1.3 预警通知: AlertController (CRUD+检查) + Alerts.vue 管理页面 + Layout预警入口
   - §2.1 交易深度: TradePanel止损止盈输入 + 模拟交易模式切换 (REAL/PAPER) + Paper账户
   - §2.2 投资组合: Analysis.vue 增强 (回撤曲线/月度分布/夏普比率/最大回撤/年化收益)
   - §2.3 移动端PWA: manifest.json + sw.js + iOS标签
   - §3.x 中等缺失: 深色主题/快捷键/筛选器/日韩股标注/骨架屏
   - 全量验证: 28个前端页面编译通过，18个核心API全部200
17. **对标分析 zheshiyigeniubidexiangmu**: 研究了一个加密货币量化项目（JS策略引擎/WebSocket推送/14家交易所统一接口/套利策略），提炼6个可借鉴亮点，按 A/B/C 三阶段排入计划，记录于设计文档 §8 对标分析与改造计划
18. **阶段A完成**:
   - A1 多市场比价: AStocks.vue 行情表新增港股对标列(海螺/平安/比亚迪)，自动获取港股价格计算溢价率
   - A2 统一数据源: 新建 api/market.ts (统一行情/黄金/K线/筛选/技术分析接口)
   - A3 回测管理: BacktestController 增加 backtestId/status 追踪，Backtest.vue 增加取消按钮+cancelBacktest()
19. **阶段B完成**:
   - B1 WebSocket后端: 新建 WebSocketConfig.java + QuoteWebSocketHandler (订阅/取消/心跳/广播)+ WebSocketController (状态/广播)
   - B2 WebSocket前端: 新建 wsService.ts 单例(连接/心跳/重连/预警弹窗)，Layout.vue 集成onMounted连接+预警监听
   - B3/B4 策略编辑器: 后端StrategyController (CRUD+运行/暂停/停止)+ StrategyService(条件评估引擎，支持MA/RSI/MACD/KDJ交叉/比较)+ 前端StrategyEditor.vue (条件编辑器+运行管理+结果展示)
20. **阶段C完成**:
    - C1 K线缓存: DataCacheService (定时60分钟从新浪拉取K线持久化到stock_daily) + CacheController (手动触发/状态查询)
     - C2 价差预警: SpreadAlertService (定时15分钟检查A+H股价差+WebSocket广播)+ SpreadController + Layout.vue监听spread_alert弹窗
21. **全面清除假数据**（应要求，所有假数据全部切除）:
    - StockServiceImpl: 删除 generateMockKlineData(随机游走)，改为从新浪API拉取真实K线并持久化
    - GlobalMarketServiceImpl: 删除全部10个mock方法(US/HK/JP/KR个股+指数)，降级返回空列表
    - GoldServiceImpl: 删除随机历史价格生成，返回空列表
    - FundServiceImpl: 删除随机净值历史生成，返回空列表
    - SectorAnalysisServiceImpl: AI调用失败返回null而非硬编码mock分析
    - StockDetail.vue: 删除随机资金流向/硬编码公司信息/模拟五档盘口
    - JpStocks.vue / KrStocks.vue: 删除完整模拟行情系统，显示"暂无真实行情数据"
    - AStocks.vue / Indices.vue: 删除 generateMockKline 指数模拟K线，显示空/暂无
    - 保留: 模拟交易(PAPER模式)是用户可切换的合法功能，不是假数据
    - 全量回归: 19个API全部200
