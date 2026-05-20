<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { post } from '../../api/request.js'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => parseInt(route.params.id))

const project = ref(null)
const episodes = ref([])
const loading = ref(true)
const episodeDialogVisible = ref(false)
const dialogLoading = ref(false)
let fetching = false

// 新建剧集
const newEpisode = ref({
  title: '',
  episodeNumber: 1
})

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
    } else {
      ElMessage.error(res.message || '获取项目详情失败')
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
      ElMessage.success(res.message || '添加成功')
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

// 进入剧集详情页
function goToEpisode(episode) {
  router.push(`/mlm/project/${projectId.value}/episode/${episode.id}`)
}

// 获取当前阶段名称
function getStageName(status) {
  if (status === STATUS.FAILED) return '失败'
  if (status === STATUS.COMPLETED) return '已完成'
  const stageNames = {
    [STATUS.SCRIPT_DRAFT]: '剧本创作',
    [STATUS.SCRIPT_REVIEW]: '剧本审核',
    [STATUS.STORYBOARD]: '拆分镜',
    [STATUS.GENERATING]: 'AI成片',
    [STATUS.EPISODE_APPROVAL]: '终审'
  }
  return stageNames[status] || '未知阶段'
}

// 获取状态标签类型
function getStatusType(status) {
  if (status === STATUS.COMPLETED) return 'success'
  if (status === STATUS.FAILED) return 'danger'
  if (status === STATUS.SCRIPT_REVIEW || status === STATUS.EPISODE_APPROVAL) return 'warning'
  return 'primary'
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
            <el-button type="primary" @click="episodeDialogVisible = true">添加剧集</el-button>
          </div>
        </template>
        
        <el-descriptions :column="3" border>
          <el-descriptions-item label="项目ID">{{ project.id }}</el-descriptions-item>
          <el-descriptions-item label="可见性">
            <el-tag :type="project.isPublic ? 'success' : 'info'" size="small">
              {{ project.isPublic ? '公开' : '私有' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ project.createdAt || '未知' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 剧集列表 -->
      <el-card class="content-card">
        <template #header>
          <span>剧集列表 ({{ episodes.length }}集)</span>
        </template>

        <el-table :data="episodes" stripe v-loading="loading">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="episodeNumber" label="集数" width="80" />
          <el-table-column label="标题" min-width="200">
            <template #default="{ row }">
              <div class="episode-title" @click="goToEpisode(row)">
                <el-icon color="#409EFF"><VideoCamera /></el-icon>
                <span>{{ row.title }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="阶段" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStageName(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="剧本" min-width="200">
            <template #default="{ row }">
              <span v-if="row.scriptContent" class="script-preview">{{ row.scriptContent.substring(0, 50) }}...</span>
              <span v-else class="no-script">暂无剧本</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="goToEpisode(row)">进入</el-button>
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
  align-items: center;
}
.subtitle {
  margin: 8px 0 0 0;
  color: #909399;
}
.episode-title {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #409EFF;
}
.episode-title:hover {
  text-decoration: underline;
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
