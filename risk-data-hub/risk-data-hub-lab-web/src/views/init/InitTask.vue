<!--
  InitTask — 初始化任务页
  启动中台基础数据表结构初始化，查看进度
-->
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getInitTask, startInitData } from '@/api/modules/hub'
import { ElMessage } from 'element-plus'

const initTask = ref(null)
const loading = ref(false)
const submitLoading = ref(false)
let timer = null

onMounted(async () => {
  await fetchTask()
  timer = setInterval(() => { if (initTask.value?.status === 'RUNNING') fetchTask(true) }, 3000)
})

onUnmounted(() => { if (timer) clearInterval(timer) })

async function fetchTask(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await getInitTask()
    initTask.value = res.data
  } catch (e) { if (!silent) console.error('[Init] 获取任务失败:', e.message) }
  finally { if (!silent) loading.value = false }
}

async function handleStart() {
  submitLoading.value = true
  try {
    await startInitData()
    ElMessage.success('初始化任务已启动')
    await fetchTask()
  } catch (e) { console.error('[Init] 启动失败:', e.message) }
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
  <div class="init-task">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item>初始化任务</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="content-card">
      <template #header>
        <div class="card-header">
          <span>初始化任务管理</span>
          <div>
            <el-button @click="fetchTask()" :loading="loading">刷新</el-button>
            <el-button type="success" @click="handleStart" :loading="submitLoading">初始化数据</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="initTask?.status === 'RUNNING'" title="初始化运行中..." type="info" :closable="false" show-icon style="margin-bottom: 16px">
        <template #default><span>每 3 秒自动刷新</span></template>
      </el-alert>

      <div v-if="initTask" v-loading="loading">
        <div class="task-header">
          <h4>当前任务</h4>
          <el-tag :type="statusType(initTask.status)" size="small">{{ statusText(initTask.status) }}</el-tag>
        </div>

        <el-descriptions :column="3" border style="margin-top: 12px">
          <el-descriptions-item label="任务ID">{{ initTask.taskId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(initTask.status)" size="small">{{ statusText(initTask.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">
            <el-progress :percentage="initTask.progress || 0" :status="initTask.status === 'COMPLETED' ? 'success' : undefined" style="width: 120px" />
          </el-descriptions-item>
          <el-descriptions-item label="开始时间" :span="2">{{ initTask.startTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-empty v-else description="暂无初始化任务，点击「初始化数据」开始" />
    </el-card>
  </div>
</template>

<style scoped>
.init-task { max-width: 1400px; margin: 0 auto; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.task-header { display: flex; align-items: center; gap: 12px; }
.task-header h4 { margin: 0; color: #303133; font-size: 16px; }
</style>
