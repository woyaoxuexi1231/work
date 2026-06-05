<!--
  MainLayout — 主布局
  左侧深色导航栏 + 右侧内容区域
-->
<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

// 当前激活的菜单项
const activePath = computed(() => route.path)

// 导航菜单配置
const navItems = [
  { path: '/dashboard',    label: '工作台',     icon: '◉' },
  { path: '/overview',     label: '数据总览',   icon: '◎' },
  { path: '/datasources',  label: '数据源管理', icon: '◆' },
  { path: '/sync',         label: '同步任务',   icon: '▶' }
]

// 切换页面
function goTo(path) {
  router.push(path)
}
</script>

<template>
  <div class="flex h-screen bg-slate-50">
    <!-- ===== 左侧导航栏 ===== -->
    <aside class="w-60 bg-slate-900 text-slate-300 flex flex-col shrink-0">
      <!-- Logo 区域 -->
      <div class="h-16 flex items-center px-6 border-b border-slate-700">
        <span class="text-xl font-bold text-white tracking-wide">Risk Hub</span>
      </div>

      <!-- 导航菜单 -->
      <nav class="flex-1 py-4 px-3 space-y-1">
        <div
          v-for="item in navItems"
          :key="item.path"
          @click="goTo(item.path)"
          class="flex items-center gap-3 px-4 py-2.5 rounded-lg cursor-pointer transition-colors duration-150"
          :class="
            activePath === item.path
              ? 'bg-slate-800 text-white shadow-sm'
              : 'hover:bg-slate-800 hover:text-white'
          "
        >
          <span class="text-lg">{{ item.icon }}</span>
          <span class="text-sm font-medium">{{ item.label }}</span>
        </div>
      </nav>

      <!-- 底部版本信息 -->
      <div class="px-6 py-4 border-t border-slate-700 text-xs text-slate-500">
        数据中台同步实验室 v1.0
      </div>
    </aside>

    <!-- ===== 右侧内容区域 ===== -->
    <main class="flex-1 overflow-auto">
      <!-- 顶部标题栏 -->
      <header class="h-16 bg-white border-b border-slate-200 flex items-center px-8 sticky top-0 z-10">
        <h1 class="text-lg font-semibold text-slate-800">
          {{ navItems.find(n => n.path === activePath)?.label || 'Risk Hub' }}
        </h1>
      </header>

      <!-- 页面内容（子路由渲染位置） -->
      <div class="p-8">
        <router-view />
      </div>
    </main>
  </div>
</template>
