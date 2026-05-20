<script setup>
import { ref, onMounted } from 'vue'
import { post } from '../../api/request.js'
import { ElMessage } from 'element-plus'

const modelList = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogLoading = ref(false)

const modelForm = ref({
  name: '',
  vendor: 'stable_diffusion',
  type: 'TEXT_TO_IMAGE',
  apiKey: '',
  apiUrl: '',
  enabled: true
})

const vendorOptions = [
  { label: 'Stable Diffusion', value: 'stable_diffusion' },
  { label: 'OpenAI (DALL-E)', value: 'openai' },
  { label: 'Kling (快手可灵)', value: 'kling' }
]

const typeOptions = [
  { label: '文生图 (Text to Image)', value: 'TEXT_TO_IMAGE' },
  { label: '图生视频 (Image to Video)', value: 'IMAGE_TO_VIDEO' },
  { label: '文生视频 (Text to Video)', value: 'TEXT_TO_VIDEO' }
]

onMounted(async () => {
  await fetchModels()
})

async function fetchModels() {
  loading.value = true
  try {
    const res = await post('/mlm-api/api/models/list')
    if (res._httpError) {
      ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      modelList.value = res.data || []
      if (res.status) {
        console.log('[MLM] 模型列表已加载:', res.status)
      }
    } else {
      ElMessage.error((res.message || '获取模型列表失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
  }
}

async function createModel() {
  if (!modelForm.value.name.trim()) {
    ElMessage.warning('请输入模型名称')
    return
  }
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/models/create', {
      name: modelForm.value.name,
      vendor: modelForm.value.vendor,
      type: modelForm.value.type,
      apiKey: modelForm.value.apiKey,
      apiUrl: modelForm.value.apiUrl,
      enabled: modelForm.value.enabled
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '创建成功') + ' [' + res.status + ']')
      dialogVisible.value = false
      modelForm.value = { name: '', vendor: 'stable_diffusion', type: 'TEXT_TO_IMAGE', apiKey: '', apiUrl: '', enabled: true }
      await fetchModels()
    } else {
      ElMessage.error((res.message || '创建失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function toggleEnabled(model) {
  try {
    // TODO: 调用更新接口
    ElMessage.info('更新功能开发中')
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function deleteModel(model) {
  try {
    ElMessage.info('删除功能开发中')
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

const vendorText = (vendor) => {
  const map = {
    'stable_diffusion': 'Stable Diffusion',
    'openai': 'OpenAI',
    'kling': 'Kling'
  }
  return map[vendor] || vendor
}

const typeText = (type) => {
  const map = {
    'TEXT_TO_IMAGE': '文生图',
    'IMAGE_TO_VIDEO': '图生视频',
    'TEXT_TO_VIDEO': '文生视频'
  }
  return map[type] || type
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
          <el-button type="primary" :icon="Plus" @click="dialogVisible = true">添加模型</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="modelList" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="模型" min-width="200">
          <template #default="{ row }">
            <div class="model-info">
              <el-icon size="24" color="#409EFF"><Cpu /></el-icon>
              <div>
                <div class="model-name">{{ row.name }}</div>
                <div class="model-vendor">{{ vendorText(row.vendor) }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apiUrl" label="API地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="toggleEnabled(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" @click="deleteModel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && modelList.length === 0" description="暂无模型配置，点击右上角添加" />
    </el-card>

    <!-- 添加模型对话框 -->
    <el-dialog v-model="dialogVisible" title="添加模型" width="600px">
      <el-form :model="modelForm" label-width="100px">
        <el-form-item label="模型名称" required>
          <el-input v-model="modelForm.name" placeholder="请输入模型名称，如 Stable Diffusion XL" />
        </el-form-item>
        <el-form-item label="供应商" required>
          <el-select v-model="modelForm.vendor" style="width: 100%">
            <el-option v-for="opt in vendorOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型类型" required>
          <el-select v-model="modelForm.type" style="width: 100%">
            <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="API地址">
          <el-input v-model="modelForm.apiUrl" placeholder="可选，指定API endpoint" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="modelForm.apiKey" type="password" placeholder="可选，API密钥" show-password />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="modelForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="createModel">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.model-list {
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
.model-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.model-name {
  font-weight: 600;
  color: #303133;
}
.model-vendor {
  font-size: 12px;
  color: #909399;
}
</style>
