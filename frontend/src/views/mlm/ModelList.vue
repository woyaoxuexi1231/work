<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const modelList = ref([
  { id: 1, name: 'Stable Diffusion XL', type: '文生图', status: 'enabled', description: '高性能文生图模型' },
  { id: 2, name: 'ControlNet', type: '姿态控制', status: 'enabled', description: '精确姿态控制模型' },
  { id: 3, name: 'LoRA Trainer', type: '微调训练', status: 'disabled', description: '自定义角色训练' }
])

const dialogVisible = ref(false)
const modelForm = ref({
  name: '',
  type: '',
  description: ''
})

function handleEdit(row) {
  ElMessage.info('编辑功能开发中')
}

function handleDelete(row) {
  ElMessage.info('删除功能开发中')
}
</script>

<template>
  <div class="model-list">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>MLM Anime</el-breadcrumb-item>
      <el-breadcrumb-item>模型配置</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>AI 模型配置</span>
          <el-button type="primary" :icon="Plus" size="small">添加模型</el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :xs="24" :sm="12" :md="8" v-for="model in modelList" :key="model.id">
          <el-card class="model-card" shadow="hover">
            <div class="model-header">
              <el-icon size="24" color="#409EFF"><Cpu /></el-icon>
              <el-tag :type="model.status === 'enabled' ? 'success' : 'info'" size="small">
                {{ model.status === 'enabled' ? '已启用' : '已禁用' }}
              </el-tag>
            </div>
            <h3 class="model-name">{{ model.name }}</h3>
            <el-tag type="warning" size="small" class="model-type">{{ model.type }}</el-tag>
            <p class="model-desc">{{ model.description }}</p>
            <div class="model-actions">
              <el-button type="primary" size="small" link @click="handleEdit(model)">配置</el-button>
              <el-button type="danger" size="small" link @click="handleDelete(model)">删除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-if="modelList.length === 0" description="暂无模型配置" />
    </el-card>
  </div>
</template>

<style scoped>
.model-list {
  max-width: 1200px;
  margin: 0 auto;
}
.content-card {
  margin-top: 16px;
  border-radius: 12px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.model-card {
  margin-bottom: 20px;
  border-radius: 12px;
  transition: all 0.3s;
}
.model-card:hover {
  transform: translateY(-4px);
}
.model-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.model-name {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.model-type {
  margin-bottom: 8px;
}
.model-desc {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #909399;
}
.model-actions {
  display: flex;
  gap: 12px;
}
</style>
