<!--
  ProjectList — 动漫项目列表页
  展示所有项目，支持新建、查看详情、切换可见性
-->
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listProjects, createProject, toggleVisibility } from '@/api/modules/project'
import { listResources } from '@/api/modules/resource'
import { ElMessage } from 'element-plus'

const router = useRouter()
const projects = ref([])
const tableLoading = ref(false)
const dialogVisible = ref(false)
const dialogLoading = ref(false)

const newProject = ref({ name: '', resourceId: null })
const resourceList = ref([])

// 阶段名映射
const STATUS = { SCRIPT_DRAFT: 2, SCRIPT_REVIEW: 3, STORYBOARD: 4, GENERATING: 5, EPISODE_APPROVAL: 6, COMPLETED: 7, FAILED: -1 }

onMounted(async () => {
  await fetchProjects()
  fetchResources()
})

async function fetchProjects() {
  tableLoading.value = true
  try {
    const res = await listProjects()
    projects.value = res.data || []
  } catch (e) {
    console.error('[ProjectList] 获取项目列表失败:', e.message)
  } finally {
    tableLoading.value = false
  }
}

async function fetchResources() {
  try {
    const res = await listResources()
    resourceList.value = res.data || []
  } catch (e) {
    console.error('[ProjectList] 获取资源列表失败:', e.message)
  }
}

async function handleCreate() {
  if (!newProject.value.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  dialogLoading.value = true
  try {
    await createProject(newProject.value)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    newProject.value = { name: '', resourceId: null }
    await fetchProjects()
  } catch (e) {
    console.error('[ProjectList] 创建项目失败:', e.message)
  } finally {
    dialogLoading.value = false
  }
}

async function handleToggle(row) {
  try {
    await toggleVisibility(row.id)
    ElMessage.success(row.isPublic ? '已设为私有' : '已设为公开')
    await fetchProjects()
  } catch (e) {
    console.error('[ProjectList] 切换可见性失败:', e.message)
  }
}

function goDetail(project) {
  router.push(`/project/${project.id}`)
}

function getStageName(episodes) {
  if (!episodes?.length) return '无剧集'
  let maxStatus = 0, hasCompleted = false, hasFailed = false
  for (const ep of episodes) {
    if (ep.status === STATUS.COMPLETED) hasCompleted = true
    else if (ep.status === STATUS.FAILED) hasFailed = true
    else if (ep.status > maxStatus) maxStatus = ep.status
  }
  if (hasCompleted) return '已完成'
  if (hasFailed) return '部分失败'
  const names = { [STATUS.SCRIPT_DRAFT]: '剧本创作', [STATUS.SCRIPT_REVIEW]: '剧本审核', [STATUS.STORYBOARD]: '拆分镜', [STATUS.GENERATING]: 'AI成片', [STATUS.EPISODE_APPROVAL]: '终审' }
  return names[maxStatus] || '未开始'
}

function getStageType(episodes) {
  if (!episodes?.length) return 'info'
  let maxStatus = 0, hasCompleted = false, hasFailed = false
  for (const ep of episodes) {
    if (ep.status === STATUS.COMPLETED) hasCompleted = true
    else if (ep.status === STATUS.FAILED) hasFailed = true
    else if (ep.status > maxStatus) maxStatus = ep.status
  }
  if (hasCompleted) return 'success'
  if (hasFailed) return 'danger'
  if (maxStatus === STATUS.SCRIPT_REVIEW || maxStatus === STATUS.EPISODE_APPROVAL) return 'warning'
  if (maxStatus > 0) return 'primary'
  return 'info'
}
</script>

<template>
  <div class="project-list">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item>项目列表</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>项目列表 ({{ projects.length }})</span>
          <el-button type="primary" @click="dialogVisible = true">新建项目</el-button>
        </div>
      </template>

      <el-table :data="projects" v-loading="tableLoading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="项目名称" min-width="180">
          <template #default="{ row }">
            <div class="project-name" @click="goDetail(row)">
              <el-icon color="#409EFF"><FolderOpened /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="阶段" width="120">
          <template #default="{ row }">
            <el-tag :type="getStageType(row.episodes)" size="small">{{ getStageName(row.episodes) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可见性" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isPublic ? 'success' : 'info'" size="small">{{ row.isPublic ? '公开' : '私有' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row)">详情</el-button>
            <el-button link type="warning" size="small" @click="handleToggle(row)">{{ row.isPublic ? '设为私有' : '设为公开' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!tableLoading && !projects.length" description="暂无项目，点击右上角创建" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建项目" width="500px">
      <el-form :model="newProject" label-width="80px">
        <el-form-item label="项目名称" required>
          <el-input v-model="newProject.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="关联资源">
          <el-select v-model="newProject.resourceId" placeholder="选择资源（可选）" clearable style="width: 100%">
            <el-option v-for="r in resourceList" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.project-list { max-width: 1400px; margin: 0 auto; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.project-name { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #409EFF; }
.project-name:hover { text-decoration: underline; }
</style>
