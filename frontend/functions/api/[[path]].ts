type AnyRecord = Record<string, any>;
type Sentiment = "bullish" | "bearish" | "neutral";
type Signal = "BUY" | "SELL" | "HOLD";
type KVNamespaceLike = {
  get(key: string, type?: "json"): Promise<any>;
  put(key: string, value: string): Promise<void>;
};
type PagesEnv = {
  LIANGHUA_STATE?: KVNamespaceLike;
  LIANGHUA_SYNC_TOKEN?: string;
  WECHAT_MP_FEED_URL?: string;
  WECHAT_MP_FEED_TOKEN?: string;
  WEWE_RSS_BASE_URL?: string;
  WEWE_RSS_AUTH_CODE?: string;
  DUDUDIGUA_SHARE_LINK?: string;
  WECHAT_SYNC_INTERVAL_MINUTES?: string;
  HUABAO_API_BASE?: string;
  HUABAO_CLIENT_ID?: string;
  HUABAO_ACCOUNT_ID?: string;
  HUABAO_TRADING_ENABLED?: string;
};
type PagesContext = {
  request: Request;
  env: PagesEnv;
};

const memoryStore = new Map<string, any>();
let currentEnv: PagesEnv = {};

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
  "sz000063", "sz000333", "sz000651", "sz000858", "sz002230", "sz002594", "sz300014", "sz300015",
  "sz300033", "sz300059", "sz300122", "sz300124", "sz300274", "sz300308", "sz300316", "sz300347",
  "sz300408", "sz300433", "sz300450", "sz300454", "sz300496", "sz300498", "sz300502", "sz300628",
  "sz300661", "sz300724", "sz300750", "sz300759", "sz300760", "sz300782", "sz300896", "sz300999",
  "sh688008", "sh688009", "sh688012", "sh688036", "sh688041", "sh688052", "sh688063", "sh688111",
  "sh688126", "sh688169", "sh688187", "sh688223", "sh688256", "sh688271", "sh688303", "sh688363",
  "sh688390", "sh688396", "sh688599", "sh688981"
];
const US_STOCKS = ["gb_aapl", "gb_msft", "gb_googl", "gb_amzn", "gb_nvda", "gb_tsla", "gb_meta"];
const INDICES = ["sh000001", "sz399001", "sz399006", "sh000300"];
const METALS: Record<string, string> = {
  hf_GC: "纽约黄金",
  hf_XAU: "伦敦金",
  hf_SI: "纽约白银",
  hf_CAD: "伦铜",
  hf_NID: "伦镍",
  hf_ZSD: "伦锌",
  hf_PBD: "伦铅"
};

const SECTOR_GROUPS: Array<{ sectorCode: string; sectorName: string; codes: string[] }> = [
  { sectorCode: "bank", sectorName: "银行", codes: ["sh600036", "sh601166", "sh601328", "sh601398", "sh600016", "sh601288", "sh600000", "sh600015", "sh601818", "sz000001"] },
  { sectorCode: "liquor", sectorName: "白酒消费", codes: ["sh600519", "sz000858", "sh600887", "sh600809"] },
  { sectorCode: "medicine", sectorName: "医药医疗", codes: ["sh600276", "sh600196", "sh600332", "sh600436"] },
  { sectorCode: "broker", sectorName: "证券保险", codes: ["sh601318", "sh600030", "sh601628", "sh600837", "sh601319", "sh601601"] },
  { sectorCode: "new-energy", sectorName: "新能源", codes: ["sh601012", "sh600438", "sz002594", "sz300750", "sz300274", "sz300014", "sh688599"] },
  { sectorCode: "tech", sectorName: "科技互联网", codes: ["sz000063", "sz002230", "sz300059", "sh600570", "sh600588", "sh688111", "sh688981", "sh688041"] },
  { sectorCode: "chinext", sectorName: "创业板", codes: ["sz300033", "sz300059", "sz300122", "sz300274", "sz300308", "sz300408", "sz300433", "sz300450", "sz300496", "sz300750", "sz300760", "sz300896"] },
  { sectorCode: "star", sectorName: "科创板", codes: ["sh688008", "sh688012", "sh688036", "sh688111", "sh688126", "sh688169", "sh688223", "sh688256", "sh688303", "sh688396", "sh688599", "sh688981"] },
  { sectorCode: "resource", sectorName: "资源能源", codes: ["sh600028", "sh601857", "sh601088", "sh601899", "sh600547"] },
  { sectorCode: "manufacture", sectorName: "高端制造", codes: ["sh600031", "sh600150", "sh600760", "sz000333", "sz000651"] }
];

function filterAStocksByBoard(board = "") {
  if (board === "chinext") return A_STOCKS.filter((code) => /^sz300/.test(code));
  if (board === "star") return A_STOCKS.filter((code) => /^sh688/.test(code));
  if (board === "main") return A_STOCKS.filter((code) => /^sh60/.test(code) || /^sz00/.test(code));
  return A_STOCKS;
}

function boardNameByCode(code = "") {
  if (/^300/.test(code)) return "创业板";
  if (/^688/.test(code)) return "科创板";
  if (/^gb_/.test(code)) return "美股";
  return "主板";
}

const BULLISH_WORDS = ["增持", "买入", "上涨", "增长", "盈利", "突破", "利好", "回购", "中标", "分红", "创新高", "扩张", "净利润", "翻身仗", "拉升", "洗出去"];
const BEARISH_WORDS = ["减持", "卖出", "下跌", "亏损", "处罚", "风险", "诉讼", "退市", "暴跌", "利空", "问询", "监管", "下降", "磨顶", "破9", "阴跌", "套人", "后悔", "跌停", "水下", "脑壳痛"];

function blobStore() {
  return {
    async get(key: string, _options?: any) {
      const kv = currentEnv.LIANGHUA_STATE;
      if (kv) {
        const value = await kv.get(key, "json");
        if (value !== null) return value;
      }
      return memoryStore.get(key) ?? null;
    },
    async setJSON(key: string, value: any) {
      const kv = currentEnv.LIANGHUA_STATE;
      if (kv) {
        await kv.put(key, JSON.stringify(value));
      }
      memoryStore.set(key, value);
    }
  };
}

function send(data: any = null, message = "成功", code = 200) {
  return Response.json({ code, message, data });
}

function cleanText(value = "") {
  return String(value)
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ")
    .trim();
}

function decodeGb(buffer: ArrayBuffer) {
  try {
    return new TextDecoder("gb18030").decode(buffer);
  } catch {
    return new TextDecoder("utf-8").decode(buffer);
  }
}

function stockPrefix(code: string) {
  if (/^(sh|sz|gb_)/i.test(code)) return code.toLowerCase();
  if (/^6/.test(code)) return `sh${code}`;
  if (/^[03]/.test(code)) return `sz${code}`;
  if (/^[a-z]{1,8}$/i.test(code)) return `gb_${code.toLowerCase()}`;
  return `sh${code}`;
}

function cninfoColumn(code: string) {
  return code.startsWith("6") ? "sse" : "szse";
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

function userIdFrom(req: Request) {
  const direct = req.headers.get("x-user-id");
  if (direct) return Number(direct);
  const payload = (req.headers.get("authorization") || "").replace(/^Bearer\s+/i, "").split(".")[1];
  if (!payload) return 1;
  try {
    return Number(JSON.parse(atob(payload.replaceAll("-", "+").replaceAll("_", "/"))).userId || 1);
  } catch {
    return 1;
  }
}

async function jsonBody(req: Request) {
  try {
    return await req.json();
  } catch {
    return {};
  }
}

function syncAllowed(req: Request) {
  const expected = currentEnv.LIANGHUA_SYNC_TOKEN;
  if (!expected || expected === "set-this-in-cloudflare-pages-environment") return false;
  return req.headers.get("x-sync-token") === expected;
}

async function userByName(username: string) {
  return await blobStore().get(`user:${username}`, { type: "json" });
}

async function saveUser(user: AnyRecord) {
  const store = blobStore();
  await store.setJSON(`user:${user.username}`, user);
  await store.setJSON(`user-id:${user.id}`, user);
}

async function ensureUser(username = "admin", password = "123456") {
  const existing = await userByName(username);
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
  await blobStore().setJSON(`positions:${user.id}`, []);
  await blobStore().setJSON(`orders:${user.id}`, []);
  return user;
}

function defaultAlertSettings(user: AnyRecord = {}) {
  return {
    email: user.email || "",
    emailEnabled: false,
    kingTradeOnly: true,
    includeTopFive: true,
    updatedAt: null
  };
}

async function alertSettings(userId: number) {
  const store = blobStore();
  const user = await store.get(`user-id:${userId}`, { type: "json" }) || await ensureUser();
  const saved = await store.get(`ai-lab-alert-settings:${userId}`, { type: "json" });
  return { ...defaultAlertSettings(user), ...(saved || {}) };
}

async function saveAlertSettings(userId: number, data: AnyRecord) {
  const store = blobStore();
  const user = await store.get(`user-id:${userId}`, { type: "json" }) || await ensureUser();
  const email = String(data.email || "").trim();
  const next = {
    email,
    emailEnabled: Boolean(data.emailEnabled),
    kingTradeOnly: data.kingTradeOnly !== false,
    includeTopFive: data.includeTopFive !== false,
    updatedAt: new Date().toISOString()
  };
  await store.setJSON(`ai-lab-alert-settings:${userId}`, next);
  await saveUser({ ...user, email });
  const state = await labState(userId);
  await saveLabState(userId, { ...state, alertSettings: next });
  return next;
}

async function fetchSina(codes: string[]) {
  if (!codes.length) return [];
  const response = await fetch(`https://hq.sinajs.cn/list=${codes.join(",")}`, {
    headers: {
      Referer: "https://finance.sina.com.cn",
      "User-Agent": "Mozilla/5.0"
    }
  });
  const text = decodeGb(await response.arrayBuffer());
  return text.split("\n").map((line) => {
    const match = line.match(/hq_str_(\w+)="(.*)"/);
    if (!match) return null;
    const rawCode = match[1];
    const fields = match[2].split(",");
    if (!fields[0] || fields.length < 10) return null;
    if (rawCode.startsWith("gb_")) {
      const current = Number(fields[1]) || 0;
      const changePercent = Number(fields[2]) || 0;
      const change = Number(fields[4]) || 0;
      const prevClose = Number(fields[26]) || (current - change);
      return {
        code: rawCode.replace(/^gb_/, ""),
        name: fields[0],
        current,
        open: Number(fields[5]) || 0,
        prevClose,
        high: Number(fields[6]) || 0,
        low: Number(fields[7]) || 0,
        volume: Number(fields[10]) || 0,
        amount: Number(fields[12]) || 0,
        change,
        changePercent,
        market: "US"
      };
    }
    const current = Number(fields[3]) || 0;
    const prevClose = Number(fields[2]) || 0;
    const change = current - prevClose;
    return {
      code: rawCode.replace(/^(sh|sz|gb_)/, ""),
      name: fields[0],
      current,
      open: Number(fields[1]) || 0,
      prevClose,
      high: Number(fields[4]) || 0,
      low: Number(fields[5]) || 0,
      volume: Number(fields[8]) || Number(fields[6]) || 0,
      amount: Number(fields[9]) || Number(fields[7]) || 0,
      change,
      changePercent: prevClose ? Number(((change / prevClose) * 100).toFixed(2)) : 0,
      market: rawCode.startsWith("gb_") ? "US" : "A"
    };
  }).filter(Boolean);
}

async function fetchEastmoneyBoardStocks(boardCode: string) {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4500);
    const url = `https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=5&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=b:${encodeURIComponent(boardCode)}&fields=f12,f14,f2,f3,f5,f6`;
    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        Referer: "https://quote.eastmoney.com",
        "User-Agent": "Mozilla/5.0"
      }
    });
    clearTimeout(timeout);
    const json = await response.json() as AnyRecord;
    return ((json.data?.diff || []) as AnyRecord[])
      .filter((item) => item.f12 && item.f14)
      .map((item) => ({
        stockCode: String(item.f12),
        stockName: String(item.f14),
        currentPrice: Number(item.f2 || 0),
        changePercent: Number(item.f3 || 0),
        marketCap: Number(item.f6 || 0)
      }));
  } catch {
    return [];
  }
}

async function fetchEastmoneySectorRows() {
  try {
    const url = "https://push2.eastmoney.com/api/qt/clist/get?pn=1&pz=20&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:90+t:2&fields=f12,f14,f3,f62,f128,f136,f140,f141";
    const response = await fetch(url, {
      headers: {
        Referer: "https://quote.eastmoney.com",
        "User-Agent": "Mozilla/5.0"
      }
    });
    const json = await response.json() as AnyRecord;
    const boards = ((json.data?.diff || []) as AnyRecord[]).filter((item) => item.f12 && item.f14);
    const rows: AnyRecord[] = [];
    for (const board of boards.slice(0, 8)) {
      const boardCode = String(board.f12);
      const stocks = await fetchEastmoneyBoardStocks(boardCode);
      const leaderCode = String(board.f140 || "");
      const leaderName = String(board.f128 || "");
      const leaderChange = Number(board.f136 || 0);
      const leaderStock = leaderCode
        ? {
            stockCode: leaderCode,
            stockName: leaderName || leaderCode,
            currentPrice: 0,
            changePercent: leaderChange,
            marketCap: null
          }
        : null;
      const mergedStocks = stocks.length
        ? stocks
        : leaderStock
          ? [leaderStock]
          : [];
      rows.push({
        sectorName: String(board.f14),
        sectorCode: boardCode,
        changePercent: Number(board.f3 || 0),
        leaderStock: leaderCode || mergedStocks[0]?.stockCode || null,
        leaderName: leaderName || mergedStocks[0]?.stockName || null,
        stockCount: mergedStocks.length,
        avgChange: Number(board.f3 || 0),
        leaderChangePercent: leaderChange || Number(mergedStocks[0]?.changePercent || 0),
        source: "eastmoney-push2-industry",
        stocks: mergedStocks
      });
    }
    return rows.filter((item) => item.sectorCode && item.sectorName);
  } catch {
    return [];
  }
}

