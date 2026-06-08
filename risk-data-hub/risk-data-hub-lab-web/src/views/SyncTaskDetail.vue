<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSyncTaskById, getSyncDetails, startSync } from '@/api/index.js'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.taskId)

const task = ref(null)
const details = ref([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  await loadTask()
})

async function loadTask() {
  loading.value = true
  error.value = ''
  try {
    const res = await getSyncTaskById(taskId)
    task.value = res.data || null
    if (task.value) {
      const res2 = await getSyncDetails(taskId)
      details.value = res2.data || []
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function handleContinue() {
  if (!task.value) return
  try {
    await startSync(task.value.dataSourceKey, task.value.pageSize || 10000)
    await loadTask()
  } catch (e) {
    error.value = '继续同步失败: ' + e.message
  }
}

function statusInfo(status) {
  const map = {
    QUEUED:  { label: '排队中', class: 'bg-sky-50 text-sky-700' },
    RUNNING: { label: '运行中', class: 'bg-amber-50 text-amber-700' },
    SUCCESS: { label: '已完成', class: 'bg-emerald-50 text-emerald-700' },
    FAILED:  { label: '失败',   class: 'bg-red-50 text-red-700' },
    IDLE:    { label: '空闲',   class: 'bg-slate-50 text-slate-500' }
  }
  return map[task.value?.status] || { label: '-', class: 'bg-slate-50 text-slate-500' }
}

function bizStatusInfo(status) {
  const map = {
    RUNNING: { label: '运行中', class: 'bg-amber-50 text-amber-700' },
    SUCCESS: { label: '已完成', class: 'bg-emerald-50 text-emerald-700' },
    FAILED:  { label: '失败',   class: 'bg-red-50 text-red-700' }
  }
  return map[status] || { label: status || '-', class: 'bg-slate-50 text-slate-500' }
}

function bizProgress(rec) {
  if (!rec.pulledCount || rec.pulledCount === 0) return 0
  return Math.round((rec.savedCount / rec.pulledCount) * 100)
}

function bizPending(rec) {
  return Math.max(0, (rec.pulledCount || 0) - (rec.savedCount || 0))
}
</script>

<template>
  <div class="max-w-4xl mx-auto animate-fade-in">

    <div class="flex items-center gap-2 text-sm text-slate-400 mb-4">
      <button @click="router.push('/sync')" class="hover:text-indigo-600 transition-colors">← 同步任务</button>
      <span>/</span>
      <span class="text-slate-700 font-medium">任务 #{{ taskId }}</span>
    </div>

    <div v-if="error" class="mb-4 bg-red-50 border border-red-200 text-red-700 rounded-xl px-5 py-3 text-sm">
      {{ error }}
      <button @click="error = ''" class="ml-3 underline">关闭</button>
    </div>

    <!-- ============================================================
         任务详情卡片
         ============================================================ -->
    <div v-if="task" class="bg-white rounded-xl border border-slate-200 p-6">

      <!-- 标题栏 -->
      <div class="flex items-center justify-between mb-5">
        <div class="flex items-center gap-3">
          <h3 class="text-base font-semibold text-slate-800">任务 #{{ task.id }}</h3>
          <span class="px-2.5 py-0.5 rounded-md text-xs font-medium" :class="statusInfo().class">
            {{ statusInfo().label }}
          </span>
          <span class="text-xs px-2 py-0.5 rounded" :class="task.syncType === 'FULL' ? 'bg-red-50 text-red-600' : 'bg-sky-50 text-sky-600'">
            {{ task.syncType === 'FULL' ? '全量同步' : '增量同步' }}
          </span>
        </div>
        <div class="flex gap-2">
          <button v-if="task.syncType === 'INCREMENTAL'" @click="loadTask"
            class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50 transition-colors"
          >
            刷新
          </button>
          <button v-if="task.syncType === 'INCREMENTAL'" @click="handleContinue"
            class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors"
          >
            继续同步
          </button>
        </div>
      </div>

      <!-- 实时运行提示 -->
      <div v-if="task.running"
        class="mb-4 bg-amber-50 border border-amber-200 text-amber-700 rounded-lg px-4 py-2.5 text-sm flex items-center gap-2"
      >
        <span class="inline-block w-2 h-2 bg-amber-500 rounded-full animate-pulse"></span>
        任务运行中
      </div>

      <!-- 基础信息 -->
      <div class="grid grid-cols-3 gap-4 mb-4">
        <div>
          <div class="text-xs text-slate-400 mb-1">数据源</div>
          <div class="text-sm text-slate-800 font-medium">{{ task.dataSourceKey || '-' }}</div>
        </div>
        <div>
          <div class="text-xs text-slate-400 mb-1">分页大小</div>
          <div class="text-sm text-slate-800">{{ task.pageSize || '-' }}</div>
        </div>
        <div>
          <div class="text-xs text-slate-400 mb-1">记录于</div>
          <div class="text-sm text-slate-800">{{ task.submittedAt || '-' }}</div>
        </div>
      </div>

      <!-- 进度条 -->
      <div class="mb-4">
        <div class="flex justify-between text-xs text-slate-400 mb-1.5">
          <span>进度</span>
          <span>{{ task.progress || 0 }}%</span>
        </div>
        <div class="w-full bg-slate-100 rounded-full h-2">
          <div class="h-2 rounded-full transition-all duration-500"
            :class="task.status === 'FAILED' ? 'bg-red-500' : task.progress >= 100 ? 'bg-emerald-500' : 'bg-indigo-500'"
            :style="{ width: (task.progress || 0) + '%' }"
          ></div>
        </div>
      </div>

      <!-- 统计 -->
      <div class="grid grid-cols-2 gap-4 mb-4">
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
      <div v-if="task.errorMessage" class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-2.5 text-xs">
        {{ task.errorMessage }}
      </div>
      <div v-if="task.message && task.status !== 'RUNNING' && task.status !== 'FAILED'" class="text-xs text-slate-400 mt-2">
        {{ task.message }}
      </div>
    </div>

    <div v-else-if="!loading" class="bg-white rounded-xl border border-slate-200 p-12 text-center text-slate-400 text-sm">
      未找到任务 #{{ taskId }}
    </div>

    <!-- ============================================================
         业务同步详情
         ============================================================ -->
    <div v-if="details.length > 0" class="mt-6">
      <h4 class="text-base font-semibold text-slate-800 mb-4">业务同步详情</h4>
      <div class="grid grid-cols-2 gap-4">
        <div v-for="rec in details" :key="rec.businessCode" class="bg-white border border-slate-200 rounded-xl p-4">
          <div class="flex items-center justify-between mb-3">
            <span class="font-semibold text-slate-800">{{ rec.businessCode }}</span>
            <span class="px-2 py-0.5 rounded-md text-xs font-medium" :class="bizStatusInfo(rec.status).class">
              {{ bizStatusInfo(rec.status).label }}
            </span>
          </div>
          <div class="text-xs text-slate-400 mb-3">
            开始: {{ rec.startedAt || '-' }}
            <span v-if="rec.finishedAt" class="ml-3">结束: {{ rec.finishedAt }}</span>
          </div>
          <div class="grid grid-cols-3 gap-2 mb-3">
            <div class="bg-slate-50 rounded-lg px-3 py-2">
              <div class="text-xs text-slate-400">已拉取</div>
              <div class="text-lg font-bold text-slate-800">{{ rec.pulledCount ?? 0 }}</div>
            </div>
            <div class="bg-slate-50 rounded-lg px-3 py-2">
              <div class="text-xs text-slate-400">已落库</div>
              <div class="text-lg font-bold text-emerald-600">{{ rec.savedCount ?? 0 }}</div>
            </div>
            <div class="bg-slate-50 rounded-lg px-3 py-2">
              <div class="text-xs text-slate-400">积压</div>
              <div class="text-lg font-bold text-amber-600">{{ bizPending(rec) }}</div>
            </div>
          </div>
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>写入进度</span>
              <span>{{ bizProgress(rec) }}%</span>
            </div>
            <div class="w-full bg-slate-100 rounded-full h-1.5">
              <div class="h-1.5 rounded-full transition-all duration-500"
                :class="rec.status === 'FAILED' ? 'bg-red-500' : rec.status === 'SUCCESS' ? 'bg-emerald-500' : 'bg-indigo-500'"
                :style="{ width: bizProgress(rec) + '%' }"
              ></div>
            </div>
          </div>
          <div v-if="rec.status === 'SUCCESS'" class="mt-3 pt-3 border-t border-slate-100 text-xs text-slate-400">
            <span>共 {{ rec.pageCount || '-' }} 页</span>
          </div>
          <div v-if="rec.id && rec.pulledCount > 0" class="mt-2">
            <button @click="router.push('/batch-metrics/' + rec.id + '?biz=' + rec.businessCode)"
              class="text-xs text-indigo-500 hover:text-indigo-700 font-medium">
              查看批次耗时 →
            </button>
          </div>
          <div v-if="rec.errorMessage" class="mt-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-3 py-2 text-xs">
            {{ rec.errorMessage }}
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="py-12 text-center text-slate-400 text-sm">加载中...</div>
  </div>
</template>