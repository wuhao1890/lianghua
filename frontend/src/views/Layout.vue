<template>
  <el-container class="layout-container">
    <!-- 顶部导航 -->
    <el-header class="layout-topbar">
      <div class="topbar-left">
        <el-icon :size="24" color="#33d685"><TrendCharts /></el-icon>
        <span class="topbar-logo">量化交易</span>
      </div>
      <div class="topbar-menu">
        <div
          v-for="item in topMenu"
          :key="item.key"
          class="topbar-item"
          :class="{ active: activeTop === item.key }"
          @click="switchTop(item)"
        >
          <el-icon :size="18"><component :is="iconMap[item.icon]" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
      </div>
      <div class="topbar-right">
        <!-- 主题切换 -->
        <el-tooltip :content="isDark ? '切换亮色模式' : '切换深色模式'" placement="bottom">
          <el-button text @click="toggleTheme" style="color:#b8dcc6;margin-right:8px;font-size:18px">
            <el-icon><Moon v-if="!isDark" /><Sunny v-else /></el-icon>
          </el-button>
        </el-tooltip>
        <!-- 预警按钮 -->
        <el-tooltip content="价格预警" placement="bottom">
          <el-button text @click="showAlertDialog = true" style="color:#b8dcc6;margin-right:8px;font-size:18px;position:relative">
            <el-icon><Bell /></el-icon>
            <span v-if="alertCount > 0" class="alert-badge">{{ alertCount }}</span>
          </el-button>
        </el-tooltip>
        <!-- 按Ctrl+K搜索 -->
        <el-tooltip content="Ctrl+K 快速搜索" placement="bottom">
          <el-button text @click="showSearchDialog = true" style="color:#b8dcc6;font-size:18px">
            <el-icon><Search /></el-icon>
          </el-button>
        </el-tooltip>
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-info">
            <el-avatar :size="30" icon="UserFilled" />
            <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="dashboard">
                <el-icon><Odometer /></el-icon>仪表盘
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="main-area">
      <!-- 左侧菜单（非空时才显示） -->
      <el-aside v-if="activeSidebarItems.length > 0" :width="isCollapse ? '64px' : '200px'" class="layout-aside">
        <div class="aside-bg"></div>
        <div class="aside-overlay"></div>
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :collapse-transition="false"
          router
          background-color="transparent"
          text-color="#b8dcc6"
          active-text-color="#33d685"
          class="aside-menu"
        >
          <el-menu-item
            v-for="item in activeSidebarItems"
            :key="item.path"
            :index="item.path"
          >
            <el-icon><component :is="iconMap[item.icon]" /></el-icon>
            <template #title>{{ item.label }}</template>
          </el-menu-item>
        </el-menu>
        <div class="collapse-btn" @click="toggleCollapse">
          <el-icon><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
        </div>
      </el-aside>

      <!-- 主内容 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

    <!-- 价格预警对话框 -->
    <el-dialog v-model="showAlertDialog" title="价格预警" width="500px" top="8vh">
      <div class="alert-list" v-if="alerts.length > 0">
        <div v-for="alert in alerts" :key="alert.id" class="alert-item">
          <div class="alert-info">
            <span class="alert-code">{{ alert.code }}</span>
            <span class="alert-name">{{ alert.name }}</span>
            <span class="alert-direction" :class="alert.direction">
              {{ alert.direction === 'above' ? '>=' : alert.direction === 'below' ? '<=' : '触发' }}
            </span>
            <span class="alert-price">{{ alert.targetPrice }}</span>
            <el-tag :type="alert.enabled ? 'success' : 'info'" size="small">
              {{ alert.enabled ? '已启用' : '已禁用' }}
            </el-tag>
          </div>
          <div class="alert-actions">
            <el-switch :model-value="alert.enabled" @change="toggleAlert(alert.id)" />
            <el-button type="danger" text size="small" @click="removeAlert(alert.id)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无价格预警" />
    </el-dialog>

    <!-- 全局搜索对话框 (Ctrl+K) -->
    <el-dialog v-model="showSearchDialog" title="快速搜索" width="480px" top="15vh" @opened="searchKeyword = ''; globalSearchResults = []">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索股票代码或名称..."
        clearable
        size="large"
        @input="onGlobalSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <div class="search-results" v-if="globalSearchResults.length > 0">
        <div
          v-for="item in globalSearchResults"
          :key="item.code"
          class="search-item"
          @click="goToSearchResult(item)"
        >
          <span class="search-code">{{ item.code }}</span>
          <span class="search-name">{{ item.name }}</span>
          <span class="search-market">{{ item.market || '' }}</span>
        </div>
      </div>
      <div v-else-if="searchKeyword" class="search-empty">
        未找到匹配结果
      </div>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useTheme } from '@/store/theme'
