<!--
  MainLayout — Risk Data Hub Lab 主布局
  顶部导航 + 内容区
-->
<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const navItems = [
  { path: '/dashboard', icon: 'HomeFilled', label: '工作台' },
  { path: '/overview', icon: 'DataLine', label: '数据总览' },
  { path: '/datasources', icon: 'Connection', label: '数据源' },
  { path: '/sync', icon: 'Refresh', label: '同步任务' },
  { path: '/init', icon: 'Upload', label: '初始化' }
]

function handleNav(path) { router.push(path) }
async function handleLogout() { await userStore.logout(); router.push('/login') }
</script>

<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="header-left">
        <el-icon size="26" color="#67c23a"><DataLine /></el-icon>
        <span class="logo-text">Risk Hub</span>
      </div>

      <el-menu mode="horizontal" :default-active="activeMenu" class="header-menu" :ellipsis="false">
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path" @click="handleNav(item.path)">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="header-right">
        <el-dropdown @command="handleLogout">
          <span class="user-info">
            <el-icon><UserFilled /></el-icon>
            <span>{{ userStore.userInfo?.username || '管理员' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="layout-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-container { min-height: 100vh; }
.layout-header {
  display: flex; align-items: center; padding: 0 24px;
  background: #fff; border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.header-left { display: flex; align-items: center; gap: 10px; margin-right: 32px; }
.logo-text { font-size: 20px; font-weight: 700; color: #303133; }
.header-menu { flex: 1; border-bottom: none !important; }
.header-right { margin-left: auto; }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #606266; padding: 8px 14px; border-radius: 8px; transition: background 0.25s; }
.user-info:hover { background: #f0f2f5; }
.layout-main { background: var(--risk-bg); padding: 24px; }
</style>
