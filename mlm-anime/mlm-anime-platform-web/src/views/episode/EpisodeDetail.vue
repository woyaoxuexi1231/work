<!--
  EpisodeDetail — 剧集详情与流水线操作页
  展示剧集阶段进度，根据当前阶段提供对应操作按钮
-->
<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProject, submitScript, approveScript, rejectScript, generateImage, generateVideo, completeGeneration, finalApprove, finalReject, retryEpisode } from '@/api/modules/project'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))
const episodeId = computed(() => Number(route.params.episodeId))

const project = ref(null)
const episode = ref(null)
const loading = ref(true)
const actionLoading = ref(false)
const scriptDialogVisible = ref(false)
const scriptContent = ref('')

const STATUS = { SCRIPT_DRAFT: 2, SCRIPT_REVIEW: 3, STORYBOARD: 4, GENERATING: 5, EPISODE_APPROVAL: 6, COMPLETED: 7, FAILED: -1 }
const FLOW = [
  { status: STATUS.SCRIPT_DRAFT, name: '剧本创作' },
  { status: STATUS.SCRIPT_REVIEW, name: '剧本审核' },
  { status: STATUS.STORYBOARD, name: '拆分镜' },
  { status: STATUS.GENERATING, name: 'AI成片' },
  { status: STATUS.EPISODE_APPROVAL, name: '终审' },
  { status: STATUS.COMPLETED, name: '完成' }
]

onMounted(() => fetchDetail())

async function fetchDetail() {
  loading.value = true
  try {
    const res = await getProject(projectId.value)
    project.value = res.data.project
    episode.value = (res.data.episodes || []).find(e => Number(e.id) === episodeId.value) || null
    if (!episode.value) ElMessage.error('未找到该剧集')
  } catch (e) {
    console.error('[EpisodeDetail] 获取详情失败:', e.message)
  } finally {
    loading.value = false
  }
}

const stepIndex = computed(() => FLOW.findIndex(s => s.status === episode.value?.status))

function stageName(s) {
  const m = { [STATUS.SCRIPT_DRAFT]: '剧本创作', [STATUS.SCRIPT_REVIEW]: '剧本审核', [STATUS.STORYBOARD]: '拆分镜', [STATUS.GENERATING]: 'AI成片', [STATUS.EPISODE_APPROVAL]: '终审', [STATUS.COMPLETED]: '已完成', [STATUS.FAILED]: '失败' }
  return m[s] || '未知'
}

