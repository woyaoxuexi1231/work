<!--
  ProjectDetail — 项目详情页
  展示项目信息 + 剧集列表，支持添加剧集
-->
<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProject, addEpisode } from '@/api/modules/project'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.id))

const project = ref(null)
const episodes = ref([])
const loading = ref(true)
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const newEpisode = ref({ title: '', episodeNumber: 1 })

const STATUS = { SCRIPT_DRAFT: 2, SCRIPT_REVIEW: 3, STORYBOARD: 4, GENERATING: 5, EPISODE_APPROVAL: 6, COMPLETED: 7, FAILED: -1 }

onMounted(async () => {
  await fetchDetail()
})

async function fetchDetail() {
  loading.value = true
  try {
    const res = await getProject(projectId.value)
    project.value = res.data.project
    episodes.value = res.data.episodes || []
  } catch (e) {
    console.error('[ProjectDetail] 获取详情失败:', e.message)
  } finally {
    loading.value = false
  }
}

async function handleAddEpisode() {
  if (!newEpisode.value.title.trim()) { ElMessage.warning('请输入剧集标题'); return }
  dialogLoading.value = true
  try {
    await addEpisode({ projectId: projectId.value, ...newEpisode.value })
    ElMessage.success('添加成功')
    dialogVisible.value = false
    newEpisode.value = { title: '', episodeNumber: episodes.value.length + 1 }
    await fetchDetail()
  } catch (e) {
    console.error('[ProjectDetail] 添加剧集失败:', e.message)
  } finally {
    dialogLoading.value = false
  }
}

function goEpisode(ep) {
  router.push(`/project/${projectId.value}/episode/${ep.id}`)
}

function statusLabel(s) {
  const m = { [STATUS.SCRIPT_DRAFT]: '剧本创作', [STATUS.SCRIPT_REVIEW]: '剧本审核', [STATUS.STORYBOARD]: '拆分镜', [STATUS.GENERATING]: 'AI成片', [STATUS.EPISODE_APPROVAL]: '终审', [STATUS.COMPLETED]: '已完成', [STATUS.FAILED]: '失败' }
  return m[s] || '未知'
}

function statusType(s) {
  if (s === STATUS.COMPLETED) return 'success'
  if (s === STATUS.FAILED) return 'danger'
  if (s === STATUS.SCRIPT_REVIEW || s === STATUS.EPISODE_APPROVAL) return 'warning'
  return 'primary'
}
</script>

<template>
  <div class="project-detail">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/projects' }">项目列表</el-breadcrumb-item>
      <el-breadcrumb-item>{{ project?.name || '加载中...' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-loading="loading">
      <el-card class="content-card" v-if="project">
        <template #header>
          <div class="card-header">
            <div>
              <h2>{{ project.name }}</h2>
              <p class="desc">{{ project.description || '暂无描述' }}</p>
            </div>
            <el-button type="primary" @click="dialogVisible = true">添加剧集</el-button>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="项目ID">{{ project.id }}</el-descriptions-item>
          <el-descriptions-item label="可见性">
            <el-tag :type="project.isPublic ? 'success' : 'info'" size="small">{{ project.isPublic ? '公开' : '私有' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ project.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="content-card">
        <template #header><span>剧集列表 ({{ episodes.length }}集)</span></template>
        <el-table :data="episodes" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="episodeNumber" label="集数" width="70" />
          <el-table-column label="标题" min-width="180">
            <template #default="{ row }">
              <div class="ep-title" @click="goEpisode(row)">
                <el-icon color="#409EFF"><VideoCamera /></el-icon>
                <span>{{ row.title }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="阶段" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="剧本预览" min-width="160">
            <template #default="{ row }">
              <span v-if="row.scriptContent" class="has-script">{{ row.scriptContent.substring(0, 40) }}...</span>
              <span v-else class="no-script">暂无剧本</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="goEpisode(row)">进入</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!episodes.length && !loading" description="暂无剧集" />
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" title="添加剧集" width="480px">
      <el-form :model="newEpisode" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="newEpisode.title" placeholder="请输入剧集标题" />
        </el-form-item>
        <el-form-item label="集数">
          <el-input-number v-model="newEpisode.episodeNumber" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleAddEpisode">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.project-detail { max-width: 1400px; margin: 0 auto; }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; }
.desc { margin: 8px 0 0; color: #909399; }
.ep-title { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #409EFF; }
.ep-title:hover { text-decoration: underline; }
.has-script { color: #67c23a; font-size: 13px; }
.no-script { color: #c0c4cc; font-style: italic; }
</style>
