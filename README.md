# 股票模拟交易系统 (Stock Trading Simulator)

## 项目简介

基于 **Vue 3 + Spring Cloud + MySQL** 的全栈股票模拟交易系统，支持 **A股** 和 **美股纳斯达克** 行情展示、技术指标分析（MA/MACD/RSI/KDJ/布林带）、模拟买卖交易、持仓管理、收益分析等功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + ECharts + Pinia |
| 后端 | Spring Cloud 2021.0.8 + Spring Boot 2.7.18 + MyBatis-Plus |
| 数据库 | MySQL 8.x |
| 认证 | JWT |
| JDK | JDK 8 |

## 项目结构

```
stock-trading-system/
├── backend/                    # 后端 Spring Cloud 微服务
│   ├── pom.xml                 # 父POM
│   ├── sql/
│   │   └── init.sql            # 数据库初始化脚本
│   ├── stock-gateway/          # API网关 (端口8080)
│   ├── stock-auth/             # 认证服务 (端口8081)
│   ├── stock-stock/            # 股票行情服务 (端口8082)
│   ├── stock-trade/            # 模拟交易服务 (端口8083)
│   └── stock-analysis/         # 技术分析服务 (端口8084)
└── frontend/                   # 前端 Vue 3 项目
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── views/              # 页面组件
        ├── components/         # 公共组件
        ├── store/              # Pinia 状态管理
        ├── api/                # API 接口
        ├── types/              # TypeScript 类型
        └── utils/              # 工具函数
```

## 环境要求

- **JDK**: 1.8+
- **Maven**: 3.6+
- **Node.js**: 16+
- **MySQL**: 8.0+
- **IDE**: IntelliJ IDEA (后端) + VS Code (前端)

## 快速开始

### 1. 初始化数据库

```bash
# 登录MySQL，执行初始化脚本
mysql -u root -p
source d:\TRAEFile\stock-trading-system\backend\sql\init.sql
```

> 数据库配置：账号 `root`，密码 `Wu15250707866`，数据库名 `stock_trading`

### 2. 启动后端服务

```bash
cd d:\TRAEFile\stock-trading-system\backend

# 方式一：按顺序启动各模块
# 1. 启动认证服务
cd stock-auth
mvn spring-boot:run

# 2. 启动行情服务
cd ../stock-stock
mvn spring-boot:run

# 3. 启动交易服务
cd ../stock-trade
mvn spring-boot:run

# 4. 启动分析服务
cd ../stock-analysis
mvn spring-boot:run

# 5. 启动网关
cd ../stock-gateway
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd d:\TRAEFile\stock-trading-system\frontend
npm install
npm run dev
```

前端访问地址：http://localhost:3000

### 4. 访问系统

1. 打开 http://localhost:3000
2. 注册一个新账号（默认初始资金100万）
3. 登录后即可开始模拟交易

## 核心功能

### 行情中心
- A股实时行情（新浪财经API）
- 美股纳斯达克行情（腾讯财经API）
- K线图展示（日K/周K/月K）
- 股票搜索

### 模拟交易
- 买入/卖出（市价单/限价单）
- 手续费计算（万三费率）
- 持仓管理（实时盈亏计算）
- 撤单功能
- 账户资产概览

### 技术分析
- MA均线（5/10/20/60日）
- MACD（DIF/DEA/柱状图）
- RSI（6/12/24日）
- KDJ（K/D/J）
- 布林带（上轨/中轨/下轨）
- 综合买卖信号
- MA金叉死叉回测

### 收益分析
- 总资产曲线
- 日/周/月/总收益率
- 交易统计（胜率、平均盈亏）
- 月度收益柱状图
- 持仓分布饼图

## API 接口

| 服务 | 端口 | 接口 |
|------|------|------|
| 网关 | 8080 | 统一入口，路由到各微服务 |
| 认证 | 8081 | `POST /api/auth/register` `POST /api/auth/login` `GET /api/auth/info` |
| 行情 | 8082 | `GET /api/stock/search` `GET /api/stock/realtime/{code}` `GET /api/stock/kline/{code}` |
| 交易 | 8083 | `POST /api/trade/buy` `POST /api/trade/sell` `GET /api/trade/positions` `GET /api/trade/account` |
| 分析 | 8084 | `GET /api/analysis/indicators/{code}` `GET /api/analysis/signal/{code}` `GET /api/analysis/backtest/{code}` |

## 页面预览

| 页面 | 路由 | 说明 |
|------|------|------|
| 登录/注册 | `/login` | 深色主题登录页 |
| 仪表盘 | `/dashboard` | 账户概览、持仓排行、最近交易 |
| 行情中心 | `/market` | A股/美股列表、搜索 |
| 股票详情 | `/stock/:code` | K线图、技术指标、买卖信号 |
| 交易 | `/trade` | 买入/卖出面板 |
| 持仓 | `/position` | 持仓列表、盈亏分析 |
| 交易记录 | `/history` | 历史订单、撤单 |
| 收益分析 | `/analysis` | 收益曲线、交易统计 |

## 注意事项

1. **Eureka 服务注册**：当前版本各服务通过网关直接路由，如需使用 Eureka 服务发现，需要额外添加 `stock-registry` (Eureka Server) 模块
2. **行情数据**：A股数据通过新浪API获取，美股通过腾讯API获取，如API不可用请检查网络或更换数据源
3. **初始数据**：SQL脚本已预置8只示例股票（4只A股 + 4只美股）和60天模拟K线数据
4. **手续费**：默认万三费率（0.03%），可在交易服务配置中修改