async function fetchGoldQuote(code = "hf_GC") {
  const response = await fetch(`https://hq.sinajs.cn/list=${code}`, {
    headers: {
      Referer: "https://finance.sina.com.cn",
      "User-Agent": "Mozilla/5.0"
    }
  });
  const text = decodeGb(await response.arrayBuffer());
  const match = text.match(/hq_str_([^=]+)="([^"]*)"/);
  if (!match) throw new Error("gold quote unavailable");
  const fields = match[2].split(",");
  const price = Number(fields[0]) || 0;
  const prevClose = Number(fields[7] || fields[1] || 0);
  const changePercent = prevClose ? Number((((price - prevClose) / prevClose) * 100).toFixed(2)) : 0;
  return {
    productCode: code,
    productName: fields[13] || (code === "hf_XAU" ? "伦敦金" : "COMEX黄金"),
    price,
    changePercent,
    high: Number(fields[4]) || price,
    low: Number(fields[5]) || price,
    openPrice: Number(fields[2] || fields[1]) || price,
    tradeDate: `${fields[12] || new Date().toISOString().slice(0, 10)} ${fields[6] || ""}`.trim(),
    source: "sina-public-gold"
  };
}

const FUND_CODES = [
  "000001", "110022", "161725", "163402", "000083", "003096", "005827", "006327",
  "001875", "002594", "519674", "270002", "260108", "000311", "001938", "005827"
];

function classifyFund(name = "") {
  if (/货币|现金|添利|宝/.test(name)) return "货币型";
  if (/债|纯债|信用/.test(name)) return "债券型";
  if (/指数|ETF|联接|LOF|300|500|100|50/.test(name)) return "指数型";
  if (/股票|行业|消费|医药|新能源|科技|半导体|军工/.test(name)) return "股票型";
  return "混合型";
}

async function fetchFundRealtime(code: string) {
  const response = await fetch(`https://fundgz.1234567.com.cn/js/${code}.js?rt=${Date.now()}`, {
    headers: {
      Referer: "https://fund.eastmoney.com",
      "User-Agent": "Mozilla/5.0"
    }
  });
  const text = await response.text();
  const match = text.match(/jsonpgz\((.*)\);?/);
  if (!match) return null;
  if (!match[1]?.trim()) return null;
  const json = JSON.parse(match[1]);
  const nav = Number(json.gsz || json.dwjz || 0);
  return {
    code: json.fundcode || code,
    name: json.name || code,
    nav,
    accNav: Number(json.dwjz || nav),
    navDate: json.gztime || json.jzrq || "",
    changePercent: Number(json.gszzl || 0),
    fundType: classifyFund(json.name || ""),
    source: "eastmoney-fundgz"
  };
}

async function fetchFundList(page = 1, pageSize = 20, keyword = "", fundType = "") {
  const rows = (await Promise.all(FUND_CODES.map((code) => fetchFundRealtime(code)))).filter(Boolean) as AnyRecord[];
  const filtered = rows.filter((fund) => {
    const hitKeyword = !keyword || String(fund.code).includes(keyword) || String(fund.name).includes(keyword);
    const hitType = !fundType || String(fund.fundType).includes(fundType);
    return hitKeyword && hitType;
  });
  const start = (page - 1) * pageSize;
  return {
    list: filtered.slice(start, start + pageSize),
    total: filtered.length,
    page,
    pageSize
  };
}

async function fetchFundNavPoints(code: string) {
  const fund = await fetchFundRealtime(code);
  if (!fund) return [];
  return [{
    date: String(fund.navDate || new Date().toISOString()).slice(0, 10),
    nav: fund.nav,
    accNav: fund.accNav,
    changePercent: fund.changePercent,
    source: fund.source
  }];
}

async function fetchSectorRows() {
  const eastmoneyRows = await fetchEastmoneySectorRows();
  if (eastmoneyRows.length >= 5) return eastmoneyRows;

  const rows: AnyRecord[] = [];
  for (const group of SECTOR_GROUPS) {
    let stocks = (await fetchSina(group.codes)) as AnyRecord[];
    if (stocks.length < Math.min(3, group.codes.length)) {
      const fallbackStocks: AnyRecord[] = [];
      for (const code of group.codes) {
        try {
          const single = (await fetchSina([code])) as AnyRecord[];
          if (single[0]) fallbackStocks.push(single[0]);
        } catch {
          // Keep the sector usable with the remaining real quotes.
        }
      }
      if (fallbackStocks.length > stocks.length) stocks = fallbackStocks;
    }
    const valid = stocks.filter((item) => Number(item.current) > 0);
    if (!valid.length) continue;
    const sorted = [...valid].sort((a, b) => Number(b.changePercent || 0) - Number(a.changePercent || 0));
    const avgChange = Number((valid.reduce((sum, item) => sum + Number(item.changePercent || 0), 0) / valid.length).toFixed(2));
    const leader = sorted[0];
    rows.push({
      sectorName: group.sectorName,
      sectorCode: group.sectorCode,
      changePercent: avgChange,
      leaderStock: leader?.code || null,
      leaderName: leader?.name || null,
      stockCount: valid.length,
      avgChange,
      leaderChangePercent: Number(leader?.changePercent || 0),
      stocks: valid.map((stock) => ({
        stockCode: stock.code,
        stockName: stock.name,
        currentPrice: stock.current,
        changePercent: stock.changePercent,
        marketCap: null
      }))
    });
  }
  return rows.sort((a, b) => Number(b.changePercent || 0) - Number(a.changePercent || 0));
}

function sectorAiReason(sector: AnyRecord) {
  const change = Number(sector.changePercent || 0);
  const leader = Number(sector.leaderChangePercent || 0);
  if (change >= 1.5 && leader >= 2) return "板块整体上涨且龙头股强势，说明资金共振较好，适合进入AI实验室重点观察。";
  if (change >= 0.3) return "板块温和走强，龙头股有带动效应，适合低仓位验证趋势持续性。";
  if (change < -1) return "板块短线承压，但若新闻或政策催化转强，可作为回撤低吸候选。";
  return "板块处于震荡区间，建议等待新闻、资金和技术面进一步确认。";
}

function stockAiScore(stock: AnyRecord, sector: AnyRecord) {
  const change = Number(stock.changePercent || 0);
  const sectorChange = Number(sector.changePercent || 0);
  const momentum = Math.max(0, Math.min(35, (change + 3) * 5));
  const sectorBoost = Math.max(0, Math.min(25, (sectorChange + 2) * 5));
  const stability = Math.max(0, 25 - Math.abs(change) * 2);
  return Math.round(Math.max(0, Math.min(100, 35 + momentum + sectorBoost + stability)));
}

async function aiTopSectorPicks() {
  const sectors = await fetchSectorRows();
  const topSectors = sectors.slice(0, 5).map((sector) => {
    const stocks = [...(sector.stocks || [])]
      .map((stock) => ({
        code: stock.stockCode,
        name: stock.stockName,
        changePercent: Number(stock.changePercent || 0),
        aiScore: stockAiScore(stock, sector),
        aiTrend: Number(stock.changePercent || 0) >= 1 ? "强势跟踪" : Number(stock.changePercent || 0) < 0 ? "回撤观察" : "震荡确认",
        aiReason: `所在${sector.sectorName}板块涨跌 ${Number(sector.changePercent || 0).toFixed(2)}%，个股涨跌 ${Number(stock.changePercent || 0).toFixed(2)}%，按板块强度、龙头动量和波动稳定性综合评分。`
      }))
      .sort((a, b) => b.aiScore - a.aiScore)
      .slice(0, 5);
    return {
      sectorName: sector.sectorName,
      sectorCode: sector.sectorCode,
      changePercent: Number(sector.changePercent || 0),
      aiScore: Math.round(stocks.reduce((sum, item) => sum + item.aiScore, 0) / Math.max(1, stocks.length)),
      aiReason: sectorAiReason(sector),
      leaderStocks: stocks
    };
  });
  const report = { topSectors, analysisTime: new Date().toISOString(), source: "sina-public-quotes" };
  await blobStore().setJSON("ai-sector-top-picks", report);
  return report;
}

async function labState(userId: number) {
  const store = blobStore();
  const ownState = await store.get(`ai-lab-state:${userId}`, { type: "json" });
  if (ownState && (Number(ownState.generation || 0) > 0 || (ownState.experiments || []).length || (ownState.assets || []).length)) {
    return ownState;
  }
  if (userId !== 1) {
    const globalState = await store.get("ai-lab-state:1", { type: "json" });
    if (globalState && (Number(globalState.generation || 0) > 0 || (globalState.experiments || []).length || (globalState.assets || []).length)) {
      return { ...globalState, inheritedFromGlobalLab: true };
    }
  }
  return ownState || {
    generation: 0,
    iterationCount: 0,
    capital: 100000,
    intervalMinutes: 5,
    assets: [],
    experiments: [],
    evolutionLog: [],
    champion: null,
    lastRunAt: null,
    updatedAt: null
  };
}

async function labIterations(userId: number) {
  const store = blobStore();
  const own = await store.get(`ai-lab-iterations:${userId}`, { type: "json" }) || [];
  if (own.length || userId === 1) return own;
  return await store.get("ai-lab-iterations:1", { type: "json" }) || [];
}

async function labTrades(userId: number) {
  const state = await labState(userId);
  return Array.isArray(state?.simulatedTrades) ? state.simulatedTrades : [];
}

function labOrdersFromTrades(trades: AnyRecord[]) {
  const orders: AnyRecord[] = [];
  for (const trade of trades) {
    const quantity = Number(trade.quantity || 0);
    const buyPrice = Number(trade.buyPrice || 0);
    const sellPrice = Number(trade.sellPrice || 0);
    orders.push({
      id: `${trade.id}-BUY`,
      stockCode: trade.assetCode,
      stockName: trade.assetName,
      assetType: String(trade.assetCode || "").startsWith("hf_") ? "gold" : FUND_CODES.includes(String(trade.assetCode || "")) ? "fund" : "stock",
      direction: "BUY",
      price: buyPrice,
      quantity,
      amount: Number(trade.amount || buyPrice * quantity),
      fee: Number(trade.fee || 5),
      status: "FILLED",
      strategyName: trade.strategyName,
      source: "AI实验室模拟",
      createdAt: trade.createdAt,
      updatedAt: trade.createdAt
    });
    if (String(trade.status || "") !== "持仓中" && sellPrice > 0) {
      orders.push({
        id: `${trade.id}-SELL`,
        stockCode: trade.assetCode,
        stockName: trade.assetName,
        assetType: String(trade.assetCode || "").startsWith("hf_") ? "gold" : FUND_CODES.includes(String(trade.assetCode || "")) ? "fund" : "stock",
        direction: "SELL",
        price: sellPrice,
        quantity,
        amount: Number((sellPrice * quantity).toFixed(2)),
        fee: Number(trade.fee || 5),
        profit: Number(trade.profit || 0),
        closeReason: trade.closeReason || "",
        status: "FILLED",
        strategyName: trade.strategyName,
        source: "AI实验室模拟",
        createdAt: trade.closedAt || trade.createdAt,
        updatedAt: trade.closedAt || trade.createdAt
      });
    }
  }
  return orders.sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
}

function labPositionsFromTrades(trades: AnyRecord[]) {
  return trades
    .filter((trade) => String(trade.status || "") === "持仓中")
    .map((trade) => {
      const quantity = Number(trade.quantity || 0);
      const costPrice = Number(trade.buyPrice || 0);
      const currentPrice = Number(trade.currentPrice || trade.buyPrice || 0);
      const marketValue = Number((currentPrice * quantity).toFixed(2));
      const costAmount = Number(trade.amount || costPrice * quantity);
      const profit = Number((marketValue - costAmount - Number(trade.fee || 5)).toFixed(2));
      const profitPercent = costAmount > 0 ? Number((profit / costAmount * 100).toFixed(2)) : 0;
      return {
        stockCode: trade.assetCode,
        stockName: trade.assetName,
        assetType: String(trade.assetCode || "").startsWith("hf_") ? "gold" : FUND_CODES.includes(String(trade.assetCode || "")) ? "fund" : "stock",
        quantity,
        availableQuantity: quantity,
        costPrice,
        currentPrice,
        marketValue,
        profit,
        profitPercent,
        strategyName: trade.strategyName,
        bucketName: trade.bucketName || "",
        source: "AI实验室模拟",
        createdAt: trade.createdAt
      };
    });
}

function labProfitAnalysisFromTrades(trades: AnyRecord[]) {
  const closed = trades.filter((trade) => String(trade.status || "") !== "持仓中" && Number.isFinite(Number(trade.profit)));
  const profits = closed.map((trade) => Number(trade.profit || 0));
  const wins = profits.filter((value) => value > 0);
  const losses = profits.filter((value) => value < 0);
  const totalProfit = Number(wins.reduce((sum, value) => sum + value, 0).toFixed(2));
  const totalLoss = Number(losses.reduce((sum, value) => sum + value, 0).toFixed(2));
  return {
    totalTradeCount: closed.length,
    winCount: wins.length,
    loseCount: losses.length,
    winRate: closed.length ? Number((wins.length / closed.length * 100).toFixed(2)) : 0,
    totalProfit,
    totalLoss,
    avgProfit: wins.length ? Number((totalProfit / wins.length).toFixed(2)) : 0,
    avgLoss: losses.length ? Number((totalLoss / losses.length).toFixed(2)) : 0,
    profitLossRatio: totalLoss ? Number(Math.abs(totalProfit / totalLoss).toFixed(2)) : 0,
    maxDrawdown: Math.abs(Math.min(0, ...profits)),
    sharpeRatio: 0
  };
}

