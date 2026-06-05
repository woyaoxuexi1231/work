<!--
  SyncTask — 同步任务页
  功能：查看当前同步任务状态、发起新的同步任务、自动刷新进行中的任务
-->
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getSyncTask, startSync, listDatasources } from '@/api/index.js'

// ===== 任务状态 =====
const task = ref(null)         // 当前同步任务
const loading = ref(false)     // 页面加载中
const taskError = ref('')      // 任务错误信息

// ===== 弹窗状态 =====
const showForm = ref(false)
const submitting = ref(false)

// 同步表单
const form = ref({
  dataSourceKey: '',
  pageSize: 100
})

// 可选数据源列表（不含中台库）
const dsOptions = ref([])

// 自动刷新定时器
let timer = null

// ===== 初始化 =====
onMounted(async () => {
  await loadTask()
  loadDsOptions()
})

// 离开页面时清除定时器
onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// ===== 加载任务 =====
async function loadTask() {
  loading.value = true
  taskError.value = ''
  try {
    const res = await getSyncTask()
    task.value = res.data

    // 如果任务正在运行，启动 3 秒自动刷新
    if (res.data?.running) {
      startAutoRefresh()
    } else {
      stopAutoRefresh()
    }
  } catch (e) {
    taskError.value = e.message
    stopAutoRefresh()
  } finally {
    loading.value = false
  }
}

// ===== 加载数据源选项（过滤掉中台库） =====
async function loadDsOptions() {
  try {
    const res = await listDatasources()
    dsOptions.value = (res.data || []).filter(ds => ds.datasourceType !== 'HUB')
  } catch (e) {
    console.error('[同步] 获取数据源列表失败', e.message)
  }
}

// ===== 自动刷新 =====
function startAutoRefresh() {
  stopAutoRefresh()
  timer = setInterval(() => {
    getSyncTask().then(res => {
      task.value = res.data
      if (!res.data?.running) {
        stopAutoRefresh() // 任务完成或失败后停止刷新
      }
    }).catch(() => {})
  }, 3000)
}

function stopAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

// ===== 发起同步 =====
function openForm() {
  form.value = { dataSourceKey: dsOptions.value[0]?.key || '', pageSize: 100 }
  showForm.value = true
}

async function handleStart() {
  if (!form.value.dataSourceKey) return

  submitting.value = true
  try {
    await startSync(form.value.dataSourceKey, form.value.pageSize)
    showForm.value = false
    await loadTask()
  } catch (e) {
    taskError.value = '启动同步失败: ' + e.message
  } finally {
    submitting.value = false
  }
}

// ===== 任务状态样式 =====
function statusInfo(status) {
  const map = {
    QUEUED:  { label: '排队中', class: 'bg-sky-50 text-sky-700' },
    RUNNING: { label: '运行中', class: 'bg-amber-50 text-amber-700' },
    SUCCESS: { label: '已完成', class: 'bg-emerald-50 text-emerald-700' },
    FAILED:  { label: '失败',   class: 'bg-red-50 text-red-700' },
    IDLE:    { label: '空闲',   class: 'bg-slate-50 text-slate-500' }
  }
  return map[status] || { label: status || '-', class: 'bg-slate-50 text-slate-500' }
}
</script>