import { useAlertStore } from '@/store/alert'
import wsService from '@/api/wsService'
import {
  TrendCharts, Coin, DataBoard, Monitor, User,
  Fold, Expand, ArrowDown, Odometer, SwitchButton,
  DataLine, Grid, Money, Wallet, List, DataAnalysis,
  CreditCard, Management, Moon, Sunny, Bell, Search, Warning
} from '@element-plus/icons-vue'

// Icon resolver for sidebar
const iconMap: Record<string, any> = {
  TrendCharts, DataLine, Grid, Money, Wallet,
  List, DataAnalysis, CreditCard, Management, Odometer,
  Coin, DataBoard, Monitor, User, Search, Warning, Bell
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)
const { isDark, toggle: toggleTheme, init: initTheme } = useTheme()
const { alerts, remove: removeAlert, toggle: toggleAlert } = useAlertStore()
const showAlertDialog = ref(false)
const showSearchDialog = ref(false)
const searchKeyword = ref('')
const globalSearchResults = ref<any[]>([])

const alertCount = computed(() => alerts.value.filter(a => a.enabled).length)

// Keyboard shortcuts
function handleKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    showSearchDialog.value = true
  }
  if (e.key === 'F11') {
    e.preventDefault()
    document.documentElement.requestFullscreen?.() || document.exitFullscreen?.()
  }
}

onMounted(() => {
  initTheme()
  window.addEventListener('keydown', handleKeydown)
  // 建立WebSocket连接
  wsService.connect()
  // 监听预警推送，弹窗通知
  wsService.on('alert', (data: any) => {
    const alertStore = useAlertStore()
    alertStore.load() // 刷新预警列表
  })
  // 监听价差预警
  wsService.on('spread_alert', (data: any) => {
    const d = data.data || data
    const title = d.alertType === '溢价过高' ? '📈 A+H溢价预警' : '📉 A+H折价预警'
    const msg = `${d.aName}(${d.aCode}) 溢价${d.premium?.toFixed(1)}%`
    ElNotification({ title, message: msg, type: 'warning', duration: 10000, position: 'top-right' })
  })
})
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))

// 顶部菜单定义
const topMenu = [
  { key: 'gold', label: '黄金', icon: 'Coin', path: '/gold' },
  { key: 'fund', label: '基金', icon: 'DataBoard', path: '/funds' },
  { key: 'stock', label: '股票', icon: 'TrendCharts', path: '/market' },
  { key: 'ai', label: 'AI', icon: 'Monitor', path: '/ai-agent' },
  { key: 'user', label: '用户中心', icon: 'User', path: '/dashboard' },
]

// 各模块侧栏菜单
const sidebarMap: Record<string, { label: string; icon: string; path: string }[]> = {
  stock: [
    { label: 'A股', icon: 'TrendCharts', path: '/a-stocks' },
    { label: '美股', icon: 'TrendCharts', path: '/us-stocks' },
    { label: '港股', icon: 'TrendCharts', path: '/hk-stocks' },
    { label: '日股', icon: 'TrendCharts', path: '/jp-stocks' },
    { label: '韩股', icon: 'TrendCharts', path: '/kr-stocks' },
  ],
  user: [
    { label: '仪表盘', icon: 'Odometer', path: '/dashboard' },
    { label: '交易', icon: 'Money', path: '/trade' },
    { label: '持仓管理', icon: 'Wallet', path: '/position' },
    { label: '交易记录', icon: 'List', path: '/history' },
    { label: '收益分析', icon: 'DataAnalysis', path: '/analysis' },
    { label: '策略回测', icon: 'DataAnalysis', path: '/backtest' },
    { label: '策略编辑器', icon: 'Monitor', path: '/strategy-editor' },
    { label: '选股筛选器', icon: 'Search', path: '/screener' },
    { label: '风控设置', icon: 'Warning', path: '/risk' },
    { label: '价格预警', icon: 'Bell', path: '/alerts' },
    { label: '充值中心', icon: 'CreditCard', path: '/recharge' },
    { label: '充值管理', icon: 'Management', path: '/admin/recharge', admin: true },
  ],
  gold: [],
  fund: [],
  ai: [],
}