function labProfitRecordsFromTrades(trades: AnyRecord[]) {
  const map = new Map<string, number>();
  for (const trade of trades) {
    if (String(trade.status || "") === "持仓中") continue;
    const date = String(trade.closedAt || trade.createdAt || new Date().toISOString()).slice(0, 10);
    map.set(date, Number(((map.get(date) || 0) + Number(trade.profit || 0)).toFixed(2)));
  }
  return [...map.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([date, profit]) => ({ date, profit, source: "AI实验室模拟" }));
}

async function markDirtyUser(userId: number) {
  const store = blobStore();
  const users = await store.get("ai-lab-dirty-users", { type: "json" }) || [];
  if (!users.includes(userId)) {
    users.push(userId);
    await store.setJSON("ai-lab-dirty-users", users);
  }
}

function isPlainObject(value: unknown): value is AnyRecord {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function isGoodObjectArray(value: unknown) {
  return Array.isArray(value) && value.every((item) => isPlainObject(item));
}

function isValidLabStateShape(state: unknown) {
  if (!isPlainObject(state)) return false;
  if (!isGoodObjectArray(state.assets || [])) return false;
  if (!isGoodObjectArray(state.experiments || [])) return false;
  if (!isGoodObjectArray(state.evolutionLog || [])) return false;
  if (state.customStrategies != null && !isGoodObjectArray(state.customStrategies)) return false;
  if (state.champion != null && !isPlainObject(state.champion)) return false;
  return true;
}

async function saveLabState(userId: number, data: AnyRecord) {
  const previous = await labState(userId);
  const next = mergeLabState(previous, data);
  await blobStore().setJSON(`ai-lab-state:${userId}`, next);
  await markDirtyUser(userId);
  return next;
}

function isBadResearchDirection(value: string) {
  const text = String(value || "").trim();
  if (!text) return false;
  const questionCount = (text.match(/\?/g) || []).length;
  return questionCount >= 6 && questionCount / Math.max(text.length, 1) > 0.35;
}

function defaultResearchDirection(target: string) {
  const base = "结合历史周期、技术指标、新闻舆情、资金流、行业位置和风险回撤，持续总结买点、卖点和持仓周期。";
  const known: Record<string, string> = {
    "600519": `贵州茅台：${base}重点研究白酒消费周期、北向资金、机构观点、业绩公告和估值区间。`,
    "300750": `宁德时代：${base}重点研究新能源产业链、订单变化、原材料价格、政策新闻和成长股风险。`,
    "110022": `易方达消费行业股票：${base}重点研究基金净值曲线、重仓消费股表现、基金经理风格和回撤控制。`
  };
  return known[target] || `${target ? `${target}：` : ""}${base}`;
}

function cleanResearchDirection(direction: string, target: string) {
  const text = String(direction || "").trim();
  if (!text || isBadResearchDirection(text)) return defaultResearchDirection(target);
  return text;
}

function normalizeResearchFocusList(value: unknown): AnyRecord[] {
  if (!Array.isArray(value)) return [];
  const seen = new Set<string>();
  const result: AnyRecord[] = [];
  for (const raw of value) {
    if (!raw || typeof raw !== "object") continue;
    const item = raw as AnyRecord;
    const target = String(item.target || "").trim();
    const rawDirection = String(item.direction || "").trim();
    if (!target && !rawDirection) continue;
    const direction = cleanResearchDirection(rawDirection, target);
    const key = String(item.id || `${target}|${direction}`);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push({
      id: String(item.id || `focus-${target || "global"}-${result.length}`),
      target,
      direction,
      updatedAt: String(item.updatedAt || new Date().toISOString())
    });
  }
  return result;
}

function focusListFromState(state: AnyRecord) {
  const list = normalizeResearchFocusList(state?.researchFocuses);
  if (!list.length && state?.researchFocus && typeof state.researchFocus === "object") {
    return normalizeResearchFocusList([state.researchFocus]);
  }
  return list;
}

function mergeResearchFocuses(previous: AnyRecord, incoming: AnyRecord) {
  const incomingList = focusListFromState(incoming);
  const previousList = focusListFromState(previous);
  if (incoming.researchFocusesReplace === true) return incomingList;
  const merged = new Map<string, AnyRecord>();
  for (const item of previousList) merged.set(String(item.id || `${item.target}|${item.direction}`), item);
  for (const item of incomingList) merged.set(String(item.id || `${item.target}|${item.direction}`), item);
  return [...merged.values()];
}

function mergeLabState(previous: AnyRecord, data: AnyRecord) {
  const researchFocuses = mergeResearchFocuses(previous, data);
  const next = {
    ...previous,
    ...data,
    researchFocuses,
    researchFocus: researchFocuses[0] || null,
    iterationCount: Number(data.iterationCount ?? data.generation ?? previous.iterationCount ?? 0),
    updatedAt: new Date().toISOString()
  };
  delete (next as AnyRecord).researchFocusesReplace;
  return next;
}

function toStockInfo(stock: AnyRecord) {
  return {
    code: stock.code,
    name: stock.name,
    market: stock.market || "A",
    currentPrice: stock.current,
    openPrice: stock.open,
    closePrice: stock.prevClose,
    highPrice: stock.high,
    lowPrice: stock.low,
    volume: stock.volume,
    turnover: stock.amount,
    changePercent: stock.changePercent,
    changeAmount: stock.change,
    turnoverRate: 0,
    pe: 0,
    pb: 0,
    marketCap: 0,
    totalShares: 0,
    circulateShares: 0
  };
}

function klinePeriodToEastmoney(period = "daily") {
  if (period === "weekly") return "102";
  if (period === "monthly") return "103";
  return "101";
}

function klinePeriodToTencent(period = "daily") {
  if (period === "weekly") return { key: "qfqweek", value: "week" };
  if (period === "monthly") return { key: "qfqmonth", value: "month" };
  return { key: "qfqday", value: "day" };
}

function normalizeKlineRows(rows: string[][], period: string, source: string) {
  const parsed = rows.map((row) => {
    const open = Number(row[1]);
    const close = Number(row[2]);
    const high = Number(row[3]);
    const low = Number(row[4]);
    return { date: row[0], price: [open, close, low, high], volume: Number(row[5]) || 0 };
  }).filter((row: AnyRecord) => row.date && row.price.every((value: number) => Number.isFinite(value) && value > 0));
  return {
    dates: parsed.map((row: AnyRecord) => row.date),
    prices: parsed.map((row: AnyRecord) => row.price),
    volumes: parsed.map((row: AnyRecord) => row.volume),
    turnover: [],
    period,
    source
  };
}

async function fetchTencentKline(code: string, period = "daily", limit = 120) {
  const prefixed = stockPrefix(code);
  const tencent = klinePeriodToTencent(period);
  const url = `https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=${prefixed},${tencent.value},,,${limit},qfq`;
  try {
    const response = await fetch(url, {
      headers: {
        "User-Agent": "Mozilla/5.0",
        Referer: "https://gu.qq.com"
      }
    });
    const json = await response.json() as AnyRecord;
    const rows = json?.data?.[prefixed]?.[tencent.key] || [];
    if (Array.isArray(rows) && rows.length) return normalizeKlineRows(rows, period, "tencent-kline");
  } catch {
    // Keep trying other sources.
  }
  return null;
}

async function fetchKline(code: string, period = "daily", limit = 120) {
  const secid = code.startsWith("6") ? `1.${code}` : `0.${code}`;
  const klt = klinePeriodToEastmoney(period);
  const url = `https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=${secid}&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61&klt=${klt}&fqt=1&end=20500101&lmt=${limit}`;
  try {
    const response = await fetch(url, {
      headers: {
        "User-Agent": "Mozilla/5.0",
        Referer: "https://quote.eastmoney.com"
      }
    });
    const text = await response.text();
    const json = JSON.parse(text);
    const rows = json?.data?.klines || [];
    if (rows.length) {
      const parsed = rows.map((row: string) => {
        const fields = row.split(",");
        const open = Number(fields[1]);
        const close = Number(fields[2]);
        const high = Number(fields[3]);
        const low = Number(fields[4]);
        return { date: fields[0], price: [open, close, low, high], volume: Number(fields[5]) || 0, turnover: Number(fields[6]) || 0 };
      }).filter((row: AnyRecord) => row.date && row.price.every((value: number) => Number.isFinite(value) && value > 0));
      return {
        dates: parsed.map((row: AnyRecord) => row.date),
        prices: parsed.map((row: AnyRecord) => row.price),
        volumes: parsed.map((row: AnyRecord) => row.volume),
        turnover: parsed.map((row: AnyRecord) => row.turnover),
        period,
        source: "eastmoney-kline"
      };
    }
  } catch {
    // Keep the API usable without inventing historical candles.
  }
  const tencent = await fetchTencentKline(code, period, limit);
  if (tencent?.dates?.length) return tencent;
  return {
    dates: [],
    prices: [],
    volumes: [],
    turnover: [],
    period,
    source: "history-unavailable"
  };
}

function sentimentFromText(text: string): { sentiment: Sentiment; impactScore: number; score: number; reason: string } {
  const bullish = BULLISH_WORDS.filter((word) => text.includes(word)).length;
  const bearish = BEARISH_WORDS.filter((word) => text.includes(word)).length;
  if (bullish > bearish) {
    return { sentiment: "bullish", impactScore: Math.min(90, 58 + bullish * 8), score: Math.min(90, 58 + bullish * 8), reason: `命中${bullish}个偏多关键词` };
  }
  if (bearish > bullish) {
    return { sentiment: "bearish", impactScore: Math.min(90, 58 + bearish * 8), score: Math.max(10, 42 - bearish * 8), reason: `命中${bearish}个偏空关键词` };
  }
  return { sentiment: "neutral", impactScore: 50, score: 50, reason: "未命中明确多空关键词" };
}

function averageScore(items: Array<{ score?: number; sentiment?: Sentiment }>, fallback = 50) {
  if (!items.length) return fallback;
  const total = items.reduce((sum, item) => {
    if (typeof item.score === "number") return sum + item.score;
    if (item.sentiment === "bullish") return sum + 65;
    if (item.sentiment === "bearish") return sum + 35;
    return sum + 50;
  }, 0);
  return Math.round(total / items.length);
}

function consensus(items: Array<{ sentiment?: Sentiment; type?: Sentiment }>) {
  const bullishCount = items.filter((item) => (item.sentiment || item.type) === "bullish").length;
  const bearishCount = items.filter((item) => (item.sentiment || item.type) === "bearish").length;
  const neutralCount = Math.max(0, items.length - bullishCount - bearishCount);
  const final: Sentiment = bullishCount > bearishCount && bullishCount > neutralCount
    ? "bullish"
    : bearishCount > bullishCount && bearishCount > neutralCount
      ? "bearish"
      : "neutral";
  return { consensus: final, bullishCount, bearishCount, neutralCount };
}

function moneyFlowFromQuote(stock: AnyRecord, sentimentScore = 50) {
  const volume = Number(stock.volume || 0);
  const amount = Number(stock.amount || stock.turnover || 0);
  const changePercent = Number(stock.changePercent || 0);
  const pressure = Math.max(-1, Math.min(1, changePercent / 5 + (sentimentScore - 50) / 100));
  const activeAmount = amount > 0 ? amount : volume * Number(stock.current || 0);
  const buyRatio = Math.max(0.18, Math.min(0.82, 0.5 + pressure * 0.28));
  const bigOrderBuyAmount = Math.round(activeAmount * buyRatio * 0.28);
  const bigOrderSellAmount = Math.round(activeAmount * (1 - buyRatio) * 0.28);
  const netBigOrderAmount = bigOrderBuyAmount - bigOrderSellAmount;
  const expectedVolume = Math.round(volume * Math.max(0.65, Math.min(1.8, 1 + Math.abs(changePercent) / 8 + Math.abs(sentimentScore - 50) / 120)));
  const direction = netBigOrderAmount > 0 ? "大单净买入" : netBigOrderAmount < 0 ? "大单净卖出" : "大单均衡";
  return {
    bigOrderBuyAmount,
    bigOrderSellAmount,
    netBigOrderAmount,
    bigOrderDirection: direction,
    expectedVolume,
    expectedVolumeChangePercent: volume ? Number(((expectedVolume / volume - 1) * 100).toFixed(2)) : 0,
    basis: "基于真实实时成交量、成交额、涨跌幅和新闻舆情分估算；不是虚构成交明细"
  };
}

async function stockMeta(code: string) {
  const list = await fetchSina([stockPrefix(code)]);
  return (list[0] || { code, name: code, current: 0, changePercent: 0 }) as AnyRecord;
}

async function searchStocks(keyword: string) {
  const kw = keyword.trim().toLowerCase();
  if (!kw) return [];
  const exactCodes = new Set<string>();
  if (/^\d{6}$/.test(kw)) exactCodes.add(stockPrefix(kw));
  if (/^[a-z]{1,6}$/i.test(kw)) exactCodes.add(`gb_${kw.toLowerCase()}`);

  const pool = [...A_STOCKS, ...US_STOCKS];
  const candidates = pool.filter((code) => {
    const rawCode = code.replace(/^sh|^sz|^gb_/, "").toLowerCase();
    if (rawCode.includes(kw) || code.toLowerCase().includes(kw)) return true;
    return false;
  });
  exactCodes.forEach((code) => candidates.unshift(code));

  const uniqueCodes = [...new Set(candidates)].slice(0, 40);
  let list = uniqueCodes.length ? await fetchSina(uniqueCodes) : [];
  if (!/^\d{6}$/.test(kw)) {
    const broader = await fetchSina(pool.slice(0, 120));
    list = [...list, ...broader.filter((item: AnyRecord) => String(item.name || "").toLowerCase().includes(kw))];
  }
  const unique = new Map<string, AnyRecord>();
  for (const item of list) {
    const code = String(item.code || "").toLowerCase();
    if (!code || unique.has(code)) continue;
    const market = String(item.market || "").toUpperCase() === "US" ? "US" : "A";
    unique.set(code, {
      ...item,
      board: market === "US" ? "美股" : boardNameByCode(code),
      market
    });
  }
  return [...unique.values()].slice(0, 30);
}

async function fetchEastmoneyNews(code: string, keyword: string) {
  const param = {
    uid: "",
    keyword,
    type: ["cmsArticleWebOld"],
    client: "web",
    clientType: "web",
    clientVersion: "curr",
    param: {
      cmsArticleWebOld: {
        searchScope: "default",
        sort: "default",
        pageIndex: 1,
        pageSize: 8
      }
    }
  };
  const api = `https://search-api-web.eastmoney.com/search/jsonp?cb=callback&param=${encodeURIComponent(JSON.stringify(param))}`;
  try {
    const response = await fetch(api, { headers: { "User-Agent": "Mozilla/5.0" } });
    const text = await response.text();
    const json = JSON.parse(text.replace(/^callback\(/, "").replace(/\);?$/, ""));
    return (json?.result?.cmsArticleWebOld || []).map((item: AnyRecord) => {
      const title = cleanText(item.title);
      const sentiment = sentimentFromText(`${title} ${cleanText(item.content || "")}`);
      return {
        title,
        source: "东方财富资讯",
        url: item.url || `https://finance.eastmoney.com/a/${item.code}.html`,
        publishTime: item.date || new Date().toISOString(),
        sentiment: sentiment.sentiment,
        impactScore: sentiment.impactScore,
        score: sentiment.score,
        reason: `${keyword} 相关新闻；${sentiment.reason}`
      };
    }).filter((item: AnyRecord) => item.title);
  } catch {
    return [];
  }
}

async function fetchSinaNews(code: string, keyword: string) {
  const api = `https://vip.stock.finance.sina.com.cn/corp/view/vCB_AllNewsStock.php?symbol=${stockPrefix(code)}`;
  try {
    const response = await fetch(api, { headers: { "User-Agent": "Mozilla/5.0" } });
    const html = decodeGb(await response.arrayBuffer());
    const items: AnyRecord[] = [];
    const regex = /(\d{4}-\d{2}-\d{2})&nbsp;(\d{2}:\d{2})&nbsp;&nbsp;<a[^>]+href=['"]([^'"]+)['"][^>]*>([\s\S]*?)<\/a>/g;
    for (const match of html.matchAll(regex)) {
      const title = cleanText(match[4]);
      if (!title || items.some((item) => item.title === title)) continue;
      const sentiment = sentimentFromText(title);
      items.push({
        title,
        source: "新浪公开新闻",
        url: match[3],
        publishTime: `${match[1]} ${match[2]}`,
        sentiment: sentiment.sentiment,
        impactScore: sentiment.impactScore,
        score: sentiment.score,
        reason: `${code} 新浪公开新闻；${sentiment.reason}`
      });
      if (items.length >= 5) break;
    }
    return items;
  } catch {
    return [];
  }
}

async function fetchCctvNews(keyword = "") {
  try {
    const response = await fetch("https://news.cctv.com/", {
      headers: { "User-Agent": "Mozilla/5.0" }
    });
    const html = await response.text();
    const items: AnyRecord[] = [];
    const regex = /<a[^>]+href=["'](https?:\/\/news\.cctv\.com\/[^"']+)["'][^>]*>([\s\S]*?)<\/a>/g;
    for (const match of html.matchAll(regex)) {
      const title = cleanText(match[2]);
      if (!title || title.length < 6 || items.some((item) => item.title === title)) continue;
      if (keyword && !title.includes(keyword)) {
        const economyWords = ["经济", "金融", "资本", "市场", "产业", "科技", "消费", "能源", "汽车", "AI"];
        if (!economyWords.some((word) => title.includes(word))) continue;
      }
      const sentiment = sentimentFromText(title);
      items.push({
        title,
        source: "央视新闻",
        url: match[1],
        publishTime: new Date().toISOString(),
        sentiment: sentiment.sentiment,
        impactScore: sentiment.impactScore,
        score: sentiment.score,
        category: inferNewsCategory(title),
        relatedStockHint: inferRelatedStock(title),
        reason: `央视新闻重点源；${sentiment.reason}`
      });
      if (items.length >= 10) break;
    }
    return items;
  } catch {
    return [];
  }
}

function inferNewsCategory(title: string) {
  if (title.includes("金融") || title.includes("资本") || title.includes("银行") || title.includes("证券")) return "金融";
  if (title.includes("科技") || title.includes("AI") || title.includes("芯片") || title.includes("数据")) return "科技";
  if (title.includes("消费") || title.includes("旅游") || title.includes("白酒")) return "消费";
  if (title.includes("能源") || title.includes("电力") || title.includes("煤") || title.includes("油")) return "能源";
  if (title.includes("医药") || title.includes("医疗")) return "医药";
  if (title.includes("地产") || title.includes("房地产")) return "地产";
  return "宏观";
}

function inferRelatedStock(title: string) {
  const hints = [
    { word: "银行", stock: "银行板块/招商银行/浦发银行" },
    { word: "白酒", stock: "白酒板块/贵州茅台/五粮液" },
    { word: "AI", stock: "AI算力/科技板块" },
    { word: "芯片", stock: "半导体板块" },
    { word: "能源", stock: "能源板块/中国石油/中国石化" },
    { word: "医药", stock: "医药板块/恒瑞医药" },
    { word: "汽车", stock: "汽车板块/比亚迪" }
  ];
  return hints.find((item) => title.includes(item.word))?.stock || "需AI进一步关联";
}

async function fetchCninfoAnnouncements(code: string) {
  try {
    const stockListResponse = await fetch("https://www.cninfo.com.cn/new/data/szse_stock.json", { headers: { "User-Agent": "Mozilla/5.0" } });
    const stockList = await stockListResponse.json();
    const hit = (stockList?.stockList || []).find((item: AnyRecord) => item.code === code);
    if (!hit?.orgId) return [];
    const body = new URLSearchParams({
      stock: `${code},${hit.orgId}`,
      searchkey: "",
      plate: "",
      category: "",
      trade: "",
      column: cninfoColumn(code),
      columnTitle: "历史公告查询",
      pageNum: "1",
      pageSize: "8",
      tabName: "fulltext",
      sortName: "",
      sortType: "",
      limit: "",
      showTitle: "",
      seDate: ""
    });
    const response = await fetch("https://www.cninfo.com.cn/new/hisAnnouncement/query", {
      method: "POST",
      body,
      headers: {
        Referer: "https://www.cninfo.com.cn/new/commonUrl/pageOfSearch?url=disclosure/list/search",
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "User-Agent": "Mozilla/5.0"
      }
    });
    const json = await response.json();
    return (json?.announcements || []).map((item: AnyRecord) => {
      const title = cleanText(item.announcementTitle || item.shortTitle || "");
      const sentiment = sentimentFromText(title);
      return {
        title,
        source: "巨潮资讯公告",
        url: `https://www.cninfo.com.cn/new/disclosure/detail?stockCode=${code}&announcementId=${item.announcementId}&orgId=${hit.orgId}&announcementTime=${item.announcementTime}`,
        publishTime: item.announcementTime ? new Date(item.announcementTime).toISOString() : new Date().toISOString(),
        sentiment: sentiment.sentiment,
        impactScore: Math.max(sentiment.impactScore, title.includes("年度报告") || title.includes("季度报告") ? 68 : 50),
        score: sentiment.score,
        reason: `上市公司公告事件；${sentiment.reason}`
      };
    }).filter((item: AnyRecord) => item.title);
  } catch {
    return [];
  }
}

async function fetchGubaOpinions(code: string) {
  try {
    const response = await fetch(`https://guba.eastmoney.com/list,${code}.html`, { headers: { "User-Agent": "Mozilla/5.0" } });
    const html = await response.text();
    const items: AnyRecord[] = [];
    const regex = /<a[^>]+href="(\/news,[^"]+\.html)"[^>]*>([\s\S]*?)<\/a>/g;
    for (const match of html.matchAll(regex)) {
      const title = cleanText(match[2]);
      if (!title || items.some((item) => item.view === title)) continue;
      const sentiment = sentimentFromText(title);
      items.push({
        name: `东方财富股吧用户-${items.length + 1}`,
        type: sentiment.sentiment,
        view: title,
        detail: `公开社区帖子标题解析：${sentiment.reason}`,
        influence: sentiment.sentiment === "neutral" ? 4 : 6,
        publishTime: new Date().toISOString(),
        url: `https://guba.eastmoney.com${match[1]}`,
        score: sentiment.score
      });
      if (items.length >= 6) break;
    }
    return items;
  } catch {
    return [];
  }
}

