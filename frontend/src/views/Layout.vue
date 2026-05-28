<template>
  <el-container class="layout-container">
    <!-- 左侧菜单 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="aside-bg"></div>
      <div class="aside-overlay"></div>
      <div class="logo-container">
        <el-icon :size="26" color="#33d685"><TrendCharts /></el-icon>
        <span v-show="!isCollapse" class="logo-text">量化交易</span>
      </div>
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
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>

        <!-- 股票子菜单 -->
        <el-sub-menu index="stocks">
          <template #title>
            <el-icon><TrendCharts /></el-icon>
            <span>股票</span>
          </template>
          <el-menu-item index="/market">行情中心</el-menu-item>
          <el-menu-item index="/indices">大盘指数</el-menu-item>
          <el-menu-item index="/sectors">板块</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/a-stocks">
          <el-icon><DataLine /></el-icon>
          <template #title>A股行情</template>
        </el-menu-item>
        <el-menu-item index="/us-stocks">
          <el-icon><DataLine /></el-icon>
          <template #title>美股行情</template>
        </el-menu-item>
        <el-menu-item index="/gold">
          <el-icon><Coin /></el-icon>
          <template #title>黄金</template>
        </el-menu-item>
        <el-menu-item index="/funds">
          <el-icon><DataBoard /></el-icon>
          <template #title>基金</template>
        </el-menu-item>
        <el-menu-item index="/trade">
          <el-icon><Money /></el-icon>
          <template #title>交易</template>
        </el-menu-item>
        <el-menu-item index="/position">
          <el-icon><Wallet /></el-icon>
          <template #title>持仓管理</template>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><List /></el-icon>
          <template #title>交易记录</template>
        </el-menu-item>
        <el-menu-item index="/analysis">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>收益分析</template>
        </el-menu-item>
        <el-menu-item index="/recharge">
          <el-icon><CreditCard /></el-icon>
          <template #title>充值中心</template>
        </el-menu-item>
        <el-menu-item index="/ai-agent">
          <el-icon><Monitor /></el-icon>
          <template #title>AI Agent</template>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/admin/recharge">
          <el-icon><Management /></el-icon>
          <template #title>充值管理</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="main-container" direction="vertical">
      <!-- 顶部导航 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon
            class="collapse-btn"
            :size="20"
            @click="toggleCollapse"
          >
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentRoute.meta?.title && currentRoute.name !== 'Dashboard'">
              {{ currentRoute.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <el-tag size="small" type="success" effect="plain" class="env-tag">量化交易</el-tag>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" icon="UserFilled" />
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

      <!-- 主内容区 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Monitor, Grid, Coin, DataBoard } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

const activeMenu = computed(() => {
  return route.path
})

const currentRoute = computed(() => route)

function toggleCollapse() {
  isCollapse.value = !isCollapse.value
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
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
}

.layout-aside {
  background: #1a3a2a;
  transition: width 0.3s ease;
  overflow: hidden;
  position: relative;
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

.logo-container {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  gap: 10px;
  border-bottom: 1px solid rgba(51, 214, 133, 0.15);
  background: linear-gradient(180deg, rgba(26,58,42,0.85), rgba(30,66,48,0.85));
}

.logo-text {
  color: #33d685;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: 2px;
}

.aside-menu {
  position: relative;
  z-index: 2;
  border-right: none;
  height: calc(100vh - 60px);
  overflow-y: auto;
  padding-top: 4px;

  &::-webkit-scrollbar {
    width: 0;
  }

  :deep(.el-menu-item) {
    height: 48px;
    line-height: 48px;
    margin: 2px 8px;
    border-radius: 6px;
    width: calc(100% - 16px);

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

  :deep(.el-sub-menu) {
    .el-sub-menu__title {
      height: 48px;
      line-height: 48px;
      margin: 2px 8px;
      border-radius: 6px;
      width: calc(100% - 16px);
      color: #b8dcc6;

      &:hover {
        background-color: rgba(51, 214, 133, 0.1) !important;
        color: #6aed9a !important;
      }

      .el-icon {
        color: #b8dcc6;
      }
    }

    .el-menu {
      background-color: transparent;

      .el-menu-item {
        padding-left: 56px !important;
        font-size: 13px;
        height: 42px;
        line-height: 42px;
        margin: 1px 8px;

        &.is-active {
          background: linear-gradient(135deg, rgba(51, 214, 133, 0.2), rgba(51, 214, 133, 0.08)) !important;
          color: #33d685 !important;
        }
      }
    }

    &.is-opened {
      .el-sub-menu__title {
        color: #33d685;
        .el-icon { color: #33d685; }
      }
    }
  }
}

.main-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e0f0e5;
  box-shadow: 0 1px 4px rgba(51, 214, 133, 0.06);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.env-tag {
  border: 1px solid #33d685;
  color: #33d685;
  background: rgba(51, 214, 133, 0.06);
  font-size: 11px;
  letter-spacing: 1px;
}

.collapse-btn {
  cursor: pointer;
  color: #606266;
  transition: color 0.3s;

  &:hover {
    color: #33d685;
  }
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.3s;

  &:hover {
    background: #f0faf4;
  }
}

.username {
  font-size: 14px;
  color: #303133;
}

.layout-main {
  padding: 20px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  position: relative;
}

.layout-main::before {
  content: '';
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url('/bg-landscape.jpg') center center / cover no-repeat fixed;
  filter: brightness(1.1) saturate(0.8);
  z-index: -1;
}

.layout-main::after {
  content: '';
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(240, 247, 242, 0.82);
  z-index: -1;
}
</style>
