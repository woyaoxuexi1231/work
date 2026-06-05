<!--
  Dashboard — MLM Anime Platform 工作台
  展示快捷入口卡片，引导用户进入各功能模块
-->
<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const modules = [
  {
    title: '项目管理',
    desc: '创建和管理动漫项目，查看项目进度',
    icon: 'FolderOpened',
    color: '#409EFF',
    bg: '#ecf5ff',
    path: '/projects'
  },
  {
    title: '资源库',
    desc: '管理图片、视频、音频、模型等素材资源',
    icon: 'Files',
    color: '#67c23a',
    bg: '#f0f9eb',
    path: '/resources'
  },
  {
    title: '模型配置',
    desc: '配置 AI 模型供应商与 API 密钥',
    icon: 'Setting',
    color: '#e6a23c',
    bg: '#fdf6ec',
    path: '/models'
  }
]

function goTo(path) {
  router.push(path)
}
</script>

<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>工作台</h2>
      <p class="subtitle">欢迎使用 MLM Anime Platform — AI 驱动的动漫制作流水线</p>
    </div>

    <el-row :gutter="24">
      <el-col :xs="24" :sm="12" :md="8" v-for="m in modules" :key="m.path">
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
.dashboard {
  max-width: 1100px;
  margin: 0 auto;
}
.page-header {
  margin-bottom: 32px;
}
.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}
.subtitle {
  margin: 8px 0 0;
  color: #909399;
  font-size: 14px;
}
.module-card {
  cursor: pointer;
  border-radius: 14px;
  transition: all 0.3s ease;
  margin-bottom: 24px;
}
.module-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}
.module-content {
  display: flex;
  align-items: center;
  gap: 18px;
}
.module-icon {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.module-text h3 {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.module-text p {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}
.arrow {
  color: #c0c4cc;
  margin-left: auto;
  transition: color 0.3s;
}
.module-card:hover .arrow {
  color: #409EFF;
}
</style>