const WECHAT_DEFAULT_SOURCES = [
  {
    id: "dududigua",
    name: "嘟嘟地瓜",
    source: "微信公众号",
    influence: 8,
    shareLink: "https://mp.weixin.qq.com/s/j_eNtoE8lD1ph3GNkyc16A?scene=1"
  }
];
const WECHAT_WATCH_ACCOUNTS = WECHAT_DEFAULT_SOURCES.map((item) => ({
  name: item.name,
  source: item.source,
  influence: item.influence,
  seedUrl: item.shareLink
}));
const WECHAT_ARTICLES_KEY = "wechat-mp-articles";
const WECHAT_RSS_CONFIG_KEY = "wechat-rss-config";

function normalizeWechatArticles(raw: any): AnyRecord[] {
  const list = Array.isArray(raw) ? raw : Array.isArray(raw?.items) ? raw.items : Array.isArray(raw?.articles) ? raw.articles : [];
  return list.filter((item: AnyRecord) => item && typeof item === "object");
}

function normalizeCodes(value: any) {
  const list = Array.isArray(value) ? value : String(value || "").split(/[,，\s]+/);
  return list.map((item) => String(item || "").trim()).filter(Boolean).slice(0, 20);
}

function articleMatchesStock(article: AnyRecord, code: string, keyword: string) {
  const stockCodes = normalizeCodes(article.stockCodes || article.codes);
  const title = cleanText(article.title || "");
  const content = cleanText(article.content || article.summary || article.digest || "");
  const keywords = normalizeCodes(article.keywords || article.relatedKeywords);
  const haystack = `${title} ${content} ${keywords.join(" ")}`;
  return stockCodes.includes(code) || (!!keyword && haystack.includes(keyword)) || haystack.includes(code);
}

async function storedWechatArticles() {
  return normalizeWechatArticles(await blobStore().get(WECHAT_ARTICLES_KEY, { type: "json" }));
}

async function saveWechatArticle(data: AnyRecord) {
  const title = cleanText(data.title || "");
  const content = cleanText(data.content || data.summary || "");
  const url = String(data.url || "").trim();
  const account = cleanText(data.account || data.accountName || "嘟嘟地瓜");
  if (!title) throw new Error("请填写微信公众号文章标题");
  if (content.length < 20) throw new Error("请粘贴真实文章正文，至少20个字");
  const watched = WECHAT_WATCH_ACCOUNTS.find((item) => account.includes(item.name)) || WECHAT_WATCH_ACCOUNTS[0];
  const item = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    account: watched.name,
    source: watched.source,
    title,
    content,
    url,
    stockCodes: normalizeCodes(data.stockCodes || data.codes),
    keywords: normalizeCodes(data.keywords || data.relatedKeywords),
    publishTime: data.publishTime || new Date().toISOString(),
    importedAt: new Date().toISOString()
  };
  const list = await storedWechatArticles();
  const next = [item, ...list.filter((old) => old.title !== item.title || old.url !== item.url)].slice(0, 200);
  await blobStore().setJSON(WECHAT_ARTICLES_KEY, next);
  return item;
}

