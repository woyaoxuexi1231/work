<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
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
let fetching = false

// 新建剧集
const newEpisode = ref({
  title: '',
  episodeNumber: 1
})

// 剧本内容
const scriptContent = ref('')

// 状态常量 - 匹配后端 EpisodeStatus 枚举
const STATUS = {
  SCRIPT_DRAFT: 2,
  SCRIPT_REVIEW: 3,
  STORYBOARD: 4,
  GENERATING: 5,
  EPISODE_APPROVAL: 6,
  COMPLETED: 7,
  FAILED: -1
}

// 阶段流程定义
const STAGE_FLOW = [
  { status: STATUS.SCRIPT_DRAFT, name: '剧本创作', next: STATUS.SCRIPT_REVIEW },
  { status: STATUS.SCRIPT_REVIEW, name: '剧本审核', next: STATUS.STORYBOARD, actions: ['approve', 'reject'] },
  { status: STATUS.STORYBOARD, name: '拆分镜', next: STATUS.GENERATING },
  { status: STATUS.GENERATING, name: 'AI成片', next: STATUS.EPISODE_APPROVAL, actions: ['generateImage', 'generateVideo', 'completeGeneration'] },
  { status: STATUS.EPISODE_APPROVAL, name: '终审', next: STATUS.COMPLETED, actions: ['finalApprove', 'finalReject'] }
]

onMounted(async () => {
  await fetchProjectDetail()
})

onUnmounted(() => {
  fetching = false
})

