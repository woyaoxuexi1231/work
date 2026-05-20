<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('images')
const uploading = ref(false)

const imageList = ref([
  { id: 1, name: 'sample_001.jpg', size: '2.3MB', type: 'image/jpeg', uploadTime: '2026-05-20 10:30' },
  { id: 2, name: 'sample_002.png', size: '5.1MB', type: 'image/png', uploadTime: '2026-05-20 11:15' }
])

const videoList = ref([
  { id: 1, name: 'demo_video.mp4', size: '45.2MB', type: 'video/mp4', uploadTime: '2026-05-20 09:00' }
])

function handleUpload() {
  ElMessage.info('上传功能开发中')
}
</script>

<template>
  <div class="resource-list">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>MLM Anime</el-breadcrumb-item>
      <el-breadcrumb-item>资源库</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>资源库</span>
          <el-button type="primary" :icon="Upload" size="small" @click="handleUpload">上传资源</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="resource-tabs">
        <el-tab-pane label="图片" name="images">
          <el-table :data="imageList" stripe style="width: 100%">
            <el-table-column prop="name" label="文件名" min-width="200" />
            <el-table-column prop="size" label="大小" width="120" />
            <el-table-column prop="type" label="类型" width="150" />
            <el-table-column prop="uploadTime" label="上传时间" width="180" />
            <el-table-column label="操作" width="150">
              <template #default>
                <el-button link type="primary" size="small">预览</el-button>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        
        <el-tab-pane label="视频" name="videos">
          <el-table :data="videoList" stripe style="width: 100%">
            <el-table-column prop="name" label="文件名" min-width="200" />
            <el-table-column prop="size" label="大小" width="120" />
            <el-table-column prop="type" label="类型" width="150" />
            <el-table-column prop="uploadTime" label="上传时间" width="180" />
            <el-table-column label="操作" width="150">
              <template #default>
                <el-button link type="primary" size="small">播放</el-button>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        
        <el-tab-pane label="音频" name="audios">
          <el-empty description="暂无音频文件" />
        </el-tab-pane>
        
        <el-tab-pane label="模型" name="models">
          <el-empty description="暂无模型文件" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.resource-list {
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
.resource-tabs {
  margin-top: -10px;
}
</style>
