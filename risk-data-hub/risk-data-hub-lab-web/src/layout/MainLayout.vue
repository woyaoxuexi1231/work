<!--
  MainLayout — 主布局

  这个组件是应用的整体"壳子"，所有页面都显示在它里面。
  布局分两块：
    左侧 ← 240px 宽的深色导航栏（固定不动）
    右侧 ← 内容区域（随页面切换而变化）

  工作原理：
  1. 导航栏定义了 4 个菜单项（工作台/数据总览/数据源管理/同步任务）
  2. 点击菜单项 → router.push(path) → Vue Router 切换子路由
  3. <router-view /> 渲染当前路由对应的页面组件

  导航高亮逻辑：
  activePath === item.path 时，菜单项高亮显示。
  通过 route.path 实时获取当前路径。
-->
<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

// ----- Vue Router 实例，用于编程式导航 -----
const router = useRouter()
// ----- 当前路由对象，包含 path 等信息 -----
const route = useRoute()

// 当前激活的菜单项路径（根据当前浏览器地址栏的 path 计算）
const activePath = computed(() => route.path)

// ----- 导航菜单配置 -----
// 数组定义 4 个菜单项。label 是显示文字，icon 是左侧装饰图标，path 是路由路径
const navItems = [
  { path: '/dashboard',    label: '工作台',     icon: '◉' },
  { path: '/overview',     label: '数据总览',   icon: '◎' },
  { path: '/datasources',  label: '数据源管理', icon: '◆' },
  { path: '/sync',         label: '同步任务',   icon: '▶' }
]

// 点击菜单项时跳转到对应路由
function goTo(path) {
  router.push(path)
}
</script>

<template>
  <!-- 整体布局：flex 水平排列，h-screen 占满视口高度 -->
  <div class="flex h-screen bg-slate-50">

    <!-- ============================================================
         左侧导航栏（深色背景，固定 240px 宽度）
         ============================================================ -->
    <aside class="w-60 bg-slate-900 text-slate-300 flex flex-col shrink-0">
      <!-- Logo 区域：显示应用名称 -->
      <div class="h-16 flex items-center px-6 border-b border-slate-700">
        <span class="text-xl font-bold text-white tracking-wide">Risk Hub</span>
      </div>

      <!-- 导航菜单列表 -->
      <nav class="flex-1 py-4 px-3 space-y-1">
        <div
          v-for="item in navItems"
          :key="item.path"
          @click="goTo(item.path)"
          class="flex items-center gap-3 px-4 py-2.5 rounded-lg cursor-pointer transition-colors duration-150"
          :class="
            // 当前激活的菜单项：亮色背景 + 白色文字，其他项 hover 才变亮
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

    <!-- ============================================================
         右侧主内容区域
         ============================================================ -->
    <main class="flex-1 overflow-auto">
      <!-- 顶部标题栏：固定定位，显示当前页面名称 -->
      <header class="h-16 bg-white border-b border-slate-200 flex items-center px-8 sticky top-0 z-10">
        <h1 class="text-lg font-semibold text-slate-800">
          {{ navItems.find(n => n.path === activePath)?.label || route.meta?.title || 'Risk Hub' }}
        </h1>
      </header>

      <!-- 页面内容渲染区：子路由对应的页面组件在这里显示 -->
      <!-- p-8 提供内边距，让页面内容不贴着边框 -->
      <div class="p-8">
        <router-view />
      </div>
    </main>
  </div>
</template>
