<!--
  MainLayout — Risk Data Hub Lab 主布局
  顶部导航菜单，无需登录/登出
-->
<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()
const activeMenu = computed(() => route.path)

const navItems = [
  { path: '/dashboard', icon: 'HomeFilled', label: '工作台' },
  { path: '/overview', icon: 'DataLine', label: '数据总览' },
  { path: '/datasources', icon: 'Connection', label: '数据源' },
  { path: '/sync', icon: 'Refresh', label: '同步任务' },
  { path: '/init', icon: 'Upload', label: '初始化' }
]

function handleNav(path) { router.push(path) }
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
.layout-main { background: var(--risk-bg); padding: 24px; }
</style>
