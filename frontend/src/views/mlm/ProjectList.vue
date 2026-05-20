<script setup>
import { ref, onMounted } from 'vue'
import { post } from '../../api/request.js'
import { ElMessage } from 'element-plus'

const projects = ref([])
const loading = ref(true)
const tableLoading = ref(false)

onMounted(async () => {
  await fetchProjects()
})

async function fetchProjects() {
  tableLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/list')
    if (res.code === 200) {
      projects.value = res.data || []
    } else {
      ElMessage.error(res.message || '获取项目列表失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
    tableLoading.value = false
  }
}
</script>

<template>
  <div class="project-list">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>MLM Anime</el-breadcrumb-item>
      <el-breadcrumb-item>项目列表</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>项目列表</span>
          <el-button type="primary" :icon="Plus" size="small">新建项目</el-button>
        </div>
      </template>
      
      <el-table 
        :data="projects" 
        v-loading="tableLoading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="项目名称" min-width="200">
          <template #default="{ row }">
            <div class="project-name">
              <el-icon color="#409EFF"><FolderOpened /></el-icon>
              <span>{{ row.name || row.title || `项目 #${row.id}` }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="300" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'running' ? 'success' : 'info'" size="small">
              {{ row.status || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default>
            <el-button link type="primary" size="small">编辑</el-button>
            <el-button link type="danger" size="small">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-empty v-if="!loading && projects.length === 0" description="暂无项目" />
    </el-card>
  </div>
</template>

<style scoped>
.project-list {
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
.project-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