<template>
  <div class="max-w-4xl mx-auto animate-fade-in">
    <!-- ===== 错误提示 ===== -->
    <div
      v-if="taskError"
      class="mb-4 bg-red-50 border border-red-200 text-red-700 rounded-xl px-5 py-3 text-sm"
    >
      {{ taskError }}
      <button @click="taskError = ''" class="ml-3 underline">关闭</button>
    </div>

    <!-- ===== 同步任务卡片 ===== -->
    <div class="bg-white rounded-xl border border-slate-200 p-6">
      <!-- 标题 + 操作按钮 -->
      <div class="flex items-center justify-between mb-5">
        <div class="flex items-center gap-3">
          <h3 class="text-base font-semibold text-slate-800">同步任务</h3>
          <span
            v-if="task"
            class="px-2.5 py-0.5 rounded-md text-xs font-medium"
            :class="statusInfo(task.status).class"
          >
            {{ statusInfo(task.status).label }}
          </span>
        </div>

        <div class="flex gap-2">
          <button
            @click="loadTask"
            class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50 transition-colors"
            :disabled="loading"
          >
            {{ loading ? '刷新中...' : '刷新' }}
          </button>
          <button
            @click="openForm"
            class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors"
          >
            + 发起同步
          </button>
        </div>
      </div>

      <!-- 自动刷新提示 -->
      <div
        v-if="task?.running"
        class="mb-4 bg-amber-50 border border-amber-200 text-amber-700 rounded-lg px-4 py-2.5 text-sm flex items-center gap-2"
      >
        <span class="inline-block w-2 h-2 bg-amber-500 rounded-full animate-pulse"></span>
        任务运行中，每 3 秒自动刷新
      </div>

      <!-- 任务详情 -->
      <div v-if="task && task.status !== 'IDLE'" class="space-y-4">
        <!-- 基础信息行 -->
        <div class="grid grid-cols-3 gap-4">
          <div>
            <div class="text-xs text-slate-400 mb-1">数据源</div>
            <div class="text-sm text-slate-800 font-medium">{{ task.dataSourceKey || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-slate-400 mb-1">数据源名称</div>
            <div class="text-sm text-slate-800">{{ task.dataSourceName || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-slate-400 mb-1">分页大小</div>
            <div class="text-sm text-slate-800">{{ task.pageSize || '-' }}</div>
          </div>
        </div>

        <!-- 时间信息行 -->
        <div class="grid grid-cols-3 gap-4">
          <div>
            <div class="text-xs text-slate-400 mb-1">提交时间</div>
            <div class="text-sm text-slate-600">{{ task.submittedAt || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-slate-400 mb-1">开始时间</div>
            <div class="text-sm text-slate-600">{{ task.startedAt || '-' }}</div>
          </div>
          <div>
            <div class="text-xs text-slate-400 mb-1">结束时间</div>
            <div class="text-sm text-slate-600">{{ task.finishedAt || '-' }}</div>
          </div>
        </div>

        <!-- 进度条 -->
        <div>
          <div class="flex justify-between text-xs text-slate-400 mb-1.5">
            <span>进度</span>
            <span>{{ task.progress || 0 }}%</span>
          </div>
          <div class="w-full bg-slate-100 rounded-full h-2">
            <div
              class="h-2 rounded-full transition-all duration-500"
              :class="task.status === 'FAILED' ? 'bg-red-500' : task.progress >= 100 ? 'bg-emerald-500' : 'bg-indigo-500'"
              :style="{ width: (task.progress || 0) + '%' }"
            ></div>
          </div>
        </div>

        <!-- 统计行 -->
        <div class="grid grid-cols-2 gap-4">
          <div class="bg-slate-50 rounded-lg px-4 py-3">
            <div class="text-xs text-slate-400">拉取条数</div>
            <div class="text-xl font-bold text-slate-800">{{ task.totalPulledCount ?? 0 }}</div>
          </div>
          <div class="bg-slate-50 rounded-lg px-4 py-3">
            <div class="text-xs text-slate-400">落库条数</div>
            <div class="text-xl font-bold text-slate-800">{{ task.totalSavedCount ?? 0 }}</div>
          </div>
        </div>

        <!-- 错误信息 -->
        <div
          v-if="task.errorMessage"
          class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-2.5 text-xs"
        >
          {{ task.errorMessage }}
        </div>

        <!-- 任务消息 -->
        <div v-if="task.message && task.status !== 'FAILED'" class="text-xs text-slate-400">
          {{ task.message }}
        </div>
      </div>

      <!-- 空闲状态 -->
      <div v-else-if="!loading" class="py-12 text-center text-slate-400 text-sm">
        暂无同步任务，点击「发起同步」开始
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="py-12 text-center text-slate-400 text-sm">加载中...</div>
    </div>

    <!-- ===== 发起同步弹窗 ===== -->
    <div
      v-if="showForm"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="showForm = false"
    >
      <div class="bg-white rounded-2xl w-full max-w-md mx-4 p-6 shadow-2xl">
        <h3 class="text-lg font-semibold text-slate-800 mb-5">发起同步</h3>

        <div class="space-y-4">
          <!-- 选择数据源 -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">数据源</label>
            <select
              v-model="form.dataSourceKey"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            >
              <option value="" disabled>请选择数据源</option>
              <option v-for="ds in dsOptions" :key="ds.key" :value="ds.key">
                {{ ds.name }} ({{ ds.key }})
              </option>
            </select>
            <div v-if="dsOptions.length === 0" class="mt-1 text-xs text-slate-400">
              暂无可用数据源，请先在「数据源管理」页面注册
            </div>
          </div>

          <!-- 分页大小 -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">每页条数</label>
            <input
              v-model.number="form.pageSize"
              type="number"
              min="10"
              max="500"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
            <div class="mt-1 text-xs text-slate-400">范围 10 ~ 500</div>
          </div>
        </div>

        <!-- 按钮 -->
        <div class="flex justify-end gap-3 mt-6 pt-4 border-t border-slate-100">
          <button
            @click="showForm = false"
            class="px-5 py-2 text-sm text-slate-600 hover:text-slate-800 font-medium"
          >
            取消
          </button>
          <button
            @click="handleStart"
            :disabled="!form.dataSourceKey || submitting"
            class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {{ submitting ? '启动中...' : '开始同步' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
