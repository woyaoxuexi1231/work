<!--
  Dashboard — 工作台首页
  展示功能模块入口卡片，点击跳转到对应页面
-->
<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

// 功能模块列表
const modules = [
  {
    title: '数据总览',
    desc: '查看中台系统拓扑、业务表统计和清洗交易记录',
    color: 'text-indigo-600',
    bg: 'bg-indigo-50',
    icon: '◎',
    path: '/overview'
  },
  {
    title: '数据源管理',
    desc: '注册和管理上游交易系统的数据库连接',
    color: 'text-emerald-600',
    bg: 'bg-emerald-50',
    icon: '◆',
    path: '/datasources'
  },
  {
    title: '同步任务',
    desc: '发起数据同步、实时查看任务进度和执行结果',
    color: 'text-amber-600',
    bg: 'bg-amber-50',
    icon: '▶',
    path: '/sync'
  }
]

function goTo(path) {
  router.push(path)
}
</script>

<template>
  <div class="max-w-5xl mx-auto">
    <!-- 欢迎区域 -->
    <div class="mb-10">
      <h2 class="text-2xl font-bold text-slate-800">欢迎使用</h2>
      <p class="mt-2 text-slate-500">
        Risk Data Hub Lab — 多数据源 ETL 同步演示平台
      </p>
    </div>

    <!-- 功能卡片网格 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div
        v-for="m in modules"
        :key="m.path"
        @click="goTo(m.path)"
        class="bg-white rounded-2xl border border-slate-200 p-6 cursor-pointer transition-all duration-200 hover:-translate-y-1 hover:shadow-lg"
      >
        <!-- 图标 -->
        <div
          class="w-14 h-14 rounded-xl flex items-center justify-center text-2xl mb-4"
          :class="m.bg + ' ' + m.color"
        >
          {{ m.icon }}
        </div>

        <!-- 标题 -->
        <h3 class="text-lg font-semibold text-slate-800 mb-2">
          {{ m.title }}
        </h3>

        <!-- 描述 -->
        <p class="text-sm text-slate-500 leading-relaxed">
          {{ m.desc }}
        </p>

        <!-- 箭头 -->
        <div class="mt-4 text-slate-300 text-sm flex items-center gap-1">
          进入 <span class="text-xs">→</span>
        </div>
      </div>
    </div>
  </div>
</template>