// 当前选中顶部导航
const activeTop = computed(() => {
  const p = route.path
  if (p.startsWith('/gold')) return 'gold'
  if (p.startsWith('/fund')) return 'fund'
  if (p.startsWith('/ai-agent')) return 'ai'
  if (p.startsWith('/dashboard') || p.startsWith('/trade') || p.startsWith('/position') ||
      p.startsWith('/history') || p.startsWith('/analysis') || p.startsWith('/backtest') ||
      p.startsWith('/strategy-editor') ||
      p.startsWith('/screener') || p.startsWith('/risk') || p.startsWith('/alerts') ||
      p.startsWith('/recharge') || p.startsWith('/admin')) return 'user'
  return 'stock' // default
})

// 当前侧栏菜单项（过滤 admin）
const activeSidebarItems = computed(() => {
  const items = sidebarMap[activeTop.value] || []
  const user = userStore.userInfo
  const isAdmin = user?.role === 'ADMIN'
  return items.filter(item => {
    if ((item as any).admin && !isAdmin) return false
    return true
  })
})

const activeMenu = computed(() => route.path)

function toggleCollapse() {
  isCollapse.value = !isCollapse.value
}

function switchTop(item: typeof topMenu[0]) {
  router.push(item.path)
}

function handleCommand(command: string) {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    }).catch(() => {})
  } else if (command === 'dashboard') {
    router.push('/dashboard')
  }
}

function onGlobalSearch(keyword: string) {
  if (!keyword || keyword.length < 1) {
    globalSearchResults.value = []
    return
  }
  // Simple search across known stock codes
  const kw = keyword.toUpperCase()
  const allStocks = [
    { code: '600000', name: '浦发银行', market: 'A股' },
    { code: '600519', name: '贵州茅台', market: 'A股' },
    { code: '000001', name: '平安银行', market: 'A股' },
    { code: 'AAPL', name: 'Apple', market: '美股' },
    { code: 'MSFT', name: 'Microsoft', market: '美股' },
    { code: 'GOOGL', name: 'Alphabet', market: '美股' },
    { code: 'AMZN', name: 'Amazon', market: '美股' },
    { code: 'TSLA', name: 'Tesla', market: '美股' },
    { code: '00700', name: '腾讯控股', market: '港股' },
    { code: '09988', name: '阿里巴巴', market: '港股' },
    { code: '03690', name: '美团', market: '港股' },
    { code: '9984', name: 'SoftBank Group', market: '日股' },
    { code: '7203', name: 'Toyota Motor', market: '日股' },
    { code: '005930', name: 'Samsung Electronics', market: '韩股' },
    { code: '000660', name: 'SK Hynix', market: '韩股' },
  ]
  globalSearchResults.value = allStocks.filter(s =>
    s.code.includes(kw) || s.name.toUpperCase().includes(kw)
  ).slice(0, 10)
}

function goToSearchResult(item: { code: string; market: string }) {
  showSearchDialog.value = false
  const marketRoutes: Record<string, string> = {
    'A股': '/a-stocks',
    '美股': '/us-stocks',
    '港股': '/hk-stocks',
    '日股': '/jp-stocks',
    '韩股': '/kr-stocks',
  }
  const path = marketRoutes[item.market]
  if (path) {
    router.push(path)
  }
}
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 顶部导航栏 */
.layout-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: linear-gradient(135deg, #1a3a2a, #1e422e);
  border-bottom: 1px solid rgba(51, 214, 133, 0.15);
  z-index: 100;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 140px;
}

.topbar-logo {
  color: #33d685;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 2px;
}

