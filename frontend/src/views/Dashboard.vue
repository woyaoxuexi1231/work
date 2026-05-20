<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const modules = [
  {
    title: 'MLM Anime Platform',
    description: '动漫流水线管理 — 项目列表、资源库、模型配置',
    tags: ['项目', '流水线', '资源'],
    icon: 'VideoPlay',
    color: '#409EFF',
    bgColor: '#ecf5ff',
    path: '/mlm/projects'
  },
  {
    title: 'Risk Data Hub Lab',
    description: '数据中台同步实验室 — 数据源维护、数据同步',
    tags: ['数据源', 'ETL', '同步'],
    icon: 'DataLine',
    color: '#67c23a',
    bgColor: '#f0f9eb',
    path: '/risk'
  }
]

function goTo(path) {
  router.push(path)
}
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="20" class="module-row">
      <el-col :xs="24" :sm="12" v-for="(module, index) in modules" :key="index">
        <el-card 
          class="module-card" 
          shadow="hover"
          @click="goTo(module.path)"
        >
          <div class="module-content">
            <div class="module-icon" :style="{ background: module.bgColor, color: module.color }">
              <el-icon :size="40"><component :is="module.icon" /></el-icon>
            </div>
            <div class="module-info">
              <h3>{{ module.title }}</h3>
              <p>{{ module.description }}</p>
              <div class="module-tags">
                <el-tag v-for="tag in module.tags" :key="tag" size="small">{{ tag }}</el-tag>
              </div>
            </div>
            <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1200px;
  margin: 0 auto;
}
.module-row {
  margin-top: 20px;
}
.module-card {
  cursor: pointer;
  transition: all 0.3s;
  border-radius: 12px;
  margin-bottom: 20px;
}
.module-card:hover {
  transform: translateY(-4px);
}
.module-content {
  display: flex;
  align-items: center;
  gap: 20px;
}
.module-icon {
  width: 80px;
  height: 80px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.module-info {
  flex: 1;
}
.module-info h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.module-info p {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #909399;
}
.module-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.arrow-icon {
  font-size: 24px;
  color: #c0c4cc;
  transition: color 0.3s;
}
.module-card:hover .arrow-icon {
  color: #409EFF;
}
</style>
