<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { post } from '../../api/request.js'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => Number(route.params.projectId))
const episodeId = computed(() => Number(route.params.episodeId))

const project = ref(null)
const episode = ref(null)
const loading = ref(true)
const dialogLoading = ref(false)
const scriptDialogVisible = ref(false)
const scriptContent = ref('')
let fetching = false

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
  { status: STATUS.SCRIPT_DRAFT, name: '剧本创作', icon: 'Edit' },
  { status: STATUS.SCRIPT_REVIEW, name: '剧本审核', icon: 'DocumentChecked' },
  { status: STATUS.STORYBOARD, name: '拆分镜', icon: 'Grid' },
  { status: STATUS.GENERATING, name: 'AI成片', icon: 'VideoPlay' },
  { status: STATUS.EPISODE_APPROVAL, name: '终审', icon: 'CircleCheck' },
  { status: STATUS.COMPLETED, name: '完成', icon: 'SuccessFilled' }
]

onMounted(async () => {
  await fetchEpisodeDetail()
})

onUnmounted(() => {
  fetching = false
})

async function fetchEpisodeDetail() {
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
      const ep = (res.data.episodes || []).find(e => Number(e.id) === episodeId.value)
      episode.value = ep || null
      if (!ep) {
        ElMessage.error('未找到该剧集')
      }
    } else {
      ElMessage.error(res.message || '获取剧集详情失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
    fetching = false
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

// 判断当前步骤是否已完成
function isStepCompleted(stepIndex) {
  if (!episode.value) return false
  const currentIndex = STAGE_FLOW.findIndex(s => s.status === episode.value.status)
  if (episode.value.status === STATUS.COMPLETED) return true
  return stepIndex < currentIndex
}

// 判断当前步骤是否激活
function isStepActive(stepIndex) {
  if (!episode.value) return false
  const currentIndex = STAGE_FLOW.findIndex(s => s.status === episode.value.status)
  return stepIndex === currentIndex && episode.value.status !== STATUS.COMPLETED
}

// 打开剧本编辑对话框
function openScriptDialog() {
  scriptContent.value = episode.value?.scriptContent || ''
  scriptDialogVisible.value = true
}

// 提交/更新剧本
async function submitScript() {
  if (!scriptContent.value.trim()) {
    ElMessage.warning('请输入剧本内容')
    return
  }
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/submit-script', {
      projectId: projectId.value,
      episodeId: episodeId.value,
      scriptContent: scriptContent.value
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '剧本已提交')
      scriptDialogVisible.value = false
      await fetchEpisodeDetail()
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 剧本审核通过
async function approveScript() {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/approve-script', {
      projectId: projectId.value,
      episodeId: episodeId.value
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '剧本已通过')
      await fetchEpisodeDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 剧本审核驳回
async function rejectScript() {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/reject-script', {
      projectId: projectId.value,
      episodeId: episodeId.value
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '剧本已驳回')
      await fetchEpisodeDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 生成图片
async function generateImage() {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/generate-image', {
      projectId: projectId.value,
      episodeId: episodeId.value,
      prompt: episode.value?.scriptContent || 'a beautiful anime scene'
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '图片生成任务已提交')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 生成视频
async function generateVideo() {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/generate-video', {
      projectId: projectId.value,
      episodeId: episodeId.value,
      imageUrl: episode.value?.generatedImageUrl || 'https://example.com/default.jpg'
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '视频生成任务已提交')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 完成生成，进入终审
async function completeGeneration() {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/complete-generation', {
      projectId: projectId.value,
      episodeId: episodeId.value
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '已完成生成，进入终审')
      await fetchEpisodeDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 终审通过
async function finalApprove() {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/approve', {
      projectId: projectId.value,
      episodeId: episodeId.value
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '终审通过，剧集完成')
      await fetchEpisodeDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 终审驳回
async function finalReject() {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/reject', {
      projectId: projectId.value,
      episodeId: episodeId.value
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '已驳回')
      await fetchEpisodeDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 重试
async function retry() {
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/episode/retry', {
      projectId: projectId.value,
      episodeId: episodeId.value
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '已重试')
      await fetchEpisodeDetail()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

// 返回项目详情
function goBack() {
  router.push(`/mlm/project/${projectId.value}`)
}
</script>

<template>
  <div class="episode-detail">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/mlm/projects' }">项目列表</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: `/mlm/project/${projectId}` }">{{ project?.name }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ episode?.title || '加载中...' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-loading="loading">
      <!-- 剧集信息 -->
      <el-card class="content-card" v-if="episode">
        <template #header>
          <div class="card-header">
            <div>
              <h2>{{ episode.title }} - 第{{ episode.episodeNumber }}集</h2>
              <p class="subtitle">所属项目: {{ project?.name }}</p>
            </div>
            <el-tag :type="getStatusType(episode.status)" size="large">
              {{ getStageName(episode.status) }}
            </el-tag>
          </div>
        </template>

        <!-- 阶段进度 -->
        <div class="stage-progress">
          <el-steps :active="STAGE_FLOW.findIndex(s => s.status === episode.status)" finish-status="success" align-center>
            <el-step 
              v-for="(stage, index) in STAGE_FLOW" 
              :key="stage.status"
              :title="stage.name"
              :status="episode.status === STATUS.COMPLETED && index < STAGE_FLOW.length - 1 ? 'finish' : (isStepActive(index) ? 'process' : (isStepCompleted(index) ? 'finish' : 'wait'))"
            />
          </el-steps>
        </div>

        <el-descriptions :column="3" border style="margin-top: 24px">
          <el-descriptions-item label="剧集ID">{{ episode.id }}</el-descriptions-item>
          <el-descriptions-item label="集数">第{{ episode.episodeNumber }}集</el-descriptions-item>
          <el-descriptions-item label="剧本">
            <span v-if="episode.scriptContent" class="has-script">已编写</span>
            <span v-else class="no-script">未编写</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 操作面板 -->
      <el-card class="content-card" v-if="episode">
        <template #header>
          <span>操作面板</span>
        </template>

        <!-- 剧本创作阶段 -->
        <div v-if="episode.status === STATUS.SCRIPT_DRAFT" class="action-panel">
          <el-alert type="info" :closable="false" show-icon>
            请先编写剧本内容，然后提交审核。
          </el-alert>
          <div class="action-buttons">
            <el-button type="primary" size="large" @click="openScriptDialog">
              <el-icon><Edit /></el-icon>
              编写剧本
            </el-button>
            <el-button type="success" size="large" @click="submitScript" :disabled="!episode.scriptContent">
              提交剧本
            </el-button>
          </div>
          <div v-if="episode.scriptContent" class="script-preview">
            <h4>剧本预览</h4>
            <el-input type="textarea" :rows="6" :model-value="episode.scriptContent" readonly />
          </div>
        </div>

        <!-- 剧本审核阶段 -->
        <div v-if="episode.status === STATUS.SCRIPT_REVIEW" class="action-panel">
          <el-alert type="warning" :closable="false" show-icon>
            剧本待审核，请审核后决定通过或驳回。
          </el-alert>
          <div class="script-preview">
            <h4>待审核剧本</h4>
            <el-input type="textarea" :rows="6" :model-value="episode.scriptContent" readonly />
          </div>
          <div class="action-buttons">
            <el-button type="success" size="large" @click="approveScript">
              <el-icon><CircleCheck /></el-icon>
              通过审核
            </el-button>
            <el-button type="danger" size="large" @click="rejectScript">
              <el-icon><CircleClose /></el-icon>
              驳回修改
            </el-button>
          </div>
        </div>

        <!-- 拆分镜阶段 -->
        <div v-if="episode.status === STATUS.STORYBOARD" class="action-panel">
          <el-alert type="info" :closable="false" show-icon>
            剧本已通过，正在进行AI拆分镜处理...
          </el-alert>
          <div class="action-buttons">
            <el-button type="info" size="large" disabled>
              <el-icon class="is-loading"><Loading /></el-icon>
              AI处理中...
            </el-button>
          </div>
        </div>

        <!-- AI成片阶段 -->
        <div v-if="episode.status === STATUS.GENERATING" class="action-panel">
          <el-alert type="info" :closable="false" show-icon>
            拆分镜完成，请生成图片和视频素材。
          </el-alert>
          <div class="action-buttons">
            <el-button type="primary" size="large" @click="generateImage">
              <el-icon><Picture /></el-icon>
              生成图片
            </el-button>
            <el-button type="primary" size="large" @click="generateVideo">
              <el-icon><VideoPlay /></el-icon>
              生成视频
            </el-button>
            <el-button type="success" size="large" @click="completeGeneration">
              <el-icon><Check /></el-icon>
              完成生成
            </el-button>
          </div>
          <div v-if="episode.generatedImageUrl" class="preview-section">
            <h4>生成素材预览</h4>
            <el-image :src="episode.generatedImageUrl" fit="contain" style="max-width: 400px; max-height: 300px;" />
          </div>
        </div>

        <!-- 终审阶段 -->
        <div v-if="episode.status === STATUS.EPISODE_APPROVAL" class="action-panel">
          <el-alert type="warning" :closable="false" show-icon>
            素材生成完成，请进行最终审核。
          </el-alert>
          <div v-if="episode.generatedImageUrl" class="preview-section">
            <h4>素材预览</h4>
            <el-image :src="episode.generatedImageUrl" fit="contain" style="max-width: 400px; max-height: 300px;" />
          </div>
          <div class="action-buttons">
            <el-button type="success" size="large" @click="finalApprove">
              <el-icon><CircleCheck /></el-icon>
              终审通过
            </el-button>
            <el-button type="danger" size="large" @click="finalReject">
              <el-icon><CircleClose /></el-icon>
              驳回修改
            </el-button>
          </div>
        </div>

        <!-- 已完成 -->
        <div v-if="episode.status === STATUS.COMPLETED" class="action-panel">
          <el-result icon="success" title="剧集已完成">
            <template #extra>
              <el-button type="primary" @click="goBack">返回项目</el-button>
            </template>
          </el-result>
        </div>

        <!-- 失败 -->
        <div v-if="episode.status === STATUS.FAILED" class="action-panel">
          <el-result icon="error" title="制作失败">
            <template #sub-title>
              <p>很抱歉，该剧集在制作过程中遇到了问题</p>
            </template>
            <template #extra>
              <el-button type="primary" @click="retry">重试</el-button>
              <el-button @click="goBack">返回项目</el-button>
            </template>
          </el-result>
        </div>
      </el-card>

      <!-- 返回按钮 -->
      <div class="back-section">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回项目列表
        </el-button>
      </div>
    </div>

    <!-- 剧本编辑对话框 -->
    <el-dialog v-model="scriptDialogVisible" title="编写剧本" width="700px">
      <el-form>
        <el-form-item label="剧集">
          <span>{{ episode?.title }} - 第{{ episode?.episodeNumber }}集</span>
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
.episode-detail {
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
  align-items: flex-start;
}
.subtitle {
  margin: 8px 0 0 0;
  color: #909399;
}
.stage-progress {
  padding: 20px 10px;
  background: #f5f7fa;
  border-radius: 8px;
}
.action-panel {
  padding: 10px 0;
}
.action-buttons {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  flex-wrap: wrap;
}
.script-preview, .preview-section {
  margin-top: 20px;
}
.script-preview h4, .preview-section h4 {
  margin: 0 0 12px 0;
  color: #606266;
}
.has-script {
  color: #67c23a;
}
.no-script {
  color: #f56c6c;
}
.back-section {
  margin-top: 20px;
  text-align: center;
}
</style>
