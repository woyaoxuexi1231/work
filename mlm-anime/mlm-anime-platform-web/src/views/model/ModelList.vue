<!--
  ModelList — AI 模型配置页
  管理 AI 模型供应商（SD / OpenAI / Kling），支持增删启用
-->
<script setup>
import { ref, onMounted } from 'vue'
import { listModels, createModel } from '@/api/modules/model'
import { ElMessage } from 'element-plus'

const models = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const modelForm = ref({ name: '', vendor: 'stable_diffusion', type: 'TEXT_TO_IMAGE', apiKey: '', apiUrl: '', enabled: true })

const vendors = [
  { l: 'Stable Diffusion', v: 'stable_diffusion' },
  { l: 'OpenAI (DALL-E)', v: 'openai' },
  { l: 'Kling', v: 'kling' }
]
const types = [
  { l: '文生图', v: 'TEXT_TO_IMAGE' },
  { l: '图生视频', v: 'IMAGE_TO_VIDEO' },
  { l: '文生视频', v: 'TEXT_TO_VIDEO' }
]

onMounted(() => fetchModels())

async function fetchModels() {
  loading.value = true
  try {
    const res = await listModels()
    models.value = res.data || []
  } catch (e) {
    console.error('[ModelList] 获取模型列表失败:', e.message)
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!modelForm.value.name.trim()) { ElMessage.warning('请输入模型名称'); return }
  dialogLoading.value = true
  try {
    await createModel(modelForm.value)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    modelForm.value = { name: '', vendor: 'stable_diffusion', type: 'TEXT_TO_IMAGE', apiKey: '', apiUrl: '', enabled: true }
    await fetchModels()
  } catch (e) {
    console.error('[ModelList] 创建失败:', e.message)
  } finally {
    dialogLoading.value = false
  }
}
</script>

<template>
  <div class="model-list">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item>模型配置</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>AI 模型配置</span>
          <el-button type="primary" @click="dialogVisible = true">添加模型</el-button>
        </div>
      </template>

      <el-table :data="models" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="模型" min-width="180">
          <template #default="{ row }">
            <div class="model-info">
              <el-icon size="22" color="#409EFF"><Cpu /></el-icon>
              <div>
                <div class="m-name">{{ row.name }}</div>
                <div class="m-vendor">{{ row.vendor }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }"><el-tag type="warning" size="small">{{ row.type }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="apiUrl" label="API地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" size="small">{{ row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button link type="danger" size="small">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !models.length" description="暂无模型配置" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="添加模型" width="560px">
      <el-form :model="modelForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="modelForm.name" placeholder="如 Stable Diffusion XL" />
        </el-form-item>
        <el-form-item label="供应商" required>
          <el-select v-model="modelForm.vendor" style="width: 100%">
            <el-option v-for="v in vendors" :key="v.v" :label="v.l" :value="v.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="modelForm.type" style="width: 100%">
            <el-option v-for="t in types" :key="t.v" :label="t.l" :value="t.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="API地址">
          <el-input v-model="modelForm.apiUrl" placeholder="可选" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="modelForm.apiKey" type="password" show-password placeholder="可选" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="modelForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleCreate">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.model-list { max-width: 1400px; margin: 0 auto; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.model-info { display: flex; align-items: center; gap: 12px; }
.m-name { font-weight: 600; color: #303133; }
.m-vendor { font-size: 12px; color: #909399; }
</style>