async function doAction(fn, successMsg) {
  actionLoading.value = true
  try {
    await fn({ projectId: projectId.value, episodeId: episodeId.value, scriptContent: scriptContent.value, prompt: episode.value?.scriptContent || 'anime scene', imageUrl: episode.value?.generatedImageUrl || '' })
    ElMessage.success(successMsg)
    scriptDialogVisible.value = false
    await fetchDetail()
  } catch (e) {
    console.error('[EpisodeDetail] 操作失败:', e.message)
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <div class="episode-detail">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/projects' }">项目列表</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: `/project/${projectId}` }">{{ project?.name }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ episode?.title || '加载中...' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-loading="loading">
      <el-card class="content-card" v-if="episode">
        <template #header>
          <div class="card-header">
            <div>
              <h2>{{ episode.title }} — 第{{ episode.episodeNumber }}集</h2>
              <p class="desc">所属项目: {{ project?.name }}</p>
            </div>
            <el-tag :type="episode.status === STATUS.COMPLETED ? 'success' : episode.status === STATUS.FAILED ? 'danger' : 'primary'" size="large">{{ stageName(episode.status) }}</el-tag>
          </div>
        </template>

        <el-steps :active="stepIndex" finish-status="success" align-center style="margin-bottom: 24px">
          <el-step v-for="s in FLOW" :key="s.status" :title="s.name" />
        </el-steps>

        <el-descriptions :column="3" border>
          <el-descriptions-item label="剧集ID">{{ episode.id }}</el-descriptions-item>
          <el-descriptions-item label="集数">第{{ episode.episodeNumber }}集</el-descriptions-item>
          <el-descriptions-item label="剧本"><span :class="episode.scriptContent ? 'has-script' : 'no-script'">{{ episode.scriptContent ? '已编写' : '未编写' }}</span></el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 操作面板 -->
      <el-card class="content-card" v-if="episode">
        <template #header><span>操作面板</span></template>

        <div v-if="episode.status === STATUS.SCRIPT_DRAFT" class="actions">
          <el-alert type="info" :closable="false" show-icon>请先编写剧本，然后提交审核。</el-alert>
          <div style="margin-top: 16px; display: flex; gap: 12px">
            <el-button type="primary" @click="scriptContent = episode.scriptContent || ''; scriptDialogVisible = true">编写剧本</el-button>
            <el-button type="success" :disabled="!episode.scriptContent" @click="doAction(submitScript, '剧本已提交')">提交审核</el-button>
          </div>
        </div>

        <div v-if="episode.status === STATUS.SCRIPT_REVIEW" class="actions">
          <el-alert type="warning" :closable="false" show-icon>剧本待审核。</el-alert>
          <div v-if="episode.scriptContent" style="margin: 16px 0">
            <h4>剧本内容</h4>
            <el-input type="textarea" :rows="5" :model-value="episode.scriptContent" readonly />
          </div>
          <div style="display: flex; gap: 12px">
            <el-button type="success" @click="doAction(approveScript, '审核通过')">通过</el-button>
            <el-button type="danger" @click="doAction(rejectScript, '已驳回')">驳回</el-button>
          </div>
        </div>

        <div v-if="episode.status === STATUS.STORYBOARD" class="actions">
          <el-alert type="info" :closable="false" show-icon>AI 拆分镜处理中，请等待...</el-alert>
        </div>

        <div v-if="episode.status === STATUS.GENERATING" class="actions">
          <el-alert type="info" :closable="false" show-icon>请生成图片和视频素材。</el-alert>
          <div style="margin-top: 16px; display: flex; gap: 12px; flex-wrap: wrap">
            <el-button type="primary" @click="doAction(generateImage, '图片任务已提交')">生成图片</el-button>
            <el-button type="primary" @click="doAction(generateVideo, '视频任务已提交')">生成视频</el-button>
            <el-button type="success" @click="doAction(completeGeneration, '已完成生成')">完成生成</el-button>
          </div>
        </div>

        <div v-if="episode.status === STATUS.EPISODE_APPROVAL" class="actions">
          <el-alert type="warning" :closable="false" show-icon>请进行最终审核。</el-alert>
          <div style="margin-top: 16px; display: flex; gap: 12px">
            <el-button type="success" @click="doAction(finalApprove, '终审通过')">通过</el-button>
            <el-button type="danger" @click="doAction(finalReject, '已驳回')">驳回</el-button>
          </div>
        </div>

        <div v-if="episode.status === STATUS.COMPLETED" class="actions">
          <el-result icon="success" title="剧集已完成" />
        </div>

        <div v-if="episode.status === STATUS.FAILED" class="actions">
          <el-result icon="error" title="制作失败">
            <template #extra>
              <el-button type="primary" @click="doAction(retryEpisode, '已重试')">重试</el-button>
            </template>
          </el-result>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="scriptDialogVisible" title="编写剧本" width="700px">
      <el-input v-model="scriptContent" type="textarea" :rows="10" placeholder="请输入剧本内容..." />
      <template #footer>
        <el-button @click="scriptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="doAction(submitScript, '剧本已提交')">保存并提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.episode-detail { max-width: 1200px; margin: 0 auto; }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; }
.desc { margin: 8px 0 0; color: #909399; }
.actions { padding: 8px 0; }
.has-script { color: #67c23a; }
.no-script { color: #f56c6c; }
</style>
