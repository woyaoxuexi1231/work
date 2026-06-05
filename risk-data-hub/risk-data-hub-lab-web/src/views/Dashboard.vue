<!--
  Dashboard — Risk Data Hub Lab 工作台
-->
<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const modules = [
  { title: '数据总览', desc: '查看数据中台同步概览、上游拓扑和业务表统计', icon: 'DataLine', color: '#67c23a', bg: '#f0f9eb', path: '/overview' },
  { title: '数据源管理', desc: '注册和管理 MySQL/PostgreSQL/Oracle 数据源', icon: 'Connection', color: '#409EFF', bg: '#ecf5ff', path: '/datasources' },
  { title: '同步任务', desc: '发起数据同步、查看任务进度和结果', icon: 'Refresh', color: '#e6a23c', bg: '#fdf6ec', path: '/sync' },
  { title: '初始化任务', desc: '初始化中台基础数据表结构和种子数据', icon: 'Upload', color: '#f56c6c', bg: '#fef0f0', path: '/init' }
]

function goTo(path) { router.push(path) }
</script>

<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>工作台</h2>
      <p class="subtitle">Risk Data Hub Lab — 数据中台同步实验室</p>
    </div>

    <el-row :gutter="24">
      <el-col :xs="24" :sm="12" v-for="m in modules" :key="m.path">
        <el-card class="module-card" shadow="hover" @click="goTo(m.path)">
          <div class="module-content">
            <div class="module-icon" :style="{ background: m.bg, color: m.color }">
              <el-icon size="36"><component :is="m.icon" /></el-icon>
            </div>
            <div class="module-text">
              <h3>{{ m.title }}</h3>
              <p>{{ m.desc }}</p>
            </div>
            <el-icon class="arrow" size="22"><ArrowRight /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard { max-width: 1100px; margin: 0 auto; }
.page-header { margin-bottom: 32px; }
.page-header h2 { margin: 0; font-size: 24px; font-weight: 700; color: #303133; }
.subtitle { margin: 8px 0 0; color: #909399; font-size: 14px; }
.module-card { cursor: pointer; border-radius: 14px; transition: all 0.3s ease; margin-bottom: 24px; }
.module-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.1); }
.module-content { display: flex; align-items: center; gap: 18px; }
.module-icon { width: 72px; height: 72px; border-radius: 16px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.module-text h3 { margin: 0 0 6px; font-size: 18px; font-weight: 600; color: #303133; }
.module-text p { margin: 0; font-size: 13px; color: #909399; line-height: 1.5; }
.arrow { color: #c0c4cc; margin-left: auto; transition: color 0.3s; }
.module-card:hover .arrow { color: #67c23a; }
</style>