.topbar-menu {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  justify-content: center;
}

.topbar-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 22px;
  border-radius: 8px;
  cursor: pointer;
  color: #b8dcc6;
  font-size: 14px;
  transition: all 0.25s;

  &:hover {
    background: rgba(51, 214, 133, 0.12);
    color: #6aed9a;
  }

  &.active {
    background: linear-gradient(135deg, rgba(51, 214, 133, 0.2), rgba(51, 214, 133, 0.08));
    color: #33d685;
    font-weight: 600;
  }
}

.topbar-right {
  display: flex;
  align-items: center;
  min-width: 140px;
  justify-content: flex-end;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  color: #b8dcc6;

  &:hover {
    background: rgba(51, 214, 133, 0.12);
  }

  .username {
    font-size: 13px;
  }
}

/* 主区域 */
.main-area {
  flex: 1;
  min-height: 0;
  display: flex;
}

/* 侧栏 */
.layout-aside {
  background: #1a3a2a;
  transition: width 0.3s ease;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
}

.aside-bg {
  position: absolute;
  inset: 0;
  background: url('/bg-sidebar.jpg') center center / cover no-repeat;
  opacity: 0.4;
  z-index: 0;
}

.aside-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(26,58,42,0.3) 0%, rgba(26,58,42,0.75) 100%);
  z-index: 1;
}

.aside-menu {
  position: relative;
  z-index: 2;
  border-right: none;
  height: calc(100vh - 56px);
  overflow-y: auto;
  padding-top: 8px;
  background: transparent !important;

  &::-webkit-scrollbar { width: 0; }

  :deep(.el-menu-item) {
    height: 46px;
    line-height: 46px;
    margin: 2px 10px;
    border-radius: 6px;
    width: calc(100% - 20px);

    &.is-active {
      background: linear-gradient(135deg, rgba(51, 214, 133, 0.2), rgba(51, 214, 133, 0.08)) !important;
      border-right: none;
      color: #33d685 !important;
      font-weight: 600;
    }

    &:hover {
      background-color: rgba(51, 214, 133, 0.1) !important;
      color: #6aed9a !important;
    }
  }
}

.collapse-btn {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  cursor: pointer;
  color: #b8dcc6;
  border-top: 1px solid rgba(51, 214, 133, 0.1);

  &:hover {
    color: #33d685;
    background: rgba(51, 214, 133, 0.08);
  }
}

/* 主内容 */
.layout-main {
  padding: 20px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  position: relative;
  background: rgba(240, 247, 242, 0.82);
}

.layout-main::before {
  content: '';
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: url('/bg-landscape.jpg') center center / cover no-repeat fixed;
  filter: brightness(1.1) saturate(0.8);
  z-index: -1;
}

/* 预警徽标 */
.alert-badge {
  position: absolute;
  top: -4px;
  right: -6px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  border-radius: 8px;
  padding: 0 4px;
}

/* 预警对话框 */
.alert-list {
  max-height: 400px;
  overflow-y: auto;
}

.alert-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #ebeef5;

  &:last-child {
    border-bottom: none;
  }
}

.alert-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;

  .alert-code {
    font-weight: 600;
    color: #303133;
  }

  .alert-name {
    color: #606266;
  }

  .alert-direction {
    font-size: 12px;
    padding: 1px 6px;
    border-radius: 3px;
    background: #f5f7fa;

    &.above { color: #f56c6c; }
    &.below { color: #67c23a; }
  }

  .alert-price {
    font-weight: 600;
    color: #409eff;
  }
}

.alert-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 全局搜索对话框 */
.search-results {
  margin-top: 12px;
  max-height: 360px;
  overflow-y: auto;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #f5f7fa;
  }

  .search-code {
    font-weight: 600;
    color: #303133;
    min-width: 80px;
  }

  .search-name {
    flex: 1;
    color: #606266;
  }

  .search-market {
    font-size: 12px;
    color: #909399;
    background: #f5f7fa;
    padding: 2px 8px;
    border-radius: 4px;
  }
}

.search-empty {
  text-align: center;
  padding: 40px 0;
  color: #909399;
  font-size: 14px;
}
</style>
