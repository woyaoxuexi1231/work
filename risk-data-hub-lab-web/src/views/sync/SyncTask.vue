<!--
  SyncTask — 同步任务页
  发起数据同步、查看进度和结果，支持自动刷新
-->
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getSyncTask, startSync, listDatasources } from '@/api/modules/hub'
import { ElMessage } from 'element-plus'

const syncTask = ref(null)
const loading = ref(false)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dsList = ref([])
const syncForm = ref({ dataSourceKey: '', pageSize: 100 })

let timer = null

onMounted(async () => {
  await fetchTask()
  fetchDs()
  timer = setInterval(() => { if (syncTask.value?.status === 'RUNNING') fetchTask(true) }, 3000)
})

onUnmounted(() => { if (timer) clearInterval(timer) })

async function fetchTask(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await getSyncTask()
    syncTask.value = res.data
  } catch (e) { if (!silent) console.error('[Sync] 获取任务失败:', e.message) }
  finally { if (!silent) loading.value = false }
}

async function fetchDs() {
  try {
    const res = await listDatasources()
    dsList.value = res.data || []
  } catch (e) { console.error('[Sync] 获取数据源失败:', e.message) }
}

async function handleStart() {
  if (!syncForm.value.dataSourceKey) { ElMessage.warning('请选择数据源'); return }
  submitLoading.value = true
  try {
    await startSync(syncForm.value)
    ElMessage.success('同步任务已启动')
    dialogVisible.value = false
    await fetchTask()
  } catch (e) { console.error('[Sync] 启动失败:', e.message) }
  finally { submitLoading.value = false }
}

function statusType(s) {
  const m = { PENDING: 'info', RUNNING: 'primary', SUCCESS: 'success', COMPLETED: 'success', FAILED: 'danger' }
  return m[s] || 'info'
}

function statusText(s) {
  const m = { PENDING: '等待中', RUNNING: '运行中', SUCCESS: '已完成', COMPLETED: '已完成', FAILED: '失败' }
  return m[s] || s || '-'
}
</script>

<template>
  <div class="sync-task">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item>同步任务</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>同步任务管理</span>
          <div>
            <el-button @click="fetchTask()" :loading="loading">刷新</el-button>
            <el-button type="primary" @click="dialogVisible = true">发起同步</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="syncTask?.status === 'RUNNING'" title="同步运行中..." type="info" :closable="false" show-icon style="margin-bottom: 16px">
        <template #default><span>每 3 秒自动刷新状态</span></template>
      </el-alert>

      <div v-if="syncTask" v-loading="loading">
        <div class="task-header">
          <h4>当前任务</h4>
          <el-tag :type="statusType(syncTask.status)" size="small">{{ statusText(syncTask.status) }}</el-tag>
        </div>

        <el-descriptions :column="3" border style="margin-top: 12px">
          <el-descriptions-item label="任务ID">{{ syncTask.taskId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(syncTask.status)" size="small">{{ statusText(syncTask.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">
            <el-progress :percentage="syncTask.progress || 0" :status="syncTask.status === 'SUCCESS' ? 'success' : undefined" style="width: 120px" />
          </el-descriptions-item>
          <el-descriptions-item label="数据源">{{ syncTask.dataSourceKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ syncTask.startedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ syncTask.finishedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="拉取条数">{{ syncTask.totalPulledCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="落库条数">{{ syncTask.totalSavedCount || 0 }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-empty v-else description="暂无同步任务，点击「发起同步」开始" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="发起数据同步" width="500px">
      <el-form label-width="100px">
        <el-form-item label="数据源" required>
          <el-select v-model="syncForm.dataSourceKey" placeholder="请选择" style="width: 100%">
            <el-option v-for="ds in dsList" :key="ds.key" :label="ds.name" :value="ds.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="每页条数">
          <el-input-number v-model="syncForm.pageSize" :min="10" :max="10000" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleStart">开始同步</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sync-task { max-width: 1400px; margin: 0 auto; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.task-header { display: flex; align-items: center; gap: 12px; }
.task-header h4 { margin: 0; color: #303133; font-size: 16px; }
</style>
