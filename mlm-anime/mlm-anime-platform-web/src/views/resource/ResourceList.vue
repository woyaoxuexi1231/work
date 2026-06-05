<!--
  ResourceList — 资源库管理页
  按 Tab 分类展示图片/视频/音频/模型文件，支持预览和操作
-->
<script setup>
import { ref, onMounted, watch } from 'vue'
import { listResources } from '@/api/modules/resource'
import { ElMessage } from 'element-plus'

const activeTab = ref('images')
const allResources = ref([])
const loading = ref(false)
const uploadVisible = ref(false)
const uploadForm = ref({ name: '', type: 'IMAGE' })

onMounted(() => fetchAll())

async function fetchAll() {
  loading.value = true
  try {
    const res = await listResources()
    allResources.value = res.data || []
  } catch (e) {
    console.error('[ResourceList] 获取资源失败:', e.message)
  } finally {
    loading.value = false
  }
}

const images = ref([])
const videos = ref([])
const audios = ref([])
const models = ref([])

watch(allResources, (list) => {
  images.value = list.filter(r => r.type === 'IMAGE')
  videos.value = list.filter(r => r.type === 'VIDEO')
  audios.value = list.filter(r => r.type === 'AUDIO')
  models.value = list.filter(r => r.type === 'MODEL')
}, { immediate: true })

function currentList() {
  return { images: images.value, videos: videos.value, audios: audios.value, models: models.value }[activeTab.value] || []
}

function formatSize(s) {
  if (!s) return '-'
  if (s < 1024) return s + ' B'
  if (s < 1048576) return (s / 1024).toFixed(1) + ' KB'
  return (s / 1048576).toFixed(1) + ' MB'
}
</script>

<template>
  <div class="resource-list">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item>资源库</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>资源库</span>
          <el-button type="primary" @click="uploadVisible = true">上传资源</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane v-for="tab in [{ n: 'images', l: '图片' }, { n: 'videos', l: '视频' }, { n: 'audios', l: '音频' }, { n: 'models', l: '模型' }]" :key="tab.n" :label="tab.l" :name="tab.n">
          <el-table :data="currentList()" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="预览" width="90" v-if="tab.n === 'images'">
              <template #default="{ row }">
                <el-image v-if="row.url" :src="row.url" style="width: 50px; height: 50px" fit="cover" :preview-src-list="[row.url]" />
                <span v-else class="no-preview">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column label="大小" width="110">
              <template #default="{ row }">{{ formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="createdAt" label="上传时间" width="170" />
            <el-table-column label="操作" width="150">
              <template #default>
                <el-button link type="primary" size="small">复制链接</el-button>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && !currentList().length" :description="`暂无${tab.l}资源`" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="uploadVisible" title="上传资源" width="520px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="uploadForm.name" placeholder="请输入资源名称" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="uploadForm.type" style="width: 100%">
            <el-option label="图片" value="IMAGE" />
            <el-option label="视频" value="VIDEO" />
            <el-option label="音频" value="AUDIO" />
            <el-option label="模型" value="MODEL" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload drag action="#" :auto-upload="false">
            <el-icon size="40"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件或<em>点击上传</em></div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" @click="ElMessage.info('上传功能开发中'); uploadVisible = false">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.resource-list { max-width: 1400px; margin: 0 auto; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.no-preview { color: #c0c4cc; font-size: 12px; }
</style>
