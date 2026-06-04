import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { getToken, getUser } from '@/utils/storage'

const Layout = () => import('@/views/Layout.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页', icon: 'TrendCharts' }
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'market',
        name: 'StockMarket',
        component: () => import('@/views/StockMarket.vue'),
        meta: { title: '行情中心', icon: 'TrendCharts' }
      },
      {
        path: 'indices',
        name: 'Indices',
        component: () => import('@/views/Indices.vue'),
        meta: { title: '大盘指数', icon: 'DataLine' }
      },
      {
        path: 'sectors',
        name: 'Sectors',
        component: () => import('@/views/Sectors.vue'),
        meta: { title: '板块', icon: 'Grid' }
      },
      {
        path: 'a-stocks',
        name: 'AStocks',
        component: () => import('@/views/AStocks.vue'),
        meta: { title: 'A股行情', icon: 'DataLine' }
      },
      {
        path: 'chinext-stocks',
        name: 'ChinextStocks',
        component: () => import('@/views/AStocks.vue'),
        meta: { title: '创业板', icon: 'DataLine' }
      },
      {
        path: 'star-stocks',
        name: 'StarStocks',
        component: () => import('@/views/AStocks.vue'),
        meta: { title: '科创板', icon: 'DataLine' }
      },
      {
        path: 'us-stocks',
        name: 'UsStocks',
        component: () => import('@/views/UsStocks.vue'),
        meta: { title: '美股行情', icon: 'DataLine' }
      },
      {
        path: 'gold',
        name: 'Gold',
        component: () => import('@/views/Gold.vue'),
        meta: { title: '金属', icon: 'Coin' }
      },
      {
        path: 'funds',
        name: 'Funds',
        component: () => import('@/views/Funds.vue'),
        meta: { title: '基金', icon: 'DataBoard' }
      },
      {
        path: 'news',
        name: 'News',
        component: () => import('@/views/News.vue'),
        meta: { title: '新闻', icon: 'Document' }
      },
      {
        path: 'ai-lab',
        name: 'AiLab',
        component: () => import('@/views/AiLab.vue'),
        meta: { title: '智能实验室', icon: 'Monitor' }
      },
      {
        path: 'ai-lab/growth',
        name: 'AiLabGrowth',
        component: () => import('@/views/AiLab.vue'),
        meta: { title: '实验室成长', icon: 'Monitor' }
      },
      {
        path: 'ai-lab/research',
        name: 'AiLabResearch',
        component: () => import('@/views/AiLab.vue'),
        meta: { title: '实验室研究', icon: 'Monitor' }
      },
      {
        path: 'ai-lab/portfolio',
        name: 'AiLabPortfolio',
        component: () => import('@/views/AiLab.vue'),
        meta: { title: '实验室组合', icon: 'Monitor' }
      },
      {
        path: 'stock/:code',
        name: 'StockDetail',
        component: () => import('@/views/StockDetail.vue'),
        meta: { title: '股票详情', icon: 'Document', hidden: true }
      },
      {
        path: 'fund/:code',
        name: 'FundDetail',
        component: () => import('@/views/FundDetail.vue'),
        meta: { title: '基金详情', hidden: true }
      },
      {
        path: 'trade',
        name: 'Trade',
        component: () => import('@/views/Trade.vue'),
        meta: { title: '交易', icon: 'Money' }
      },
      {
        path: 'position',
        name: 'Position',
        component: () => import('@/views/Position.vue'),
        meta: { title: '持仓管理', icon: 'Wallet' }
      },
      {
        path: 'history',
        name: 'TradeHistory',
        component: () => import('@/views/TradeHistory.vue'),
        meta: { title: '交易记录', icon: 'List' }
      },
      {
        path: 'analysis',
        name: 'Analysis',
        component: () => import('@/views/Analysis.vue'),
        meta: { title: '收益分析', icon: 'DataAnalysis' }
      },
      {
        path: 'recharge',
        name: 'Recharge',
        component: () => import('@/views/Recharge.vue'),
        meta: { title: '充值中心', icon: 'CreditCard' }
      },
      {
        path: 'alert-settings',
        name: 'AlertSettings',
        component: () => import('@/views/AlertSettings.vue'),
        meta: { title: '邮箱告警', icon: 'Message' }
      },
      {
        path: 'ai-agent',
        name: 'AiAgent',
        component: () => import('@/views/AiAgent.vue'),
        meta: { title: '智能助手', icon: 'Monitor' }
      },
      {
        path: 'admin/recharge',
        name: 'AdminRecharge',
        component: () => import('@/views/AdminRecharge.vue'),
        meta: { title: '充值管理', icon: 'Management', admin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  NProgress.start()
  document.title = `${to.meta.title || '量化交易'} - 量化交易系统`

  const token = getToken()

  // 检查管理员权限
  if (to.meta.admin) {
    const user = getUser()
    const isAdmin = user?.role === 'ADMIN'
    if (!isAdmin) {
      next({ path: '/dashboard' })
      NProgress.done()
      return
    }
  }

  if (to.path !== '/login' && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && token) {
    next({ path: '/home' })
  } else {
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
