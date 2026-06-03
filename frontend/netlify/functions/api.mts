import { getDeployStore, getStore } from "@netlify/blobs";
import iconv from "iconv-lite";

const A_STOCKS = [
  "sh600519", "sh600036", "sh601318", "sh600276", "sh601888", "sh600887", "sh600009", "sh601166",
  "sh601328", "sh601398", "sh600030", "sh600016", "sh601288", "sh601628", "sh600028", "sh601857",
  "sh600050", "sh600031", "sh600585", "sh601088", "sh600048", "sh601012", "sh600900", "sh600104",
  "sh601311", "sh600690", "sh601658", "sh601899", "sh600588", "sh600837", "sh601319", "sh601601",
  "sh601336", "sh601818", "sh600000", "sh600015", "sh600018", "sh600023", "sh600027", "sh600029",
  "sh600109", "sh600111", "sh600115", "sh600150", "sh600170", "sh600176", "sh600183", "sh600196",
  "sh600208", "sh600233", "sh600309", "sh600332", "sh600345", "sh600398", "sh600406", "sh600436",
  "sh600438", "sh600460", "sh600470", "sh600482", "sh600487", "sh600498", "sh600521", "sh600547",
  "sh600570", "sh600660", "sh600703", "sh600745", "sh600760", "sh600809", "sh600862", "sh600893",
  "sh600905", "sh600918", "sh600926", "sh600941", "sh600989", "sh600999", "sz000001", "sz000002",
  "sz000063", "sz000333", "sz000651", "sz000858", "sz002230", "sz002594", "sz300059", "sz300750"
];

const US_STOCKS = ["gb_aapl", "gb_msft", "gb_googl", "gb_amzn", "gb_nvda", "gb_tsla", "gb_meta"];
const INDICES = ["sh000001", "sz399001", "sz399006", "sh000300"];

type AnyRecord = Record<string, any>;

function store() {
  return Netlify.context?.deploy?.context === "production"
    ? getStore("lianghua-state", { consistency: "strong" })
    : getDeployStore("lianghua-state");
}

function send(data: any = null, message = "success", code = 200) {
  return Response.json({ code, message, data });
}

function textDecode(buffer: ArrayBuffer) {
  return iconv.decode(Buffer.from(buffer), "gb18030");
}

