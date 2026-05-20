<script setup>
import { ref, onMounted } from 'vue'
import { post } from '../../api/request.js'
import { ElMessage } from 'element-plus'

const activeTab = ref('images')
const resourceList = ref([])
const loading = ref(false)
const uploadDialogVisible = ref(false)
const uploadLoading = ref(false)

const uploadForm = ref({
  name: '',
  type: 'image'
})

const resourceTypeMap = {
  'IMAGE': '图片',
  'VIDEO': '视频',
  'AUDIO': '音频',
  'MODEL': '模型文件'
}

onMounted(async () => {
  await fetchResources()
})

async function fetchResources() {
  loading.value = true
  try {
    const res = await post('/mlm-api/api/resources/list')
    if (res._httpError) {
      ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      resourceList.value = res.data || []
      if (res.status) {
        console.log('[MLM] 资源列表已加载:', res.status)
      }
    } else {
      ElMessage.error((res.message || '获取资源列表失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
  }
}

const imageList = ref([])
const videoList = ref([])
const audioList = ref([])
const modelList = ref([])

const filteredResources = () => {
  const list = resourceList.value
  imageList.value = list.filter(r => r.type === 'IMAGE')
  videoList.value = list.filter(r => r.type === 'VIDEO')
  audioList.value = list.filter(r => r.type === 'AUDIO')
  modelList.value = list.filter(r => r.type === 'MODEL')
}

watch(resourceList, () => filteredResources(), { immediate: true })

import { watch } from 'vue'

function handleUpload() {
  uploadDialogVisible.value = true
}

async function submitUpload() {
  if (!uploadForm.value.name.trim()) {
    ElMessage.warning('请输入资源名称')
    return
  }
  uploadLoading.value = true
  // TODO: 实现实际上传功能
  setTimeout(() => {
    ElMessage.success('上传功能开发中')
    uploadDialogVisible.value = false
    uploadLoading.value = false
  }, 1000)
}

function formatSize(size) {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
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
          <el-button type="primary" :icon="Upload" @click="handleUpload">上传资源</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="resource-tabs">
        <el-tab-pane label="图片" name="images">
          <el-table v-loading="loading" :data="imageList" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column label="预览" width="100">
              <template #default="{ row }">
                <el-image 
                  v-if="row.url" 
                  :src="row.url" 
                  style="width: 60px; height: 60px" 
                  fit="cover"
                  :preview-src-list="[row.url]"
                />
                <span v-else class="no-preview">无预览</span>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column label="大小" width="120">
              <template #default="{ row }">{{ formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="120" />
            <el-table-column prop="createdAt" label="上传时间" width="180" />
            <el-table-column label="操作" width="150">
              <template #default>
                <el-button link type="primary" size="small">复制链接</el-button>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && imageList.length === 0" description="暂无图片资源" />
        </el-tab-pane>
        
        <el-tab-pane label="视频" name="videos">
          <el-table v-loading="loading" :data="videoList" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column label="大小" width="120">
              <template #default="{ row }">{{ formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="120" />
            <el-table-column prop="createdAt" label="上传时间" width="180" />
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button link type="primary" size="small">预览</el-button>
                <el-button link type="primary" size="small">下载</el-button>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && videoList.length === 0" description="暂无视频资源" />
        </el-tab-pane>
        
        <el-tab-pane label="音频" name="audios">
          <el-table v-loading="loading" :data="audioList" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column label="大小" width="120">
              <template #default="{ row }">{{ formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="120" />
            <el-table-column prop="createdAt" label="上传时间" width="180" />
            <el-table-column label="操作" width="150">
              <template #default>
                <el-button link type="primary" size="small">播放</el-button>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && audioList.length === 0" description="暂无音频资源" />
        </el-tab-pane>
        
        <el-tab-pane label="模型文件" name="models">
          <el-table v-loading="loading" :data="modelList" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column label="大小" width="120">
              <template #default="{ row }">{{ formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="120" />
            <el-table-column prop="createdAt" label="上传时间" width="180" />
            <el-table-column label="操作" width="150">
              <template #default>
                <el-button link type="primary" size="small">下载</el-button>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && modelList.length === 0" description="暂无模型文件" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传资源" width="500px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="资源名称" required>
          <el-input v-model="uploadForm.name" placeholder="请输入资源名称" />
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="uploadForm.type" style="width: 100%">
            <el-option label="图片" value="image" />
            <el-option label="视频" value="video" />
            <el-option label="音频" value="audio" />
            <el-option label="模型文件" value="model" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            class="upload-demo"
            drag
            action="#"
            :auto-upload="false"
            multiple
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadLoading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.resource-list {
  max-width: 1400px;
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
.no-preview {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