async function fetchProjectDetail() {
  if (fetching) return
  fetching = true
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
      // 显示状态描述
      if (res.status) {
        ElMessage.success(res.message + ' [' + res.status + ']')
      }
    } else {
      ElMessage.error((res.message || '获取项目详情失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
    fetching = false
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
      ElMessage.success((res.message || '添加成功') + ' [' + res.status + ']')
      episodeDialogVisible.value = false
      newEpisode.value = { title: '', episodeNumber: episodes.value.length + 1 }
      await fetchProjectDetail()
    } else {
      ElMessage.error((res.message || '添加失败') + ' [' + (res.status || res.code) + ']')
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
      ElMessage.success((res.message || '剧本已提交') + ' [' + res.status + ']')
      scriptDialogVisible.value = false
      await fetchProjectDetail()
    } else {
      ElMessage.error((res.message || '提交失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function approveScript(episode) {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/approve-script', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '剧本已通过') + ' [' + res.status + ']')
      await fetchProjectDetail()
    } else {
      ElMessage.error((res.message || '操作失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function rejectScript(episode) {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/reject-script', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '剧本已驳回') + ' [' + res.status + ']')
      await fetchProjectDetail()
    } else {
      ElMessage.error((res.message || '操作失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function retryEpisode(episode) {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/retry', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '已重试') + ' [' + res.status + ']')
      await fetchProjectDetail()
    } else {
      ElMessage.error((res.message || '操作失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function generateImage(episode) {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/generate-image', {
      projectId: projectId.value,
      episodeId: episode.id,
      prompt: episode.scriptContent || 'a beautiful anime scene'
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '图片生成任务已提交') + ' [' + res.status + ']')
    } else {
      ElMessage.error((res.message || '操作失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function generateVideo(episode) {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/generate-video', {
      projectId: projectId.value,
      episodeId: episode.id,
      imageUrl: episode.generatedImageUrl || 'https://example.com/default.jpg'
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '视频生成任务已提交') + ' [' + res.status + ']')
    } else {
      ElMessage.error((res.message || '操作失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function completeGeneration(episode) {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/complete-generation', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '已完成生成，进入终审') + ' [' + res.status + ']')
      await fetchProjectDetail()
    } else {
      ElMessage.error((res.message || '操作失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function finalApprove(episode) {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/approve', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '终审通过，项目完成') + ' [' + res.status + ']')
      await fetchProjectDetail()
    } else {
      ElMessage.error((res.message || '操作失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function finalReject(episode) {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/reject', {
      projectId: projectId.value,
      episodeId: episode.id
    })
    if (res.code === 200) {
      ElMessage.success((res.message || '已驳回') + ' [' + res.status + ']')
      await fetchProjectDetail()
    } else {
      ElMessage.error((res.message || '操作失败') + ' [' + (res.status || res.code) + ']')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 获取阶段进度 (0-100)
function getStageProgress(status) {
  const stageIndex = STAGE_FLOW.findIndex(s => s.status === status)
  if (status === STATUS.COMPLETED) return 100
  if (status === STATUS.FAILED) return 0
  if (stageIndex === -1) return 0
  return Math.round((stageIndex / STAGE_FLOW.length) * 100)
}

// 获取当前阶段名称
function getStageName(status) {
  if (status === STATUS.FAILED) return '失败'
  if (status === STATUS.COMPLETED) return '已完成'
  const stage = STAGE_FLOW.find(s => s.status === status)
  return stage ? stage.name : '未知阶段'
}

// 获取状态标签类型
function getStatusType(status) {
  if (status === STATUS.COMPLETED) return 'success'
  if (status === STATUS.FAILED) return 'danger'
  if (status === STATUS.SCRIPT_REVIEW || status === STATUS.EPISODE_APPROVAL) return 'warning'
  return 'primary'
}

// 判断是否有下一步操作
function hasActions(status) {
  const stage = STAGE_FLOW.find(s => s.status === status)
  return stage && stage.actions && stage.actions.length > 0
}

// 获取可执行的操作
function getActions(status) {
  const stage = STAGE_FLOW.find(s => s.status === status)
  return stage ? (stage.actions || []) : []
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
      <!-- 项目信息 + 阶段进度 -->
      <el-card class="content-card" v-if="project">
        <template #header>
          <div class="card-header">
            <div>
              <h2>{{ project.name }}</h2>
              <p class="subtitle">{{ project.description || '暂无描述' }}</p>
            </div>
            <el-tag :type="getStatusType(project.status)" size="large">
              {{ getStageName(project.status) }}
            </el-tag>
          </div>
        </template>
        
        <!-- 阶段进度条 -->
        <div class="stage-progress">
          <div class="progress-info">
            <span>制作进度</span>
            <span class="progress-percent">{{ getStageProgress(project.status) }}%</span>
          </div>
          <el-progress 
            :percentage="getStageProgress(project.status)" 
            :status="project.status === STATUS.COMPLETED ? 'success' : undefined"
            :color="project.status === STATUS.FAILED ? '#f56c6c' : '#409EFF'"
          />
          <div class="stage-steps">
            <span 
              v-for="(stage, index) in STAGE_FLOW" 
              :key="stage.status"
              :class="['stage-step', { 
                'active': project.status === stage.status,
                'completed': project.status > stage.status || project.status === STATUS.COMPLETED
              }]"
            >
              {{ stage.name }}
            </span>
            <span :class="['stage-step', { 
              'active': project.status === STATUS.COMPLETED,
              'completed': project.status === STATUS.COMPLETED
            }]">
              完成
            </span>
          </div>
        </div>

        <el-descriptions :column="3" border style="margin-top: 20px">
          <el-descriptions-item label="项目ID">{{ project.id }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusType(project.status)" size="small">
              {{ getStageName(project.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="可见性">
            <el-tag :type="project.isPublic ? 'success' : 'info'" size="small">
              {{ project.isPublic ? '公开' : '私有' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建者ID">{{ project.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ project.createdAt || '未知' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ project.updatedAt || '未知' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 剧集列表 -->
      <el-card class="content-card">
        <template #header>
          <div class="card-header">
            <span>剧集列表 ({{ episodes.length }}集)</span>
            <el-button type="primary" @click="episodeDialogVisible = true">添加剧集</el-button>
          </div>
        </template>

        <el-table :data="episodes" stripe v-loading="loading">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="episodeNumber" label="集数" width="80" />
          <el-table-column prop="title" label="标题" min-width="150" />
          <el-table-column label="阶段" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStageName(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" width="120">
            <template #default="{ row }">
              <el-progress 
                :percentage="getStageProgress(row.status)" 
                :status="row.status === STATUS.COMPLETED ? 'success' : undefined"
                :color="row.status === STATUS.FAILED ? '#f56c6c' : '#409EFF'"
                style="width: 80px"
              />
            </template>
          </el-table-column>
          <el-table-column label="剧本" min-width="200">
            <template #default="{ row }">
              <span v-if="row.scriptContent" class="script-preview">{{ row.scriptContent.substring(0, 50) }}...</span>
              <span v-else class="no-script">暂无剧本</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="350" fixed="right">
            <template #default="{ row }">
              <!-- 剧本创作阶段 -->
              <template v-if="row.status === STATUS.SCRIPT_DRAFT">
                <el-button type="primary" size="small" @click="openScriptDialog(row)">编写剧本</el-button>
                <el-button type="success" size="small" @click="submitScript" :disabled="!row.scriptContent">提交剧本</el-button>
              </template>
              
              <!-- 剧本审核阶段 -->
              <template v-if="row.status === STATUS.SCRIPT_REVIEW">
                <el-button type="success" size="small" @click="approveScript(row)">通过</el-button>
                <el-button type="danger" size="small" @click="rejectScript(row)">驳回</el-button>
              </template>
              
              <!-- 分镜阶段 -->
              <template v-if="row.status === STATUS.STORYBOARD">
                <el-tag type="info" size="small">等待AI生成...</el-tag>
              </template>
              
              <!-- AI成片阶段 -->
              <template v-if="row.status === STATUS.GENERATING">
                <el-button type="primary" size="small" @click="generateImage(row)">生成图片</el-button>
                <el-button type="primary" size="small" @click="generateVideo(row)">生成视频</el-button>
                <el-button type="success" size="small" @click="completeGeneration(row)">完成生成</el-button>
              </template>
              
              <!-- 终审阶段 -->
              <template v-if="row.status === STATUS.EPISODE_APPROVAL">
                <el-button type="success" size="small" @click="finalApprove(row)">终审通过</el-button>
                <el-button type="danger" size="small" @click="finalReject(row)">终审驳回</el-button>
              </template>
              
              <!-- 已完成 -->
              <template v-if="row.status === STATUS.COMPLETED">
                <el-tag type="success" size="small">已完成</el-tag>
              </template>
              
              <!-- 失败可重试 -->
              <el-button v-if="row.status === STATUS.FAILED" type="warning" size="small" @click="retryEpisode(row)">重试</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="episodes.length === 0 && !loading" description="暂无剧集，点击右上角添加" />
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
        <el-button type="primary" :loading="dialogLoading" @click="submitScript">保存并提交</el-button>
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

/* 阶段进度条样式 */
.stage-progress {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 10px;
}
.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  color: #606266;
}
.progress-percent {
  font-weight: 600;
  color: #409EFF;
}
.stage-steps {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
}
.stage-step {
  font-size: 12px;
  color: #909399;
  padding: 4px 8px;
  border-radius: 4px;
}
.stage-step.active {
  color: #409EFF;
  font-weight: 600;
  background: #ecf5ff;
}
.stage-step.completed {
  color: #67c23a;
}
</style>