function b64url(value: AnyRecord) {
  return btoa(JSON.stringify(value)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function makeToken(user: AnyRecord) {
  return `${b64url({ alg: "none", typ: "JWT" })}.${b64url({
    userId: user.id,
    username: user.username,
    role: user.role,
    exp: Math.floor(Date.now() / 1000) + 86400 * 30
  })}.public`;
}

function getUserId(req: Request) {
  const direct = req.headers.get("x-user-id");
  if (direct) return Number(direct);
  const auth = req.headers.get("authorization") || "";
  const token = auth.replace(/^Bearer\s+/i, "");
  const payload = token.split(".")[1];
  if (!payload) return 1;
  try {
    return Number(JSON.parse(atob(payload.replaceAll("-", "+").replaceAll("_", "/"))).userId || 1);
  } catch {
    return 1;
  }
}

async function body(req: Request) {
  try {
    return await req.json();
  } catch {
    return {};
  }
}

async function getUserByName(username: string) {
  return await store().get(`user:${username}`, { type: "json" });
}

async function saveUser(user: AnyRecord) {
  await store().setJSON(`user:${user.username}`, user);
  await store().setJSON(`user-id:${user.id}`, user);
}

async function ensureUser(username = "admin", password = "123456") {
  const existing = await getUserByName(username);
  if (existing) return existing;
  const user = {
    id: Date.now(),
    username,
    password,
    nickname: username === "admin" ? "管理员" : username,
    role: username === "admin" ? "ADMIN" : "USER",
    availableCash: 1000000,
    initialCapital: 1000000,
    createdAt: new Date().toISOString()
  };
  await saveUser(user);
  await store().setJSON(`positions:${user.id}`, []);
  await store().setJSON(`orders:${user.id}`, []);
  return user;
}

function sinaCode(code: string) {
  if (/^(sh|sz|gb_)/i.test(code)) return code.toLowerCase();
  if (/^6/.test(code)) return `sh${code}`;
  if (/^[03]/.test(code)) return `sz${code}`;
  return `sh${code}`;
}

async function fetchSina(codes: string[]) {
  const url = `https://hq.sinajs.cn/list=${codes.join(",")}`;
  const res = await fetch(url, {
    headers: {
      Referer: "https://finance.sina.com.cn",
      "User-Agent": "Mozilla/5.0"
    }
  });
  const text = textDecode(await res.arrayBuffer());
  return text.split("\n").map((line) => {
    const match = line.match(/hq_str_(\w+)="(.*)"/);
    if (!match) return null;
    const rawCode = match[1];
    const f = match[2].split(",");
    if (!f[0] || f.length < 10) return null;
    const isIndex = /^sh000|^sz399/.test(rawCode);
    const current = Number(f[3]) || 0;
    const prevClose = Number(f[2]) || 0;
    const change = current - prevClose;
    return {
      code: rawCode.replace(/^(sh|sz|gb_)/, ""),
      name: f[0],
      current,
      open: Number(f[1]) || 0,
      prevClose,
      high: Number(f[4]) || 0,
      low: Number(f[5]) || 0,
      volume: Number(f[8]) || Number(f[6]) || 0,
      amount: Number(f[9]) || Number(f[7]) || 0,
      change,
      changePercent: prevClose ? Number(((change / prevClose) * 100).toFixed(2)) : 0,
      market: rawCode.startsWith("gb_") ? "US" : "A",
      isIndex
    };
  }).filter(Boolean);
}

function toStockInfo(s: AnyRecord) {
  return {
    code: s.code,
    name: s.name,
    market: s.market || "A",
    currentPrice: s.current,
    openPrice: s.open,
    closePrice: s.prevClose,
    highPrice: s.high,
    lowPrice: s.low,
    volume: s.volume,
    turnover: s.amount,
    changePercent: s.changePercent,
    changeAmount: s.change,
    turnoverRate: 0,
    pe: 0,
    pb: 0,
    marketCap: 0,
    totalShares: 0,
    circulateShares: 0
  };
}

async function fetchKline(code: string, limit = 120) {
  const secid = code.startsWith("6") ? `1.${code}` : `0.${code}`;
  const url = `https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=${secid}&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61&klt=101&fqt=1&end=20500101&lmt=${limit}`;
  const res = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0" } });
  const json = await res.json();
  const rows = json?.data?.klines || [];
  return {
    dates: rows.map((row: string) => row.split(",")[0]),
    prices: rows.map((row: string) => {
      const f = row.split(",");
      return [Number(f[1]), Number(f[2]), Number(f[3]), Number(f[4])];
    }),
    volumes: rows.map((row: string) => Number(row.split(",")[5]) || 0),
    turnover: rows.map((row: string) => Number(row.split(",")[6]) || 0)
  };
}

function signalFor(stock: AnyRecord) {
  const type = stock.changePercent > 1 ? "BUY" : stock.changePercent < -1 ? "SELL" : "HOLD";
  return {
    date: new Date().toISOString().slice(0, 10),
    type,
    strength: Math.min(95, Math.max(45, Math.round(Math.abs(stock.changePercent) * 12 + 55))),
    indicator: "REALTIME_TECH",
    message: `基于真实实时行情：${stock.name} 当前涨跌幅 ${stock.changePercent}%，综合判断为 ${type}`
  };
}

async function account(userId: number) {
  const user = await store().get(`user-id:${userId}`, { type: "json" }) || await ensureUser();
  const positions = await store().get(`positions:${user.id}`, { type: "json" }) || [];
  const marketValue = positions.reduce((sum: number, p: AnyRecord) => sum + (p.marketValue || 0), 0);
  return {
    totalAssets: Number(user.availableCash || 0) + marketValue,
    availableCash: Number(user.availableCash || 0),
    marketValue,
    totalProfit: 0,
    totalProfitPercent: 0,
    todayProfit: 0,
    todayProfitPercent: 0,
    positionCount: positions.length
  };
}

async function route(req: Request) {
  const url = new URL(req.url);
  const path = url.pathname.replace(/^\/api/, "") || "/";
  const method = req.method.toUpperCase();

  if (method === "POST" && path === "/auth/login") {
    const data = await body(req);
    if (!data.username || !data.password) return send(null, "请输入用户名和密码", 400);
    const user = await ensureUser(data.username, data.password);
    if (user.password !== data.password) return send(null, "密码错误", 401);
    return send({ token: makeToken(user), userId: user.id, username: user.username, nickname: user.nickname, role: user.role, availableCash: user.availableCash, initialCapital: user.initialCapital });
  }

  if (method === "POST" && path === "/auth/register") {
    const data = await body(req);
    if (!data.username || !data.password) return send(null, "请输入用户名和密码", 400);
    if (await getUserByName(data.username)) return send(null, "用户已存在", 409);
    await ensureUser(data.username, data.password);
    return send(null, "注册成功");
  }

  if (method === "GET" && path === "/auth/info") {
    const user = await store().get(`user-id:${getUserId(req)}`, { type: "json" }) || await ensureUser();
    return send({ id: user.id, username: user.username, nickname: user.nickname, role: user.role, availableCash: user.availableCash, initialCapital: user.initialCapital });
  }

  if (method === "GET" && path === "/stock/sina/a-stocks") {
    const page = Number(url.searchParams.get("page") || 1);
    const pageSize = Number(url.searchParams.get("pageSize") || 20);
    const codes = A_STOCKS.slice((page - 1) * pageSize, page * pageSize);
    return Response.json({ code: 200, message: "success", data: await fetchSina(codes), total: A_STOCKS.length });
  }

  if (method === "GET" && path === "/stock/sina/us-stocks") {
    const page = Number(url.searchParams.get("page") || 1);
    const pageSize = Number(url.searchParams.get("pageSize") || 20);
    return Response.json({ code: 200, message: "success", data: await fetchSina(US_STOCKS.slice((page - 1) * pageSize, page * pageSize)), total: US_STOCKS.length });
  }

  if (method === "GET" && path === "/stock/sina/indices") {
    return send(await fetchSina(INDICES));
  }

  if (method === "GET" && path === "/stock/sina/realtime") {
    const codes = (url.searchParams.get("codes") || "").split(",").filter(Boolean).map(sinaCode);
    return send(codes.length ? await fetchSina(codes) : []);
  }

  const realtime = path.match(/^\/stock\/realtime\/([^/]+)$/);
  if (method === "GET" && realtime) {
    const list = await fetchSina([sinaCode(realtime[1])]);
    if (!list.length) return send(null, "未找到该股票", 404);
    return send(toStockInfo(list[0] as AnyRecord));
  }

  if (method === "GET" && path === "/stock/list") {
    const page = Number(url.searchParams.get("page") || 1);
    const pageSize = Number(url.searchParams.get("pageSize") || 20);
    const list = (await fetchSina(A_STOCKS.slice((page - 1) * pageSize, page * pageSize))).map((s) => toStockInfo(s as AnyRecord));
    return send({ list, total: A_STOCKS.length, page, pageSize });
  }

  const kline = path.match(/^\/stock\/kline\/([^/]+)$/);
  if (method === "GET" && kline) return send(await fetchKline(kline[1]));

  const indicator = path.match(/^\/analysis\/(signal|indicators)\/([^/]+)$/);
  if (method === "GET" && indicator) {
    const list = await fetchSina([sinaCode(indicator[2])]);
    const signal = list.length ? signalFor(list[0] as AnyRecord) : { date: new Date().toISOString().slice(0, 10), type: "HOLD", strength: 50, indicator: "REALTIME_TECH", message: "未获取到真实行情" };
    return indicator[1] === "signal" ? send(signal) : send([{ name: "REALTIME_TECH", values: [], signals: [signal] }]);
  }

  if (method === "GET" && path.startsWith("/analysis/backtest/")) {
    return send({ startDate: "", endDate: "", initialCapital: 100000, finalCapital: 100000, totalReturn: 0, annualizedReturn: 0, maxDrawdown: 0, sharpeRatio: 0, winRate: 0, totalTrades: 0, trades: [] });
  }

  if (method === "GET" && path === "/trade/account") return send(await account(getUserId(req)));
  if (method === "GET" && path === "/trade/positions") return send(await store().get(`positions:${getUserId(req)}`, { type: "json" }) || []);
  if (method === "GET" && path === "/trade/orders") return send({ list: await store().get(`orders:${getUserId(req)}`, { type: "json" }) || [], total: 0 });
  if (method === "POST" && (path === "/trade/buy" || path === "/trade/sell")) {
    const userId = getUserId(req);
    const data = await body(req);
    const orders = await store().get(`orders:${userId}`, { type: "json" }) || [];
    orders.unshift({ id: Date.now(), userId, ...data, amount: Number(data.price || 0) * Number(data.quantity || 0), fee: 0, status: "FILLED", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() });
    await store().setJSON(`orders:${userId}`, orders);
    return send(null, "交易已记录");
  }
  if (method === "DELETE" && path.startsWith("/trade/order/")) return send(null, "订单已处理");
  if (method === "GET" && path === "/trade/profit-analysis") return send({ totalTradeCount: 0, winCount: 0, loseCount: 0, winRate: 0, totalProfit: 0, totalLoss: 0, avgProfit: 0, avgLoss: 0, profitLossRatio: 0, maxDrawdown: 0, sharpeRatio: 0 });
  if (method === "GET" && path === "/trade/profit-records") return send([]);

  if (method === "GET" && path.startsWith("/ai/news")) {
    const stockCode = url.searchParams.get("stockCode") || "";
    return send([
      {
        title: `${stockCode} 实时行情驱动观察`,
        source: "Netlify实时行情引擎",
        url: "https://finance.sina.com.cn",
        publishTime: new Date().toISOString(),
        sentiment: "neutral",
        impactScore: 50,
        reason: "新闻源待接入，当前以真实实时行情和K线作为主要依据"
      }
    ]);
  }

  if (method === "GET" && path === "/ai/configs") {
    return send(await store().get("ai-configs", { type: "json" }) || []);
  }
  if (method === "POST" && path === "/ai/configs") {
    const configs = await store().get("ai-configs", { type: "json" }) || [];
    const data = await body(req);
    const item = { id: Date.now(), enabled: true, createTime: new Date().toISOString(), ...data };
    configs.push(item);
    await store().setJSON("ai-configs", configs);
    return send(item, "配置已保存");
  }
  const aiConfigById = path.match(/^\/ai\/configs\/(\d+)$/);
  if (aiConfigById && method === "PUT") {
    const configs = await store().get("ai-configs", { type: "json" }) || [];
    const data = await body(req);
    const next = configs.map((item: AnyRecord) => item.id === Number(aiConfigById[1]) ? { ...item, ...data } : item);
    await store().setJSON("ai-configs", next);
    return send(next.find((item: AnyRecord) => item.id === Number(aiConfigById[1])) || null, "配置已更新");
  }
  if (aiConfigById && method === "DELETE") {
    const configs = await store().get("ai-configs", { type: "json" }) || [];
    await store().setJSON("ai-configs", configs.filter((item: AnyRecord) => item.id !== Number(aiConfigById[1])));
    return send(null, "配置已删除");
  }
  const aiConfigTest = path.match(/^\/ai\/configs\/(\d+)\/test$/);
  if (aiConfigTest && method === "POST") {
    const configs = await store().get("ai-configs", { type: "json" }) || [];
    const item = configs.find((config: AnyRecord) => config.id === Number(aiConfigTest[1]));
    return item ? send({ available: true }, "配置存在，公网运行时可用") : send(null, "配置不存在", 404);
  }
  if (method === "POST" && path.startsWith("/ai/analyze")) {
    const data = await body(req);
    const stockCode = data.stockCode || "600000";
    const list = await fetchSina([sinaCode(stockCode)]);
    const stock = (list[0] || {}) as AnyRecord;
    const signal = stock.current ? signalFor(stock) : { type: "HOLD", strength: 50 };
    const techScore = signal.type === "BUY" ? 72 : signal.type === "SELL" ? 38 : 55;
    const sentimentScore = 50;
    const score = Math.round(techScore * 0.7 + sentimentScore * 0.3);
    return send({
      stockCode,
      stockName: stock.name || stockCode,
      signal: signal.type,
      score,
      techScore,
      sentimentScore,
      targetPrice: stock.current ? `${Number(stock.current).toFixed(2)} 附近观察` : "以真实行情为准",
      analysis: `公网 AI 研判基于真实实时行情和东方财富K线生成：当前价格 ${stock.current || "未知"}，涨跌幅 ${stock.changePercent || 0}%，技术信号 ${signal.type}。新闻/大V源暂以中性处理，避免编造舆情。`,
      modelUsed: "Netlify Quant Heuristic",
      modelAvailable: true,
      failureReason: "",
      quantDecision: {
        signal: signal.type,
        confidence: score,
        riskLevel: score >= 70 ? "MEDIUM" : "HIGH",
        trendState: signal.type === "BUY" ? "偏强" : signal.type === "SELL" ? "偏弱" : "震荡",
        suggestedPosition: signal.type === "BUY" ? "轻仓试探" : "观望",
        stopLoss: stock.current ? Number((stock.current * 0.95).toFixed(2)) : 0,
        takeProfit: stock.current ? Number((stock.current * 1.08).toFixed(2)) : 0,
        targetRange: stock.current ? `${Number((stock.current * 0.95).toFixed(2))}-${Number((stock.current * 1.08).toFixed(2))}` : "",
        summary: "真实行情 + 技术信号综合判断"
      },
      factors: [
        { name: "实时涨跌幅", score: techScore, direction: signal.type, weight: 0.7, reason: "来自新浪实时行情" },
        { name: "舆情", score: sentimentScore, direction: "neutral", weight: 0.3, reason: "公网新闻/大V源未接入，不编造观点" }
      ],
      scenarios: [],
      risks: ["公网版本暂未接入完整新闻/大V数据源", "交易记录仅用于系统内策略演示，不构成投资建议"],
      actions: signal.type === "BUY" ? ["观察回踩确认", "控制仓位", "设置止损"] : ["等待更清晰信号"],
      daVOpinions: [],
      daVMajority: { consensus: "neutral", summary: "暂无真实大V数据，系统不编造舆情", bullishCount: 0, bearishCount: 0, neutralCount: 0 },
      newsItems: [],
      candidateStrategies: [],
      selectedStrategy: null,
      evolution: { generation: 1, status: "online", lastLearning: "基于最新真实行情刷新", nextMutation: "接入新闻/大V数据后优化", outcomeJudgement: "等待更多真实交易样本", historySamples: 0 }
    });
  }

  if (path.startsWith("/stock/fund/list")) return send({ list: [], total: 0, page: 1, pageSize: 20 });
  if (path.startsWith("/stock/gold/products")) return send({ hf_GC: "COMEX黄金", hf_XAU: "伦敦金" });
  if (path.startsWith("/stock/gold/")) return send(null, "黄金公网数据源暂未接入", 503);
  if (path.startsWith("/recharge/")) return send({ list: [], total: 0 });
  if (path.startsWith("/stock/sectors")) return send([]);

  return send(null, `接口不存在：${path}`, 404);
}

export default async (req: Request) => {
  try {
    return await route(req);
  } catch (error: any) {
    return send(null, error?.message || "服务异常", 500);
  }
};

export const config = {
  path: "/api-old/*"
};
