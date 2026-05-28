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
    redirect: '/dashboard',
    children: [
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
        path: 'stock/:code',
        name: 'StockDetail',
        component: () => import('@/views/StockDetail.vue'),
        meta: { title: '股票详情', icon: 'Document', hidden: true }
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
        path: 'ai-agent',
        name: 'AiAgent',
        component: () => import('@/views/AiAgent.vue'),
        meta: { title: 'AI Agent', icon: 'Monitor' }
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
    next({ path: '/dashboard' })
  } else {
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
