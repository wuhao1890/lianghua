<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="brand" @click="router.push('/dashboard')">
        <el-icon :size="26"><TrendCharts /></el-icon>
        <span>量化交易</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        mode="horizontal"
        router
        class="top-menu"
        :ellipsis="false"
        menu-trigger="click"
      >
        <el-menu-item index="/home">
            <el-icon><TrendCharts /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <el-sub-menu index="stock-group">
          <template #title>
            <el-icon><TrendCharts /></el-icon>
            <span>股票</span>
          </template>
          <el-menu-item index="/market">股票总览</el-menu-item>
          <el-menu-item index="/a-stocks">A 股</el-menu-item>
          <el-menu-item index="/us-stocks">美股</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="asset-group">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>资产</span>
          </template>
          <el-menu-item index="/funds">基金</el-menu-item>
          <el-menu-item index="/gold">金属</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/news">
          <el-icon><Document /></el-icon>
          <template #title>新闻</template>
        </el-menu-item>

        <el-menu-item index="/ai-lab">
          <el-icon><Monitor /></el-icon>
          <template #title>智能实验室</template>
        </el-menu-item>

        <el-sub-menu index="trade-center">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>交易</span>
          </template>
          <el-menu-item index="/trade">
            <el-icon><Money /></el-icon>
            交易下单
          </el-menu-item>
          <el-menu-item index="/position">
            <el-icon><Wallet /></el-icon>
            持仓管理
          </el-menu-item>
          <el-menu-item index="/history">
            <el-icon><List /></el-icon>
            交易记录
          </el-menu-item>
          <el-menu-item index="/analysis">
            <el-icon><DataAnalysis /></el-icon>
            收益分析
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="user-center">
          <template #title>
            <el-icon><UserFilled /></el-icon>
            <span>用户</span>
          </template>
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            仪表盘
          </el-menu-item>
          <el-menu-item index="/recharge">
            <el-icon><CreditCard /></el-icon>
            充值中心
          </el-menu-item>
          <el-menu-item index="/ai-agent">
            <el-icon><Monitor /></el-icon>
            智能助手
          </el-menu-item>
          <el-menu-item v-if="userStore.isAdmin" index="/admin/recharge">
            <el-icon><Management /></el-icon>
            充值管理
          </el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="header-actions">
        <el-tag size="small" type="success" effect="plain" class="env-tag">量化交易</el-tag>
        <el-dropdown trigger="click" @command="handleCommand">
          <button class="user-button" type="button">
            <el-avatar :size="32" :icon="UserFilled" />
            <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="dashboard">
                <el-icon><Odometer /></el-icon>
                仪表盘
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="layout-main">
      <div class="page-bar">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="route.meta?.title && route.name !== 'Dashboard'">
            {{ route.meta.title }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  CreditCard,
  DataAnalysis,
  Document,
  List,
  Management,
  Money,
  Monitor,
  Odometer,
  SwitchButton,
  TrendCharts,
  UserFilled,
  Wallet
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const stockPaths = ['/market', '/a-stocks', '/us-stocks']
const assetPaths = ['/gold', '/funds']
const tradePaths = ['/trade', '/position', '/history', '/analysis']
const userPaths = ['/dashboard', '/recharge', '/ai-agent', '/admin/recharge']

const activeMenu = computed(() => {
  if (route.path.startsWith('/stock/')) return '/market'
  if (route.path.startsWith('/fund/')) return '/funds'
  if (stockPaths.includes(route.path)) return route.path
  if (assetPaths.includes(route.path)) return route.path
  if (tradePaths.includes(route.path)) return route.path
  if (userPaths.includes(route.path)) return route.path
  return route.path
})

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
</script>

<style scoped lang="scss">
.layout-container {
  min-height: 100vh;
  background: #eef5f0;
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  height: 64px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid #dfeee5;
  box-shadow: 0 4px 18px rgba(20, 66, 43, 0.08);
  backdrop-filter: blur(10px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 132px;
  color: #18965b;
  cursor: pointer;

  span {
    font-size: 20px;
    font-weight: 700;
    white-space: nowrap;
  }
}

.top-menu {
  min-width: 0;
  border-bottom: none;
  background: transparent;

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    height: 64px;
    font-size: 15px;
    border-bottom: 3px solid transparent;
  }

  :deep(.el-menu-item.is-active),
  :deep(.el-sub-menu.is-active .el-sub-menu__title) {
    color: #18965b !important;
    border-bottom-color: #25b26b !important;
    font-weight: 600;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-width: 220px;
}

.env-tag {
  border-color: #25b26b;
  color: #18965b;
  background: rgba(37, 178, 107, 0.06);
}

.user-button {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  border-radius: 8px;
  padding: 5px 8px;
  background: transparent;
  cursor: pointer;

  &:hover {
    background: #f0faf4;
  }
}

.username {
  max-width: 110px;
  overflow: hidden;
  color: #303133;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout-main {
  position: relative;
  min-height: calc(100vh - 64px);
  padding: 18px 24px 24px;
  overflow-y: auto;
}

.layout-main::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: -2;
  background: url('/bg-landscape.jpg') center center / cover no-repeat fixed;
  filter: brightness(1.08) saturate(0.86);
}

.layout-main::after {
  content: '';
  position: fixed;
  inset: 0;
  z-index: -1;
  background: rgba(240, 247, 242, 0.84);
}

.page-bar {
  display: flex;
  align-items: center;
  min-height: 28px;
  margin-bottom: 12px;
}

@media (max-width: 920px) {
  .layout-header {
    grid-template-columns: 1fr auto;
    height: auto;
    padding: 10px 14px 0;
  }

  .top-menu {
    grid-column: 1 / -1;
    order: 3;
    overflow-x: auto;
    white-space: nowrap;

    &::-webkit-scrollbar {
      height: 0;
    }

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      height: 48px;
    }
  }

  .header-actions {
    min-width: 0;
  }

  .env-tag {
    display: none;
  }

  .layout-main {
    min-height: calc(100vh - 112px);
    padding: 14px;
  }
}

@media (max-width: 560px) {
  .brand span {
    font-size: 18px;
  }

  .username {
    display: none;
  }
}
</style>
