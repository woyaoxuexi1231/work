<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '../../api/request.js'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const projects = ref([])
const loading = ref(false)
const tableLoading = ref(false)
const dialogVisible = ref(false)
const dialogLoading = ref(false)
let fetching = false

// 新建项目表单
const newProject = ref({
  name: '',
  resourceId: null
})
const resourceList = ref([])

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
  await fetchProjects()
  await fetchResources()
})

async function fetchProjects() {
  if (fetching) return
  fetching = true
  tableLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/list')
    if (res._httpError) {
      ElMessage.error(res.message || '网关错误')
      return
    }
    if (res.code === 200) {
      projects.value = res.data || []
    } else {
      ElMessage.error(res.message || '获取项目列表失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    tableLoading.value = false
    fetching = false
  }
}

async function fetchResources() {
  try {
    const res = await post('/mlm-api/api/resources/list')
    if (res.code === 200) {
      resourceList.value = res.data || []
    }
  } catch (e) {
    // 静默失败
  }
}

async function createProject() {
  if (!newProject.value.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  dialogLoading.value = true
  try {
    const res = await post('/mlm-api/api/projects/create', {
      name: newProject.value.name,
      resourceId: newProject.value.resourceId
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '创建成功')
      dialogVisible.value = false
      newProject.value = { name: '', resourceId: null }
      await fetchProjects()
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    dialogLoading.value = false
  }
}

async function toggleVisibility(row) {
  try {
    const res = await post('/mlm-api/api/projects/toggle-visibility', { id: row.id })
    if (res.code === 200) {
      ElMessage.success(row.isPublic ? '已设为私有' : '已设为公开')
      await fetchProjects()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

function goToDetail(project) {
  router.push(`/mlm/project/${project.id}`)
}

// 获取阶段名称
function getStageName(episodes) {
  if (!episodes || episodes.length === 0) return '无剧集'
  
  // 找到最高阶段的状态
  let maxStatus = 0
  let hasCompleted = false
  let hasFailed = false
  
  for (const ep of episodes) {
    if (ep.status === STATUS.COMPLETED) {
      hasCompleted = true
    } else if (ep.status === STATUS.FAILED) {
      hasFailed = true
    } else if (ep.status > maxStatus) {
      maxStatus = ep.status
    }
  }
  
  if (hasCompleted) return '已完成'
  if (hasFailed) return '部分失败'
  
  const statusNames = {
    [STATUS.SCRIPT_DRAFT]: '剧本创作',
    [STATUS.SCRIPT_REVIEW]: '剧本审核',
    [STATUS.STORYBOARD]: '拆分镜',
    [STATUS.GENERATING]: 'AI成片',
    [STATUS.EPISODE_APPROVAL]: '终审'
  }
  
  return statusNames[maxStatus] || '未开始'
}

// 获取阶段类型
function getStageType(episodes) {
  if (!episodes || episodes.length === 0) return 'info'
  
  let maxStatus = 0
  let hasCompleted = false
  let hasFailed = false
  
  for (const ep of episodes) {
    if (ep.status === STATUS.COMPLETED) {
      hasCompleted = true
    } else if (ep.status === STATUS.FAILED) {
      hasFailed = true
    } else if (ep.status > maxStatus) {
      maxStatus = ep.status
    }
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
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>MLM Anime</el-breadcrumb-item>
      <el-breadcrumb-item>项目列表</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>项目列表 ({{ projects.length }})</span>
          <el-button type="primary" @click="dialogVisible = true">新建项目</el-button>
        </div>
      </template>
      
      <el-table 
        :data="projects" 
        v-loading="tableLoading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="项目名称" min-width="200">
          <template #default="{ row }">
            <div class="project-name" @click="goToDetail(row)">
              <el-icon color="#409EFF"><FolderOpened /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="阶段" width="150">
          <template #default="{ row }">
            <el-tag :type="getStageType(row.episodes)" size="small">
              {{ getStageName(row.episodes) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可见性" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isPublic ? 'success' : 'info'" size="small">
              {{ row.isPublic ? '公开' : '私有' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goToDetail(row)">详情</el-button>
            <el-button link type="warning" size="small" @click="toggleVisibility(row)">
              {{ row.isPublic ? '设为私有' : '设为公开' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-empty v-if="!tableLoading && projects.length === 0" description="暂无项目，点击右上角创建" />
    </el-card>

    <!-- 新建项目对话框 -->
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
        <el-button type="primary" :loading="dialogLoading" @click="createProject">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.project-list {
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
.project-name {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #409EFF;
}
.project-name:hover {
  text-decoration: underline;
}
</style>
