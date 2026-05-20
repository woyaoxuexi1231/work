<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { post } from '../../api/request.js'
import { ElMessage } from 'element-plus'

const route = useRoute()
const projectId = computed(() => parseInt(route.params.id))

const project = ref(null)
const episodes = ref([])
const loading = ref(true)
const episodeDialogVisible = ref(false)
const scriptDialogVisible = ref(false)
const selectedEpisode = ref(null)
const dialogLoading = ref(false)

// 新建剧集
const newEpisode = ref({
  title: '',
  episodeNumber: 1
})

// 剧本内容
const scriptContent = ref('')

onMounted(async () => {
  await fetchProjectDetail()
})

async function fetchProjectDetail() {
  loading.value = true
  try {
    const res = await post('/mlm-api/api/projects/get', { id: projectId.value })
    if (res._httpError) {
      ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      project.value = res.data.project
      episodes.value = res.data.episodes || []
    } else {
      ElMessage.error(res.message || '获取项目详情失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
  }
}

async function addEpisode() {
  if (!newEpisode.value.title.trim()) {
    ElMessage.warning('请输入剧集标题')
    return
  }
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/add', {
      projectId: projectId.value,
      title: newEpisode.value.title,
      episodeNumber: newEpisode.value.episodeNumber
    })
    if (res.code === 200) {
      ElMessage.success('添加成功')
      episodeDialogVisible.value = false
      newEpisode.value = { title: '', episodeNumber: episodes.value.length + 1 }
      await fetchProjectDetail()
    } else {
      ElMessage.error(res.message || '添加失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

function openScriptDialog(episode) {
  selectedEpisode.value = episode
  scriptContent.value = episode.scriptContent || ''
  scriptDialogVisible.value = true
}

async function submitScript() {
  if (!scriptContent.value.trim()) {
    ElMessage.warning('请输入剧本内容')
    return
  }
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/submit-script', {
      projectId: projectId.value,
      episodeId: selectedEpisode.value.id,
      scriptContent: scriptContent.value
    })
    if (res.code === 200) {
      ElMessage.success('剧本已提交')
      scriptDialogVisible.value = false
      await fetchProjectDetail()
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function approveScript(episode) {
  try {
    const res = await post('/mlm-api/api/projects/episode/approve-script', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success('剧本已通过')
      await fetchProjectDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function rejectScript(episode) {
  try {
    const res = await post('/mlm-api/api/projects/episode/reject-script', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success('剧本已驳回')
      await fetchProjectDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function retryEpisode(episode) {
  try {
    const res = await post('/mlm-api/api/projects/episode/retry', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success('已重试')
      await fetchProjectDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function generateImage(episode) {
  try {
    const res = await post('/mlm-api/api/projects/episode/generate-image', {
      projectId: projectId.value,
      episodeId: episode.id,
      prompt: 'a beautiful anime scene'
    })
    if (res.code === 200) {
      ElMessage.success('图片生成任务已提交')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function generateVideo(episode) {
  try {
    const res = await post('/mlm-api/api/projects/episode/generate-video', {
      projectId: projectId.value,
      episodeId: episode.id,
      imageUrl: episode.generatedImageUrl || 'https://example.com/default.jpg'
    })
    if (res.code === 200) {
      ElMessage.success('视频生成任务已提交')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function completeGeneration(episode) {
  try {
    const res = await post('/mlm-api/api/projects/episode/complete-generation', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success('已完成生成，进入终审')
      await fetchProjectDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function finalApprove(episode) {
  try {
    const res = await post('/mlm-api/api/projects/episode/approve', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success('终审通过，项目完成')
      await fetchProjectDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

async function finalReject(episode) {
  try {
    const res = await post('/mlm-api/api/projects/episode/reject', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success('已驳回')
      await fetchProjectDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

const statusType = (status) => {
  const map = {
    'CREATED': 'info',
    'SCRIPT_DRAFT': 'warning',
    'SCRIPT_REVIEW': 'warning',
    'STORYBOARD': 'primary',
    'GENERATING': 'primary',
    'GENERATION_REVIEW': 'primary',
    'FINAL_REVIEW': 'warning',
    'COMPLETED': 'success',
    'REJECTED': 'danger'
  }
  return map[status] || 'info'
}

const statusText = (status) => {
  const map = {
    'CREATED': '已创建',
    'SCRIPT_DRAFT': '剧本草稿',
    'SCRIPT_REVIEW': '剧本待审',
    'STORYBOARD': '分镜制作',
    'GENERATING': '生成中',
    'GENERATION_REVIEW': '生成待审',
    'FINAL_REVIEW': '终审中',
    'COMPLETED': '已完成',
    'REJECTED': '已驳回'
  }
  return map[status] || status || '未知'
}

const stageName = (status) => {
  const map = {
    'CREATED': '创建阶段',
    'SCRIPT_DRAFT': '剧本阶段',
    'SCRIPT_REVIEW': '剧本审核',
    'STORYBOARD': '分镜阶段',
    'GENERATING': '生成阶段',
    'GENERATION_REVIEW': '生成审核',
    'FINAL_REVIEW': '终审阶段',
    'COMPLETED': '已完成',
    'REJECTED': '已驳回'
  }
  return map[status] || status || '未知'
}
</script>

<template>
  <div class="project-detail">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/mlm/projects' }">项目列表</el-breadcrumb-item>
      <el-breadcrumb-item>{{ project?.name || '加载中...' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-loading="loading">
      <!-- 项目信息 -->
      <el-card class="content-card" v-if="project">
        <template #header>
          <div class="card-header">
            <div>
              <h2>{{ project.name }}</h2>
              <p class="subtitle">{{ project.description || '暂无描述' }}</p>
            </div>
            <el-tag :type="statusType(project.status)" size="large">{{ stageName(project.status) }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="项目ID">{{ project.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(project.status) }}</el-descriptions-item>
          <el-descriptions-item label="可见性">{{ project.isPublic ? '公开' : '私有' }}</el-descriptions-item>
          <el-descriptions-item label="创建者ID">{{ project.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ project.createdAt || '未知' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ project.updatedAt || '未知' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 剧集列表 -->
      <el-card class="content-card">
        <template #header>
          <div class="card-header">
            <span>剧集列表</span>
            <el-button type="primary" :icon="Plus" @click="episodeDialogVisible = true">添加剧集</el-button>
          </div>
        </template>

        <el-table :data="episodes" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="episodeNumber" label="集数" width="80" />
          <el-table-column prop="title" label="标题" min-width="150" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="剧本" min-width="200">
            <template #default="{ row }">
              <span v-if="row.scriptContent" class="script-preview">{{ row.scriptContent.substring(0, 50) }}...</span>
              <span v-else class="no-script">暂无剧本</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="400" fixed="right">
            <template #default="{ row }">
              <!-- 剧本阶段 -->
              <template v-if="row.status === 'CREATED' || row.status === 'SCRIPT_DRAFT'">
                <el-button type="primary" size="small" @click="openScriptDialog(row)">编写剧本</el-button>
              </template>
              
              <!-- 剧本审核阶段 -->
              <template v-if="row.status === 'SCRIPT_REVIEW'">
                <el-button type="success" size="small" @click="approveScript(row)">通过</el-button>
                <el-button type="danger" size="small" @click="rejectScript(row)">驳回</el-button>
              </template>
              
              <!-- 生成阶段 -->
              <template v-if="row.status === 'GENERATING'">
                <el-button type="primary" size="small" @click="generateImage(row)">生成图片</el-button>
                <el-button type="primary" size="small" @click="generateVideo(row)">生成视频</el-button>
                <el-button type="success" size="small" @click="completeGeneration(row)">完成生成</el-button>
              </template>
              
              <!-- 生成审核阶段 -->
              <template v-if="row.status === 'GENERATION_REVIEW'">
                <el-button type="success" size="small" @click="finalApprove(row)">通过</el-button>
                <el-button type="danger" size="small" @click="finalReject(row)">驳回</el-button>
              </template>
              
              <!-- 终审阶段 -->
              <template v-if="row.status === 'FINAL_REVIEW'">
                <el-button type="success" size="small" @click="finalApprove(row)">终审通过</el-button>
                <el-button type="danger" size="small" @click="finalReject(row)">终审驳回</el-button>
              </template>
              
              <!-- 重试按钮 -->
              <el-button v-if="row.status === 'REJECTED'" type="warning" size="small" @click="retryEpisode(row)">重试</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="episodes.length === 0" description="暂无剧集，点击右上角添加" />
      </el-card>
    </div>

    <!-- 添加剧集对话框 -->
    <el-dialog v-model="episodeDialogVisible" title="添加剧集" width="500px">
      <el-form :model="newEpisode" label-width="80px">
        <el-form-item label="剧集标题" required>
          <el-input v-model="newEpisode.title" placeholder="请输入剧集标题" />
        </el-form-item>
        <el-form-item label="集数">
          <el-input-number v-model="newEpisode.episodeNumber" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="episodeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="addEpisode">添加</el-button>
      </template>
    </el-dialog>

    <!-- 剧本编辑对话框 -->
    <el-dialog v-model="scriptDialogVisible" title="编写剧本" width="700px">
      <el-form>
        <el-form-item label="剧集">
          <span>{{ selectedEpisode?.title }} - 第{{ selectedEpisode?.episodeNumber }}集</span>
        </el-form-item>
        <el-form-item label="剧本内容" required>
          <el-input 
            v-model="scriptContent" 
            type="textarea" 
            :rows="10" 
            placeholder="请输入剧本内容..." 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scriptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="submitScript">提交剧本</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.project-detail {
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
  align-items: flex-start;
}
.subtitle {
  margin: 8px 0 0 0;
  color: #909399;
}
.script-preview {
  color: #67c23a;
  font-size: 13px;
}
.no-script {
  color: #c0c4cc;
  font-style: italic;
}
</style>