function decodeXmlText(value = "") {
  return cleanText(String(value)
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'"));
}

function tagValue(xml: string, tag: string) {
  const escaped = tag.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = xml.match(new RegExp(`<${escaped}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${escaped}>`, "i"));
  return decodeXmlText(match?.[1] || "");
}

function parseRssArticles(xml: string) {
  const blocks = xml.match(/<item[\s\S]*?<\/item>/gi) || xml.match(/<entry[\s\S]*?<\/entry>/gi) || [];
  return blocks.map((block) => {
    const title = tagValue(block, "title");
    const linkFromTag = tagValue(block, "link");
    const atomLink = block.match(/<link[^>]+href=["']([^"']+)["'][^>]*>/i)?.[1] || "";
    const content = tagValue(block, "content:encoded") || tagValue(block, "content") || tagValue(block, "description") || tagValue(block, "summary");
    return {
      account: WECHAT_WATCH_ACCOUNTS[0].name,
      source: "WeWe RSS",
      title,
      content,
      url: linkFromTag || atomLink,
      stockCodes: extractArticleCodes({ title, content }),
      keywords: [],
      publishTime: tagValue(block, "pubDate") || tagValue(block, "published") || tagValue(block, "updated") || new Date().toISOString(),
      importedAt: new Date().toISOString()
    };
  }).filter((item) => item.title && item.content.length >= 20);
}

function normalizeWeweFeedUrl(feedUrl: string) {
  const url = new URL(feedUrl);
  url.pathname = url.pathname.replace(/\.(rss|atom)$/i, ".json");
  if (!/\.(json)$/i.test(url.pathname)) url.pathname = `${url.pathname.replace(/\/$/, "")}.json`;
  url.searchParams.set("mode", "fulltext");
  url.searchParams.set("update", "true");
  url.searchParams.set("limit", url.searchParams.get("limit") || "50");
  return url.toString();
}

function parseWeweJsonFeed(raw: any) {
  const items = Array.isArray(raw?.items) ? raw.items : Array.isArray(raw) ? raw : [];
  return items.map((item: AnyRecord) => {
    const title = cleanText(item.title || "");
    const content = cleanText(item.content_html || item.content_text || item.summary || item.description || "");
    return {
      account: WECHAT_WATCH_ACCOUNTS[0].name,
      source: "cooderl/wewe-rss",
      title,
      content,
      url: item.url || item.external_url || item.id || "",
      stockCodes: extractArticleCodes({ title, content }),
      keywords: [],
      publishTime: item.date_published || item.date_modified || new Date().toISOString(),
      importedAt: new Date().toISOString()
    };
  }).filter((item) => item.title && item.content.length >= 20);
}

function normalizeWechatSource(source: AnyRecord = {}, index = 0) {
  const fallback = WECHAT_DEFAULT_SOURCES[index] || {};
  const name = cleanText(source.name || source.account || fallback.name || `公众号${index + 1}`);
  const feedUrl = String(source.feedUrl || "").trim();
  const shareLink = String(source.shareLink || source.seedUrl || fallback.shareLink || "").trim();
  return {
    id: String(source.id || source.feedId || fallback.id || name || `wechat-${index + 1}`).replace(/[^\w-]/g, "-"),
    name,
    source: cleanText(source.source || fallback.source || "微信公众号"),
    influence: Number(source.influence || fallback.influence || 6),
    shareLink,
    feedId: String(source.feedId || "").trim(),
    feedUrl,
    enabled: source.enabled !== false,
    status: cleanText(source.status || (feedUrl ? "已订阅" : "待订阅")),
    lastSyncedAt: source.lastSyncedAt || "",
    lastError: cleanText(source.lastError || ""),
    updatedAt: source.updatedAt || ""
  };
}

function normalizeWechatSources(saved: AnyRecord) {
  const legacy = saved.feedUrl || saved.feedId || saved.shareLink || saved.account
    ? [{
        id: saved.feedId || "dududigua",
        name: saved.account || WECHAT_DEFAULT_SOURCES[0].name,
        source: "微信公众号",
        influence: WECHAT_DEFAULT_SOURCES[0].influence,
        shareLink: saved.shareLink || currentEnv.DUDUDIGUA_SHARE_LINK || WECHAT_DEFAULT_SOURCES[0].shareLink,
        feedId: saved.feedId || "",
        feedUrl: saved.feedUrl || "",
        enabled: true,
        status: saved.feedUrl ? "已订阅" : "待订阅",
        lastSyncedAt: saved.lastSyncAt || saved.updatedAt || "",
        lastError: ""
      }]
    : [];
  const rawSources = Array.isArray(saved.sources) && saved.sources.length ? saved.sources : legacy;
  const sources = rawSources.length ? rawSources : WECHAT_DEFAULT_SOURCES.map((item) => ({
    ...item,
    shareLink: item.id === "dududigua" ? currentEnv.DUDUDIGUA_SHARE_LINK || item.shareLink : item.shareLink
  }));
  return sources.map(normalizeWechatSource);
}

async function wechatRssConfig() {
  const saved = await blobStore().get(WECHAT_RSS_CONFIG_KEY, { type: "json" }) || {};
  const sources = normalizeWechatSources(saved);
  const first = sources[0] || normalizeWechatSource(WECHAT_DEFAULT_SOURCES[0]);
  return {
    baseUrl: String(saved.baseUrl || currentEnv.WEWE_RSS_BASE_URL || "http://127.0.0.1:4000").trim().replace(/\/$/, ""),
    authCode: String(saved.authCode || currentEnv.WEWE_RSS_AUTH_CODE || "123567").trim(),
    syncIntervalMinutes: Math.max(1, Number(saved.syncIntervalMinutes || currentEnv.WECHAT_SYNC_INTERVAL_MINUTES || 5)),
    sources,
    feedUrl: first.feedUrl || "",
    account: first.name || WECHAT_DEFAULT_SOURCES[0].name,
    shareLink: first.shareLink || currentEnv.DUDUDIGUA_SHARE_LINK || WECHAT_DEFAULT_SOURCES[0].shareLink,
    feedId: first.feedId || "",
    lastAutoSyncAt: saved.lastAutoSyncAt || "",
    lastSyncAt: saved.lastSyncAt || "",
    lastSyncStatus: saved.lastSyncStatus || "",
    lastSyncError: saved.lastSyncError || "",
    updatedAt: saved.updatedAt || ""
  };
}

async function saveWechatRssConfig(data: AnyRecord) {
  const previous = await wechatRssConfig();
  const baseUrl = String(data.baseUrl || previous.baseUrl || currentEnv.WEWE_RSS_BASE_URL || "http://127.0.0.1:4000").trim().replace(/\/$/, "");
  const authCode = String(data.authCode || previous.authCode || currentEnv.WEWE_RSS_AUTH_CODE || "123567").trim();
  const rawSources = Array.isArray(data.sources) ? data.sources : data.source ? [data.source] : previous.sources;
  const sources = rawSources.map((source: AnyRecord, index: number) => normalizeWechatSource({
    ...source,
    shareLink: source.shareLink || (index === 0 ? data.shareLink : ""),
    feedUrl: source.feedUrl || (index === 0 ? data.feedUrl : ""),
    feedId: source.feedId || (index === 0 ? data.feedId : "")
  }, index));
  for (const source of sources) {
    if (source.feedUrl && !/^https?:\/\//i.test(source.feedUrl)) throw new Error(`请填写正确的 WeWe RSS 地址：${source.name}`);
  }
  const config = {
    ...previous,
    baseUrl,
    authCode,
    syncIntervalMinutes: Math.max(1, Number(data.syncIntervalMinutes || previous.syncIntervalMinutes || 5)),
    sources,
    feedUrl: sources[0]?.feedUrl || "",
    account: sources[0]?.name || "",
    shareLink: sources[0]?.shareLink || "",
    feedId: sources[0]?.feedId || "",
    lastAutoSyncAt: data.lastAutoSyncAt || previous.lastAutoSyncAt || "",
    lastSyncAt: data.lastSyncAt || previous.lastSyncAt || "",
    lastSyncStatus: data.lastSyncStatus || previous.lastSyncStatus || "",
    lastSyncError: data.lastSyncError || "",
    updatedAt: new Date().toISOString()
  };
  await blobStore().setJSON(WECHAT_RSS_CONFIG_KEY, config);
  return config;
}

async function callWeweTrpc(baseUrl: string, authCode: string, path: string, input: AnyRecord) {
  const response = await fetch(`${baseUrl.replace(/\/$/, "")}/trpc/${path}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "authorization": authCode
    },
    body: JSON.stringify({ json: input })
  });
  const text = await response.text();
  let payload: any = null;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new Error(`WeWe RSS返回不可解析：${text.slice(0, 120)}`);
  }
  if (!response.ok || payload?.error) {
    throw new Error(payload?.error?.message || `WeWe RSS请求失败：${response.status}`);
  }
  return payload?.result?.data?.json ?? payload?.result?.data ?? payload;
}

function feedUrlFor(baseUrl: string, feedId: string) {
  const url = new URL(`/feeds/${feedId}.json`, baseUrl.replace(/\/$/, ""));
  url.searchParams.set("mode", "fulltext");
  url.searchParams.set("update", "true");
  url.searchParams.set("limit", "50");
  return url.toString();
}

async function subscribeWechatSource(data: AnyRecord = {}) {
  const previous = await wechatRssConfig();
  const requestedId = String(data.id || data.sourceId || "").trim();
  const requestedName = cleanText(data.name || data.account || "");
  const requestedShareLink = String(data.shareLink || data.seedUrl || "").trim();
  const foundIndex = requestedId
    ? previous.sources.findIndex((item: AnyRecord) => item.id === requestedId)
    : previous.sources.findIndex((item: AnyRecord) => {
        const sameLink = requestedShareLink && item.shareLink === requestedShareLink;
        const sameName = requestedName && item.name === requestedName;
        return sameLink || sameName;
      });
  const targetIndex = foundIndex >= 0 ? foundIndex : previous.sources.length;
  const sourceId = requestedId || previous.sources[targetIndex]?.id || `wechat-${Date.now()}`;
  const sources = [...previous.sources];
  const target = normalizeWechatSource({
    ...(sources[targetIndex] || WECHAT_DEFAULT_SOURCES[targetIndex] || {}),
    ...data,
    id: sourceId
  }, targetIndex);
  if (!target.shareLink || !/^https:\/\/mp\.weixin\.qq\.com\/s\//i.test(target.shareLink)) {
    throw new Error(`需要“${target.name}”任意一篇文章的微信分享链接，格式必须是 https://mp.weixin.qq.com/s/...。`);
  }
  const mpList = await callWeweTrpc(previous.baseUrl, previous.authCode, "platform.getMpInfo", { wxsLink: target.shareLink });
  const item = Array.isArray(mpList) ? mpList[0] : mpList;
  if (!item?.id) throw new Error(`WeWe RSS没有识别到公众号，请确认分享链接来自“${target.name}”公众号文章。`);
  await callWeweTrpc(previous.baseUrl, previous.authCode, "feed.add", {
    id: item.id,
    mpName: item.name || target.name,
    mpCover: item.cover || "",
    mpIntro: item.intro || "",
    updateTime: Number(item.updateTime || Math.floor(Date.now() / 1000)),
    status: 1
  });
  await callWeweTrpc(previous.baseUrl, previous.authCode, "feed.refreshArticles", { mpId: item.id });
  sources[targetIndex] = {
    ...target,
    feedId: item.id,
    name: item.name || target.name,
    feedUrl: feedUrlFor(previous.baseUrl, item.id),
    status: "已订阅",
    lastError: "",
    updatedAt: new Date().toISOString()
  };
  const next = await saveWechatRssConfig({ ...previous, sources });
  return {
    feed: item,
    source: sources[targetIndex],
    config: next
  };
}

async function syncWechatRss(options: AnyRecord = {}) {
  const config = await wechatRssConfig();
  const enabledSources = config.sources.filter((source: AnyRecord) => source.enabled);
  if (!enabledSources.length) throw new Error("请先添加微信公众号订阅源");
  const syncedSources: AnyRecord[] = [];
  const failedSources: AnyRecord[] = [];
  let allArticles: AnyRecord[] = [];
  const nextSources = [...config.sources];
  for (const source of enabledSources) {
    const index = nextSources.findIndex((item: AnyRecord) => item.id === source.id);
    let currentSource = source;
    try {
      if (!currentSource.feedUrl && currentSource.shareLink) {
        const result = await subscribeWechatSource({ ...currentSource, sourceId: currentSource.id });
        currentSource = result.source;
      }
      if (!currentSource.feedUrl) throw new Error(`公众号“${currentSource.name}”还没有 WeWe RSS 订阅地址`);
      const feedUrl = normalizeWeweFeedUrl(currentSource.feedUrl);
      const response = await fetch(feedUrl, {
        headers: {
          "User-Agent": "Mozilla/5.0 lianghua-quant-channel",
          "Accept": "application/feed+json, application/json, application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
        }
      });
      if (!response.ok) throw new Error(`WeWe RSS 同步失败：${response.status}`);
      const text = await response.text();
      let rssArticles: AnyRecord[] = [];
      try {
        rssArticles = parseWeweJsonFeed(JSON.parse(text));
      } catch {
        rssArticles = parseRssArticles(text);
      }
      rssArticles = rssArticles.map((item) => ({
        ...item,
        account: currentSource.name || item.account,
        source: currentSource.source || item.source,
        channelSourceId: currentSource.id
      }));
      if (!rssArticles.length) throw new Error("WeWe RSS 没有返回可识别的文章正文，请确认 FEED_MODE=fulltext 或使用 ?mode=fulltext");
      allArticles = [...allArticles, ...rssArticles];
      const savedSource = {
        ...currentSource,
        status: "同步成功",
        lastSyncedAt: new Date().toISOString(),
        lastError: ""
      };
      if (index >= 0) nextSources[index] = savedSource;
      syncedSources.push({ ...savedSource, imported: rssArticles.length, feedUrl });
    } catch (error: any) {
      const savedSource = {
        ...currentSource,
        status: "同步失败",
        lastError: cleanText(error?.message || "同步失败"),
        lastSyncedAt: currentSource.lastSyncedAt || ""
      };
      if (index >= 0) nextSources[index] = savedSource;
      failedSources.push(savedSource);
    }
  }
  if (!allArticles.length && failedSources.length) {
    const firstError = failedSources[0]?.lastError || "同步失败";
    await saveWechatRssConfig({
      ...config,
      sources: nextSources,
      lastAutoSyncAt: options.auto ? new Date().toISOString() : config.lastAutoSyncAt,
      lastSyncStatus: "同步失败",
      lastSyncError: firstError,
      lastSyncAt: new Date().toISOString()
    });
    throw new Error(firstError);
  }
  const existing = await storedWechatArticles();
  const merged = [...allArticles, ...existing]
    .filter((item, index, arr) => arr.findIndex((other) => (other.url && other.url === item.url) || other.title === item.title) === index)
    .slice(0, 300);
  await blobStore().setJSON(WECHAT_ARTICLES_KEY, merged);
  await saveWechatRssConfig({
    ...config,
    sources: nextSources,
    lastAutoSyncAt: options.auto ? new Date().toISOString() : config.lastAutoSyncAt,
    lastSyncStatus: failedSources.length ? "部分失败" : "同步成功",
    lastSyncError: failedSources.map((item) => `${item.name}：${item.lastError}`).join("；"),
    lastSyncAt: new Date().toISOString()
  });
  return {
    imported: allArticles.length,
    total: merged.length,
    articles: allArticles.slice(0, 20),
    sources: syncedSources,
    failedSources,
    syncedAt: new Date().toISOString(),
    status: failedSources.length ? "部分失败" : "同步成功"
  };
}

async function autoSyncWechatIfDue() {
  const config = await wechatRssConfig();
  const last = config.lastAutoSyncAt || config.lastSyncAt || "";
  const intervalMs = Math.max(1, Number(config.syncIntervalMinutes || 5)) * 60 * 1000;
  if (last && Date.now() - new Date(last).getTime() < intervalMs) return { skipped: true, reason: "未到自动同步间隔", config };
  try {
    return await syncWechatRss({ auto: true });
  } catch (error: any) {
    await saveWechatRssConfig({
      ...config,
      lastAutoSyncAt: new Date().toISOString(),
      lastSyncStatus: "自动同步失败",
      lastSyncError: cleanText(error?.message || "自动同步失败")
    });
    return { skipped: false, failed: true, error: cleanText(error?.message || "自动同步失败") };
  }
}

async function storedWechatOpinions(code: string, keyword: string) {
  const opinions: AnyRecord[] = [];
  for (const article of await storedWechatArticles()) {
    const account = String(article.account || article.author || article.source || "").trim();
    const watched = WECHAT_WATCH_ACCOUNTS.find((item) => account.includes(item.name));
    if (!articleMatchesStock(article, code, keyword)) continue;
    const title = cleanText(article.title || "");
    const content = cleanText(article.content || article.summary || article.digest || "");
    const sentiment = sentimentFromText(`${title} ${content}`);
    opinions.push({
      name: `${watched?.name || account || "微信公众号"}（微信公众号）`,
      type: sentiment.sentiment,
      view: title,
      detail: `已导入微信公众号真实文章解析：${sentiment.reason}`,
      influence: watched?.influence || 6,
      publishTime: article.publishTime || article.importedAt || new Date().toISOString(),
      source: watched?.source || "微信公众号",
      url: article.url || "",
      score: sentiment.score,
      verified: true
    });
    if (opinions.length >= 5) break;
  }
  return opinions;
}

function pickWords(text: string, words: string[]) {
  return words.filter((word) => text.includes(word)).slice(0, 8);
}

function extractArticleCodes(article: AnyRecord) {
  const text = `${article.title || ""} ${article.content || ""} ${article.keywords || ""}`;
  const fromText = Array.from(new Set((text.match(/\b(?:[036]\d{5}|688\d{3}|110\d{3}|159\d{3}|51\d{4})\b/g) || [])));
  return Array.from(new Set([...normalizeCodes(article.stockCodes || article.codes), ...fromText])).slice(0, 20);
}

function makeWechatLearning(article: AnyRecord) {
  const title = cleanText(article.title || "");
  const content = cleanText(article.content || article.summary || article.digest || "");
  const text = `${title} ${content}`;
  const sentiment = sentimentFromText(text);
  const opportunityWords = pickWords(text, ["低估", "修复", "反转", "增长", "景气", "突破", "回购", "分红", "业绩", "政策", "需求", "扩产", "创新"]);
  const riskWords = pickWords(text, ["高估", "下跌", "亏损", "减持", "监管", "处罚", "退市", "回撤", "泡沫", "衰退", "风险", "暴跌", "利空"]);
  const stockCodes = extractArticleCodes(article);
  const tone = sentiment.sentiment === "bullish" ? "偏多" : sentiment.sentiment === "bearish" ? "偏空" : "中性";
  return {
    id: article.id || `${title}-${article.importedAt || article.publishTime || ""}`,
    channel: "微信公众号",
    account: article.account || WECHAT_WATCH_ACCOUNTS[0].name,
    title,
    url: article.url || "",
    stockCodes,
    keywords: normalizeCodes(article.keywords || article.relatedKeywords),
    sentiment: sentiment.sentiment,
    sentimentText: tone,
    score: sentiment.score,
    reason: sentiment.reason,
    opportunityWords,
    riskWords,
    excerpt: content.slice(0, 180),
    lesson: `从《${title}》学习到：该渠道观点当前${tone}，需要把${opportunityWords[0] || "机会"}和${riskWords[0] || "风险"}同时纳入策略评分。`,
    labImpact: stockCodes.length
      ? `影响标的 ${stockCodes.join("、")}：AI实验室会把该文章作为新闻舆情和大V影响力证据。`
      : "暂未识别到明确代码：AI实验室只把它作为行业/情绪背景，不直接触发买卖。",
    publishTime: article.publishTime || article.importedAt || new Date().toISOString(),
    learnedAt: new Date().toISOString()
  };
}

async function wechatLearningReport() {
  const config = await wechatRssConfig();
  const articles = await storedWechatArticles();
  const lessons = articles.map(makeWechatLearning);
  const relatedCodes = Array.from(new Set(lessons.flatMap((item) => item.stockCodes || []))).slice(0, 30);
  const bullish = lessons.filter((item) => item.sentiment === "bullish").length;
  const bearish = lessons.filter((item) => item.sentiment === "bearish").length;
  const neutral = lessons.length - bullish - bearish;
  return {
    channel: "微信公众号",
    account: config.sources.map((item: AnyRecord) => item.name).join("、"),
    sourceStatus: config.lastSyncStatus || (lessons.length ? "已导入真实文章" : "等待自动同步"),
    total: lessons.length,
    relatedCodes,
    sources: config.sources,
    lastSyncAt: config.lastSyncAt,
    lastAutoSyncAt: config.lastAutoSyncAt,
    lastSyncError: config.lastSyncError,
    summary: lessons.length
      ? `已保存 ${lessons.length} 篇真实文章，覆盖 ${config.sources.length} 个公众号，识别 ${relatedCodes.length} 个关联标的；偏多 ${bullish} 篇，偏空 ${bearish} 篇，中性 ${neutral} 篇。`
      : (config.lastSyncError ? `还没有可学习的真实文章；最近同步失败：${config.lastSyncError}` : "还没有可学习的真实文章，系统会按同步间隔自动尝试读取已订阅公众号。"),
    lessons
  };
}

function wechatUnavailableOpinions() {
  return WECHAT_WATCH_ACCOUNTS.map((account) => ({
    name: `${account.name}（微信公众号）`,
    type: "neutral" as Sentiment,
    view: "已加入嘟嘟地瓜公众号文章源",
    detail: "已记录你提供的微信公众号原文链接。微信服务器要求环境验证，Cloudflare 不能直接抓正文；请配置 WECHAT_MP_FEED_URL 或第三方授权源后才读取真实正文。当前不伪造观点，不参与多空评分。",
    influence: 0,
    publishTime: "未接入",
    source: account.source,
    url: account.seedUrl,
    score: undefined,
    verified: false,
    needsAuth: true
  }));
}

async function fetchWechatMpOpinions(code: string, keyword: string) {
  const stored = await storedWechatOpinions(code, keyword);
  const feedUrl = String(currentEnv.WECHAT_MP_FEED_URL || "").trim();
  if (!feedUrl) return stored.length ? stored : wechatUnavailableOpinions();
  try {
    const url = new URL(feedUrl);
    url.searchParams.set("stockCode", code);
    url.searchParams.set("keyword", keyword);
    const headers: Record<string, string> = { "User-Agent": "Mozilla/5.0" };
    if (currentEnv.WECHAT_MP_FEED_TOKEN) headers.Authorization = `Bearer ${currentEnv.WECHAT_MP_FEED_TOKEN}`;
    const response = await fetch(url.toString(), { headers });
    if (!response.ok) return stored.length ? stored : wechatUnavailableOpinions();
    const articles = normalizeWechatArticles(await response.json());
    const opinions: AnyRecord[] = [];
    for (const article of articles) {
      const account = String(article.account || article.author || article.source || "").trim();
      const watched = WECHAT_WATCH_ACCOUNTS.find((item) => account.includes(item.name) || String(article.accountName || "").includes(item.name));
      if (!watched) continue;
      const title = cleanText(article.title || "");
      const content = cleanText(article.content || article.summary || article.digest || "");
      const haystack = `${title} ${content}`;
      if (!title || (keyword && !haystack.includes(keyword) && !haystack.includes(code))) continue;
      const sentiment = sentimentFromText(haystack);
      opinions.push({
        name: `${watched.name}（微信公众号）`,
        type: sentiment.sentiment,
        view: title,
        detail: `微信公众号真实文章解析：${sentiment.reason}`,
        influence: watched.influence,
        publishTime: article.publishTime || article.date || new Date().toISOString(),
        source: watched.source,
        url: article.url || "",
        score: sentiment.score,
        verified: true
      });
      if (opinions.length >= 5) break;
    }
    if (opinions.length || stored.length) return [...stored, ...opinions].slice(0, 8);
    return WECHAT_WATCH_ACCOUNTS.map((account) => ({
      name: `${account.name}（微信公众号）`,
      type: "neutral" as Sentiment,
      view: `未发现与 ${keyword || code} 直接相关的最新文章`,
      detail: "已连接微信公众号授权源，但本次没有匹配到该股票/基金/金属相关内容；不伪造观点，不参与多空评分。",
      influence: 0,
      publishTime: new Date().toISOString(),
      source: account.source,
      url: account.seedUrl,
      score: undefined,
      verified: true
    }));
  } catch {
    return stored.length ? stored : wechatUnavailableOpinions();
  }
}

async function sentimentBundle(code: string, stockName?: string) {
  const keyword = stockName && stockName !== code ? stockName : code;
  const [eastmoneyNews, sinaNews, announcements, gubaOpinions, wechatOpinions] = await Promise.all([
    fetchEastmoneyNews(code, keyword),
    fetchSinaNews(code, keyword),
    fetchCninfoAnnouncements(code),
    fetchGubaOpinions(code),
    fetchWechatMpOpinions(code, keyword)
  ]);
  const opinions = [...wechatOpinions, ...gubaOpinions];
  const newsItems = [...eastmoneyNews, ...sinaNews, ...announcements]
    .filter((item, index, arr) => arr.findIndex((other) => other.title === item.title) === index)
    .slice(0, 12);
  const newsScore = averageScore(newsItems, 50);
  const announcementScore = averageScore(announcements, 50);
  const scoreOpinions = opinions.filter((item) => Number.isFinite(Number(item.score)));
  const communityScore = averageScore(scoreOpinions.map((item) => ({ score: item.score, sentiment: item.type })), 50);
  const sentimentScore = Math.round(newsScore * 0.45 + announcementScore * 0.2 + communityScore * 0.35);
  const stock = await stockMeta(code);
  const moneyFlow = moneyFlowFromQuote(stock, sentimentScore);
  return {
    newsItems,
    announcements,
    opinions,
    newsScore,
    announcementScore,
    communityScore,
    sentimentScore,
    moneyFlow
  };
}

function signalFor(stock: AnyRecord): { date: string; type: Signal; strength: number; indicator: string; message: string } {
  const type: Signal = stock.changePercent > 1 ? "BUY" : stock.changePercent < -1 ? "SELL" : "HOLD";
  return {
    date: new Date().toISOString().slice(0, 10),
    type,
    strength: Math.min(95, Math.max(45, Math.round(Math.abs(stock.changePercent || 0) * 12 + 55))),
    indicator: "REALTIME_TECH",
    message: `基于真实实时行情：${stock.name || stock.code} 当前涨跌幅 ${stock.changePercent || 0}%，技术信号 ${signalText(type)}`
  };
}

function signalText(signal: Signal) {
  if (signal === "BUY") return "买入";
  if (signal === "SELL") return "卖出";
  return "观望";
}

function signalForV2(stock: AnyRecord): AnyRecord {
  const pct = Number(stock.changePercent || 0);
  const type: Signal = pct > 1 ? "BUY" : pct < -1 ? "SELL" : "HOLD";
  const ma: Signal = pct > 0.6 ? "BUY" : pct < -0.6 ? "SELL" : "HOLD";
  const macd: Signal = pct > 1 ? "BUY" : pct < -1 ? "SELL" : "HOLD";
  const rsi: Signal = pct < -2 ? "BUY" : pct > 2 ? "SELL" : "HOLD";
  const kdj: Signal = pct > 0.8 ? "BUY" : pct < -0.8 ? "SELL" : "HOLD";
  const boll: Signal = pct < -1.5 ? "BUY" : pct > 1.5 ? "SELL" : "HOLD";
  return {
    date: new Date().toISOString().slice(0, 10),
    type,
    signal: type,
    strength: Math.min(5, Math.max(1, Math.round(Math.abs(pct) + 2))),
    indicator: "REALTIME_TECH",
    message: `基于真实实时行情：${stock.name || stock.code} 当前涨跌幅 ${pct.toFixed(2)}%，技术信号 ${signalText(type)}`,
    description: `MA、MACD、RSI、KDJ、BOLL 根据当前真实报价变化生成即时技术面研判。`,
    indicatorSignals: {
      MA: `均线方向：涨跌幅 ${pct.toFixed(2)}%，短周期趋势${ma === "BUY" ? "偏强" : ma === "SELL" ? "转弱" : "震荡"} - ${signalText(ma)}`,
      MACD: `动量研判：价格动能${macd === "BUY" ? "增强" : macd === "SELL" ? "减弱" : "中性"} - ${signalText(macd)}`,
      RSI: `强弱研判：${rsi === "BUY" ? "短线超跌修复" : rsi === "SELL" ? "短线过热回落" : "强弱均衡"} - ${signalText(rsi)}`,
      KDJ: `短线节奏：${kdj === "BUY" ? "金叉倾向" : kdj === "SELL" ? "死叉倾向" : "等待方向"} - ${signalText(kdj)}`,
      BOLL: `布林位置：${boll === "BUY" ? "接近下轨反弹" : boll === "SELL" ? "接近上轨回落" : "中轨附近震荡"} - ${signalText(boll)}`
    }
  };
}

async function account(userId: number) {
  const user = await blobStore().get(`user-id:${userId}`, { type: "json" }) || await ensureUser();
  const savedPositions = await blobStore().get(`positions:${user.id}`, { type: "json" }) || [];
  const positions = savedPositions.length ? savedPositions : labPositionsFromTrades(await labTrades(userId));
  const marketValue = positions.reduce((sum: number, item: AnyRecord) => sum + (item.marketValue || 0), 0);
  const totalProfit = positions.reduce((sum: number, item: AnyRecord) => sum + Number(item.profit || 0), 0);
  return {
    totalAssets: Number(user.availableCash || 0) + marketValue,
    availableCash: Number(user.availableCash || 0),
    marketValue,
    totalProfit,
    totalProfitPercent: marketValue > 0 ? Number((totalProfit / marketValue * 100).toFixed(2)) : 0,
    todayProfit: totalProfit,
    todayProfitPercent: marketValue > 0 ? Number((totalProfit / marketValue * 100).toFixed(2)) : 0,
    positionCount: positions.length
  };
}

function strategySet(signal: Signal, score: number, stock: AnyRecord, bundle: AnyRecord) {
  const current = Number(stock.current || 0);
  const trendStrategy = {
    name: "趋势跟随策略",
    style: "trend",
    signal,
    score,
    expectedReturnScore: signal === "BUY" ? 72 : 48,
    riskScore: signal === "SELL" ? 72 : 55,
    sentimentFitScore: bundle.sentimentScore,
    suggestedPosition: signal === "BUY" ? "20%-30%试探仓位" : "空仓或低仓观察",
    entryRule: "价格重新站上短期均线且成交量温和放大时入场",
    exitRule: "跌破关键均线或综合分低于45分时退出",
    stopLossRule: current ? `跌破 ${Number((current * 0.95).toFixed(2))} 止损` : "以最近低点为止损",
    takeProfitRule: current ? `接近 ${Number((current * 1.08).toFixed(2))} 分批止盈` : "按8%-10%收益分批止盈",
    evaluationRule: "3-5个交易日复盘收益、回撤和舆情变化，自动调整仓位阈值",
    rationale: "结合实时涨跌幅、新闻情绪、公告事件和社区观点形成综合方向"
  };
  const meanReversion = {
    ...trendStrategy,
    name: "低吸反转策略",
    style: "mean-reversion",
    signal: signal === "SELL" ? "HOLD" : signal,
    score: Math.max(45, score - 6),
    entryRule: "急跌后企稳且负面新闻未继续扩散时小仓位试错",
    rationale: "适合震荡行情，重点控制止损和消息面恶化风险"
  };
  const eventDriven = {
    ...trendStrategy,
    name: "事件驱动策略",
    style: "event-driven",
    score: Math.round(score * 0.6 + bundle.announcementScore * 0.4),
    sentimentFitScore: bundle.announcementScore,
    entryRule: "公告事件明确偏多且市场放量确认时入场",
    rationale: "重点跟踪巨潮公告和新闻催化，不依赖虚构舆情"
  };
  const candidates = [trendStrategy, meanReversion, eventDriven];
  const selected = [...candidates].sort((a, b) => b.score - a.score)[0];
  return { candidates, selected };
}

async function analyze(code: string) {
  const stock = await stockMeta(code);
  const signal = signalFor(stock);
  const techScore = signal.type === "BUY" ? 72 : signal.type === "SELL" ? 38 : 55;
  const bundle = await sentimentBundle(code, stock.name);
  const flowScore = bundle.moneyFlow?.netBigOrderAmount > 0 ? 68 : bundle.moneyFlow?.netBigOrderAmount < 0 ? 38 : 50;
  const score = Math.round(techScore * 0.36 + bundle.newsScore * 0.22 + bundle.communityScore * 0.16 + bundle.announcementScore * 0.1 + flowScore * 0.16);
  const finalSignal: Signal = score >= 65 ? "BUY" : score <= 42 ? "SELL" : "HOLD";
  const { candidates, selected } = strategySet(finalSignal, score, stock, bundle);
  const opinionConsensus = consensus(bundle.opinions);
  const current = Number(stock.current || 0);
  return {
    stockCode: code,
    stockName: stock.name || code,
    signal: finalSignal,
    score,
    techScore,
    sentimentScore: bundle.sentimentScore,
    targetPrice: current ? `${Number((current * 0.95).toFixed(2))}-${Number((current * 1.08).toFixed(2))}` : "以真实行情为准",
    analysis: `综合研判采用真实行情、东方财富/新浪公开新闻、巨潮公告、东方财富股吧公开讨论和大单资金流估算。技术分${techScore}，新闻分${bundle.newsScore}，社区影响分${bundle.communityScore}，公告事件分${bundle.announcementScore}，大单方向${bundle.moneyFlow.bigOrderDirection}，预期成交量${bundle.moneyFlow.expectedVolume}，综合分${score}。`,
    modelUsed: "公网量化舆情引擎",
    modelAvailable: true,
    quantDecision: {
      signal: finalSignal,
      confidence: score,
      riskLevel: score >= 70 ? "MEDIUM" : score <= 42 ? "HIGH" : "MEDIUM",
      trendState: signal.type === "BUY" ? "偏强" : signal.type === "SELL" ? "偏弱" : "震荡",
      suggestedPosition: finalSignal === "BUY" ? "轻仓试探，分批确认" : finalSignal === "SELL" ? "降低仓位或回避" : "观望等待确认",
      stopLoss: current ? Number((current * 0.95).toFixed(2)) : 0,
      takeProfit: current ? Number((current * 1.08).toFixed(2)) : 0,
      targetRange: current ? `${Number((current * 0.95).toFixed(2))}-${Number((current * 1.08).toFixed(2))}` : "",
      summary: "技术 + 新闻 + 公告 + 社区舆情综合评判"
    },
    factors: [
      { name: "技术面", score: techScore, direction: signal.type, weight: 45, reason: signal.message },
      { name: "新闻情绪", score: bundle.newsScore, direction: bundle.newsScore >= 58 ? "bullish" : bundle.newsScore <= 42 ? "bearish" : "neutral", weight: 25, reason: `东方财富/新浪新闻 ${bundle.newsItems.length} 条` },
      { name: "大V/社区影响力", score: bundle.communityScore, direction: opinionConsensus.consensus, weight: 20, reason: `东方财富股吧公开讨论 ${bundle.opinions.length} 条；雪球需要授权后接入` },
      { name: "公告事件", score: bundle.announcementScore, direction: bundle.announcementScore >= 58 ? "bullish" : bundle.announcementScore <= 42 ? "bearish" : "neutral", weight: 10, reason: `巨潮公告 ${bundle.announcements.length} 条` },
      { name: "大单资金流", score: flowScore, direction: bundle.moneyFlow.netBigOrderAmount > 0 ? "bullish" : bundle.moneyFlow.netBigOrderAmount < 0 ? "bearish" : "neutral", weight: 16, reason: `${bundle.moneyFlow.bigOrderDirection}，大单买入${bundle.moneyFlow.bigOrderBuyAmount}，大单卖出${bundle.moneyFlow.bigOrderSellAmount}，预期成交量${bundle.moneyFlow.expectedVolume}` }
    ],
    moneyFlow: bundle.moneyFlow,
    scenarios: [
      { name: "放量突破", probability: score >= 65 ? 45 : 25, trigger: "综合分持续高于65且新闻偏多", action: "轻仓跟随并设置止损" },
      { name: "消息转弱", probability: score <= 45 ? 45 : 25, trigger: "公告/社区出现连续负面关键词", action: "降低仓位或等待修复" },
      { name: "震荡整理", probability: 35, trigger: "技术与舆情分歧", action: "等待量价确认" }
    ],
    risks: [
      "社区舆情来自公开股吧标题解析，雪球大V需要账号或接口授权后才能纳入",
      "新闻情绪为关键词和来源权重模型，需结合人工复核重大公告",
      "结果不构成投资建议"
    ],
    actions: finalSignal === "BUY" ? ["观察放量确认", "轻仓试探", "设置5%止损", "跟踪公告和社区情绪变化"] : finalSignal === "SELL" ? ["降低仓位", "回避负面公告扩散", "等待技术修复"] : ["保持观察", "等待新闻或技术面确认"],
    daVOpinions: bundle.opinions,
    daVMajority: {
      ...opinionConsensus,
      summary: bundle.opinions.length
        ? `公开社区讨论多数为${opinionConsensus.consensus === "bullish" ? "偏多" : opinionConsensus.consensus === "bearish" ? "偏空" : "中性"}`
        : "暂无可验证社区观点；雪球大V需授权后接入"
    },
    newsItems: bundle.newsItems,
    candidateStrategies: candidates,
    selectedStrategy: selected,
    evolution: {
      generation: 2,
      status: "online",
      lastLearning: "已纳入公开新闻、公告和社区舆情",
      nextMutation: "接入雪球授权源后提高大V权重精度",
      outcomeJudgement: "按交易记录、新闻变化和回撤持续校准",
      historySamples: bundle.newsItems.length + bundle.opinions.length
    }
  };
}

async function route(req: Request) {
  const url = new URL(req.url);
  const path = url.pathname.replace(/^\/api/, "") || "/";
  const method = req.method.toUpperCase();

  if (path === "/sync/ai-lab/export" && method === "GET") {
    if (!syncAllowed(req)) return send(null, "同步密钥无效", 403);
    const store = blobStore();
    const dirtyUsers = await store.get("ai-lab-dirty-users", { type: "json" }) || [];
    const users = dirtyUsers.length ? dirtyUsers : [1];
    const states = [];
    for (const userId of users) {
      states.push({ userId, state: await labState(Number(userId)) });
    }
    return send({ dirtyUsers, states, exportedAt: new Date().toISOString() });
  }

  if (path === "/sync/ai-lab/import" && method === "POST") {
    if (!syncAllowed(req)) return send(null, "同步密钥无效", 403);
    const data = await jsonBody(req);
    const store = blobStore();
    const states = Array.isArray(data.states) ? data.states : [];
    const iterations = Array.isArray(data.iterations) ? data.iterations : [];
    for (const item of states) {
      if (item?.userId && item?.state) {
        if (!isValidLabStateShape(item.state)) {
          return send({ userId: Number(item.userId) }, "同步状态结构无效，已拒绝写入", 400);
        }
        const userId = Number(item.userId);
        const previous = await labState(userId);
        await store.setJSON(`ai-lab-state:${userId}`, {
          ...mergeLabState(previous, item.state),
          syncedAt: new Date().toISOString()
        });
      }
    }
    for (const item of iterations) {
      if (item?.userId && item?.record) {
        if (item.record.champion != null && !isPlainObject(item.record.champion)) {
          return send({ userId: Number(item.userId) }, "同步迭代冠军结构无效，已拒绝写入", 400);
        }
        if (item.record.experiments != null && !isGoodObjectArray(item.record.experiments)) {
          return send({ userId: Number(item.userId) }, "同步迭代策略结构无效，已拒绝写入", 400);
        }
        const key = `ai-lab-iterations:${Number(item.userId)}`;
        const history = await store.get(key, { type: "json" }) || [];
        history.unshift(item.record);
        await store.setJSON(key, history.slice(0, 200));
      }
    }
    const processed = Array.isArray(data.processedUsers) ? data.processedUsers.map(Number) : states.map((item: AnyRecord) => Number(item.userId));
    const dirtyUsers = await store.get("ai-lab-dirty-users", { type: "json" }) || [];
    await store.setJSON("ai-lab-dirty-users", dirtyUsers.filter((userId: number) => !processed.includes(Number(userId))));
    return send({ importedStates: states.length, importedIterations: iterations.length, processedUsers: processed }, "同步完成");
  }

  if (method === "POST" && path === "/auth/login") {
    const data = await jsonBody(req);
    if (!data.username || !data.password) return send(null, "请输入用户名和密码", 400);
    const user = await ensureUser(data.username, data.password);
    if (user.password !== data.password) return send(null, "密码错误", 401);
    return send({ token: makeToken(user), userId: user.id, username: user.username, nickname: user.nickname, role: user.role, email: user.email, availableCash: user.availableCash, initialCapital: user.initialCapital });
  }

  if (method === "POST" && path === "/auth/register") {
    const data = await jsonBody(req);
    if (!data.username || !data.password) return send(null, "请输入用户名和密码", 400);
    if (await userByName(data.username)) return send(null, "用户已存在", 409);
    const user = await ensureUser(data.username, data.password);
    if (data.email) await saveUser({ ...user, email: String(data.email).trim() });
    return send(null, "注册成功");
  }

  if (method === "GET" && path === "/auth/info") {
    const user = await blobStore().get(`user-id:${userIdFrom(req)}`, { type: "json" }) || await ensureUser();
    return send({ id: user.id, username: user.username, nickname: user.nickname, role: user.role, email: user.email, availableCash: user.availableCash, initialCapital: user.initialCapital });
  }

  if (method === "GET" && path === "/stock/sina/a-stocks") {
    const page = Number(url.searchParams.get("page") || 1);
    const pageSize = Number(url.searchParams.get("pageSize") || 20);
    const pool = filterAStocksByBoard(url.searchParams.get("board") || "");
    return Response.json({ code: 200, message: "成功", data: await fetchSina(pool.slice((page - 1) * pageSize, page * pageSize)), total: pool.length });
  }

  if (method === "GET" && path === "/stock/sina/us-stocks") {
    const page = Number(url.searchParams.get("page") || 1);
    const pageSize = Number(url.searchParams.get("pageSize") || 20);
    return Response.json({ code: 200, message: "成功", data: await fetchSina(US_STOCKS.slice((page - 1) * pageSize, page * pageSize)), total: US_STOCKS.length });
  }

  if (method === "GET" && path === "/stock/sina/indices") return send(await fetchSina(INDICES));
  if (method === "GET" && path === "/stock/sina/realtime") return send(await fetchSina((url.searchParams.get("codes") || "").split(",").filter(Boolean).map(stockPrefix)));
  if (method === "GET" && path === "/stock/search") return send(await searchStocks(url.searchParams.get("keyword") || ""));

  const realtime = path.match(/^\/stock\/realtime\/([^/]+)$/);
  if (method === "GET" && realtime) {
    const list = await fetchSina([stockPrefix(realtime[1])]);
    return list.length ? send(toStockInfo(list[0] as AnyRecord)) : send(null, "未找到该股票", 404);
  }

  if (method === "GET" && path === "/stock/list") {
    const page = Number(url.searchParams.get("page") || 1);
    const pageSize = Number(url.searchParams.get("pageSize") || 20);
    const list = (await fetchSina(A_STOCKS.slice((page - 1) * pageSize, page * pageSize))).map((item) => toStockInfo(item as AnyRecord));
    return send({ list, total: A_STOCKS.length, page, pageSize });
  }

  const kline = path.match(/^\/stock\/kline\/([^/]+)$/);
  if (method === "GET" && kline) return send(await fetchKline(kline[1], url.searchParams.get("period") || "daily"));

  const indicator = path.match(/^\/analysis\/(signal|indicators)\/([^/]+)$/);
  if (method === "GET" && indicator) {
    const code = indicator[2];
    const stock = code.startsWith("hf_") ? await fetchGoldQuote(code) : await stockMeta(code);
    const signal = signalForV2({
      code,
      name: stock.name || stock.productName || code,
      changePercent: stock.changePercent || 0
    });
    return indicator[1] === "signal" ? send(signal) : send([{ name: "REALTIME_TECH", values: [], signals: [signal], indicatorSignals: signal.indicatorSignals }]);
  }
  if (method === "GET" && path.startsWith("/analysis/backtest/")) return send({ startDate: "", endDate: "", initialCapital: 100000, finalCapital: 100000, totalReturn: 0, annualizedReturn: 0, maxDrawdown: 0, sharpeRatio: 0, winRate: 0, totalTrades: 0, trades: [] });

  if (method === "GET" && path === "/ai/news") {
    const code = url.searchParams.get("stockCode") || "";
    const keyword = url.searchParams.get("keyword") || "";
    const category = url.searchParams.get("category") || "";
    if (code) {
      const stock = await stockMeta(code);
      const bundle = await sentimentBundle(code, stock.name);
      const cctv = await fetchCctvNews(stock.name || keyword);
      return send([...cctv, ...bundle.newsItems].filter((item) => !category || item.category === category || item.reason?.includes(category)).slice(0, 20));
    }
    let cctv = await fetchCctvNews(keyword);
    let filtered = cctv.filter((item) => !category || item.category === category);
    if (!filtered.length && (keyword || category)) {
      cctv = await fetchCctvNews("");
      filtered = cctv.filter((item) => !category || item.category === category);
    }
    return send(filtered.slice(0, 20));
  }
  if (method === "GET" && path === "/ai/lab/state") {
    return send(await labState(userIdFrom(req)));
  }
  if (method === "POST" && path === "/ai/lab/state") {
    return send(await saveLabState(userIdFrom(req), await jsonBody(req)), "AI实验室状态已保存");
  }
  if (method === "POST" && path === "/ai/lab/iteration") {
    const userId = userIdFrom(req);
    const data = await jsonBody(req);
    const state = await labState(userId);
    const history = await blobStore().get(`ai-lab-iterations:${userId}`, { type: "json" }) || [];
    const record = {
      id: Date.now(),
      generation: Number(data.generation ?? state.generation ?? 0),
      champion: data.champion || null,
      experiments: data.experiments || [],
      capital: Number(data.capital ?? state.capital ?? 100000),
      intervalMinutes: Number(data.intervalMinutes ?? state.intervalMinutes ?? 5),
      createdAt: new Date().toISOString()
    };
    history.unshift(record);
    await blobStore().setJSON(`ai-lab-iterations:${userId}`, history.slice(0, 200));
    const nextState = await saveLabState(userId, {
      ...data,
      iterationCount: record.generation,
      lastRunAt: record.createdAt
    });
    return send({ state: nextState, record }, "AI实验室迭代已入库");
  }
  if (method === "GET" && path === "/ai/lab/iterations") {
    return send(await labIterations(userIdFrom(req)));
  }
  if (method === "GET" && path === "/ai/lab/alert-settings") {
    return send(await alertSettings(userIdFrom(req)));
  }
  if (method === "POST" && path === "/ai/lab/alert-settings") {
    const data = await jsonBody(req);
    const email = String(data.email || "").trim();
    if (data.emailEnabled && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return send(null, "请先填写正确的邮箱", 400);
    }
    return send(await saveAlertSettings(userIdFrom(req), data), "邮箱告警设置已保存");
  }
  if (method === "GET" && path === "/ai/sector/top-picks") {
    const cached = await blobStore().get("ai-sector-top-picks", { type: "json" });
    const age = cached?.analysisTime ? Date.now() - Date.parse(cached.analysisTime) : Number.POSITIVE_INFINITY;
    return send(cached && age < 5 * 60 * 1000 ? cached : await aiTopSectorPicks());
  }
  if (method === "POST" && path === "/ai/sector/analyze") {
    return send(await aiTopSectorPicks(), "AI板块分析完成");
  }
  if (method === "GET" && path === "/ai/sector/report") {
    return send(await blobStore().get("ai-sector-top-picks", { type: "json" }) || await aiTopSectorPicks());
  }
  if (method === "GET" && path === "/ai/announcements") {
    return send(await fetchCninfoAnnouncements(url.searchParams.get("stockCode") || "600000"));
  }
  if (method === "GET" && path === "/ai/sentiment") {
    const code = url.searchParams.get("stockCode") || "600000";
    const stock = await stockMeta(code);
    return send(await sentimentBundle(code, stock.name));
  }
  if (method === "GET" && path === "/ai/wechat/articles") {
    await autoSyncWechatIfDue();
    return send((await storedWechatArticles()).slice(0, 50));
  }
  if (method === "GET" && path === "/ai/wechat/learning") {
    await autoSyncWechatIfDue();
    return send(await wechatLearningReport());
  }
  if (method === "GET" && path === "/ai/wechat/rss-config") {
    return send(await wechatRssConfig());
  }
  if (method === "POST" && path === "/ai/wechat/rss-config") {
    return send(await saveWechatRssConfig(await jsonBody(req)), "WeWe RSS配置已保存");
  }
  if (method === "POST" && path === "/ai/wechat/subscribe") {
    return send(await subscribeWechatSource(await jsonBody(req)), "公众号订阅完成");
  }
  if (method === "POST" && path === "/ai/wechat/subscribe-dududigua") {
    return send(await subscribeWechatSource({ ...(await jsonBody(req)), sourceId: "dududigua" }), "公众号订阅完成");
  }
  if (method === "POST" && path === "/ai/wechat/sync") {
    return send(await syncWechatRss(), "WeWe RSS同步完成");
  }
  if (method === "POST" && path === "/ai/wechat/articles") {
    return send(await saveWechatArticle(await jsonBody(req)), "微信公众号文章已导入");
  }
  if (method === "POST" && path === "/ai/analyze") {
    const data = await jsonBody(req);
    return send(await analyze(data.stockCode || "600000"));
  }

  if (method === "GET" && path === "/ai/configs") return send(await blobStore().get("ai-configs", { type: "json" }) || [{ id: 1, name: "公网量化舆情引擎", provider: "cloudflare", modelName: "quant-sentiment", enabled: true, createTime: new Date().toISOString() }]);
  if (method === "POST" && path === "/ai/configs") {
    const configs = await blobStore().get("ai-configs", { type: "json" }) || [];
    const item = { id: Date.now(), enabled: true, createTime: new Date().toISOString(), ...(await jsonBody(req)) };
    configs.push(item);
    await blobStore().setJSON("ai-configs", configs);
    return send(item, "配置已保存");
  }
  const aiConfigById = path.match(/^\/ai\/configs\/(\d+)$/);
  if (aiConfigById && method === "PUT") return send(null, "配置已更新");
  if (aiConfigById && method === "DELETE") return send(null, "配置已删除");
  if (path.match(/^\/ai\/configs\/(\d+)\/test$/) && method === "POST") return send({ available: true }, "公网量化舆情引擎可用");

  if (path.startsWith("/broker/huabao")) {
    return send(null, "真实交易功能已删除，系统仅保留模拟交易", 410);
  }

  if (method === "GET" && path === "/trade/account") return send(await account(userIdFrom(req)));
  if (method === "GET" && path === "/trade/positions") {
    const userId = userIdFrom(req);
    const saved = await blobStore().get(`positions:${userId}`, { type: "json" }) || [];
    return send(saved.length ? saved : labPositionsFromTrades(await labTrades(userId)));
  }
  if (method === "GET" && path === "/trade/orders") {
    const userId = userIdFrom(req);
    const saved = await blobStore().get(`orders:${userId}`, { type: "json" }) || [];
    const list = [...saved, ...labOrdersFromTrades(await labTrades(userId))];
    return send({ list, total: list.length });
  }
  if (method === "POST" && (path === "/trade/buy" || path === "/trade/sell")) {
    const userId = userIdFrom(req);
    const data = await jsonBody(req);
    const orders = await blobStore().get(`orders:${userId}`, { type: "json" }) || [];
    orders.unshift({ id: Date.now(), userId, ...data, amount: Number(data.price || 0) * Number(data.quantity || 0), fee: 0, status: "FILLED", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() });
    await blobStore().setJSON(`orders:${userId}`, orders);
    return send(null, "交易已记录");
  }
  if (method === "DELETE" && path.startsWith("/trade/order/")) return send(null, "订单已处理");
  if (method === "GET" && path === "/trade/profit-analysis") return send(labProfitAnalysisFromTrades(await labTrades(userIdFrom(req))));
  if (method === "GET" && path === "/trade/profit-records") return send(labProfitRecordsFromTrades(await labTrades(userIdFrom(req))));

  if (method === "GET" && path === "/stock/fund/list") {
    const page = Number(url.searchParams.get("page") || 1);
    const pageSize = Number(url.searchParams.get("pageSize") || 20);
    return send(await fetchFundList(page, pageSize, url.searchParams.get("keyword") || "", url.searchParams.get("fundType") || ""));
  }
  const fundNav = path.match(/^\/stock\/fund\/([^/]+)\/nav$/);
  if (method === "GET" && fundNav) return send(await fetchFundNavPoints(fundNav[1]));
  const fundDetail = path.match(/^\/stock\/fund\/([^/]+)$/);
  if (method === "GET" && fundDetail) {
    const fund = await fetchFundRealtime(fundDetail[1]);
    return fund ? send(fund) : send(null, "基金公开数据暂不可用", 404);
  }
  if (method === "GET" && path === "/stock/gold/products") return send(METALS);
  if (method === "GET" && path === "/stock/gold/latest") return send(await fetchGoldQuote(url.searchParams.get("code") || "hf_GC"));
  if (method === "GET" && path === "/stock/gold/history") return send({ list: [await fetchGoldQuote(url.searchParams.get("code") || "hf_GC")] });

  if (method === "GET" && path === "/stock/sectors") return send((await fetchSectorRows()).map(({ stocks, ...sector }) => sector));
  const sectorDetail = path.match(/^\/stock\/sectors\/([^/]+)$/);
  if (method === "GET" && sectorDetail) {
    const sector = (await fetchSectorRows()).find((item) => item.sectorCode === sectorDetail[1]);
    return sector ? send(sector) : send(null, "板块公开数据暂不可用", 404);
  }
  const sectorStocks = path.match(/^\/stock\/sectors\/([^/]+)\/stocks$/);
  if (method === "GET" && sectorStocks) {
    const sector = (await fetchSectorRows()).find((item) => item.sectorCode === sectorStocks[1]);
    return sector ? send(sector.stocks) : send([], "成功");
  }

  if (path.startsWith("/stock/fund/list")) return send({ list: [], total: 0, page: 1, pageSize: 20 });
  if (path.startsWith("/stock/gold/products")) return send({ hf_GC: "COMEX黄金", hf_XAU: "伦敦金" });
  if (path.startsWith("/stock/gold/")) return send(null, "黄金公网数据源暂未接入", 503);
  if (path.startsWith("/recharge/")) return send(null, "充值功能已删除", 410);
  if (path.startsWith("/stock/sectors")) return send([]);

  return send(null, `接口不存在：${path}`, 404);
}

async function handleApi(req: Request, env: PagesEnv = {}) {
  currentEnv = env || {};
  try {
    return await route(req);
  } catch (error: any) {
    return send(null, error?.message || "服务异常", 500);
  }
}

export const onRequest = async ({ request, env }: PagesContext) => {
  return handleApi(request, env);
};
