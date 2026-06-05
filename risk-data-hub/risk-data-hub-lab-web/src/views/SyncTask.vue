<!--
  SyncTask — 同步任务页

  核心功能：
  1. 查看当前同步任务状态（进度、拉取/落库数量、运行信息）
  2. 查看每个业务表（STOCK/TRADE/POSITION/ASSET）的同步详情
  3. 发起新的同步任务

  实时进度：后端每 1 秒写一次进度到 sync_task 和 sync_business_record，
  前端每 3 秒轮询刷新，运行中显示脉冲动画提示。
-->
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getSyncTask, startSync, forceRefresh, listDatasources, getSyncDetails, getBatchMetrics } from '@/api/index.js'

// ============================================================
// 数据状态
// ============================================================

const task = ref(null)
const details = ref([])
const loading = ref(false)
const taskError = ref('')
const batchModal = ref({ visible: false, recordId: null, businessCode: '', current: 1, data: null })

// ============================================================
// 发起同步弹窗状态
// ============================================================

const showForm = ref(false)
const submitting = ref(false)
const forceMode = ref(false)

const form = ref({
  dataSourceKey: '',
  pageSize: 10000
})

const dsOptions = ref([])
let timer = null

// ============================================================
// 生命周期
// ============================================================

onMounted(async () => {
  await loadTask()
  loadDsOptions()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// ============================================================
// 加载当前任务 & 业务详情
// ============================================================

async function loadTask() {
  loading.value = true
  taskError.value = ''
  try {
    const res = await getSyncTask()
    task.value = res.data

    if (res.data?.id) {
      await loadDetails()
    }

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

async function loadDetails() {
  if (!task.value?.id) return
  try {
    const res = await getSyncDetails(task.value.id)
    details.value = res.data || []
  } catch (e) {
    console.error('[同步] 加载业务详情失败', e.message)
  }
}

// ============================================================
// 加载数据源选项
// ============================================================

async function loadDsOptions() {
  try {
    const res = await listDatasources()
    dsOptions.value = (res.data || []).filter(ds => ds.datasourceType !== 'HUB')
  } catch (e) {
    console.error('[同步] 获取数据源列表失败', e.message)
  }
}

// ============================================================
// 自动刷新（每 3 秒轮询任务状态 + 业务详情）
// ============================================================

function startAutoRefresh() {
  stopAutoRefresh()
  timer = setInterval(() => {
    getSyncTask().then(res => {
      task.value = res.data
      if (res.data?.id) {
        getSyncDetails(res.data.id).then(r => {
          details.value = r.data || []
        }).catch(() => {})
      }
      if (!res.data?.running) {
        stopAutoRefresh()
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

// ============================================================
// 发起同步
// ============================================================

function openForm() {
  form.value = { dataSourceKey: dsOptions.value[0]?.key || '', pageSize: 10000 }
  forceMode.value = false
  showForm.value = true
}

function openForceForm() {
  form.value = { dataSourceKey: dsOptions.value[0]?.key || '', pageSize: 10000 }
  forceMode.value = true
  showForm.value = true
}

async function handleStart() {
  if (!form.value.dataSourceKey) return
  submitting.value = true
  try {
    if (forceMode.value) {
      await forceRefresh(form.value.dataSourceKey, form.value.pageSize)
    } else {
      await startSync(form.value.dataSourceKey, form.value.pageSize)
    }
    showForm.value = false
    await loadTask()
  } catch (e) {
    taskError.value = (forceMode.value ? '强制刷新' : '启动同步') + '失败: ' + e.message
  } finally {
    submitting.value = false
  }
}

// ============================================================
// 辅助函数
// ============================================================

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

function slowClass(ms) {
  if (!ms || ms < 1000) return ''
  if (ms < 3000) return 'text-amber-600'
  return 'text-red-600 font-semibold'
}

function pct(val, total) {
  if (!val || !total || total === 0) return ''
  const p = (val / total) * 100
  if (p < 1) return '<1%'
  return Math.round(p) + '%'
}

function batchTotalMs(records) {
  if (!records || records.length === 0) return 0
  return records.reduce((s, r) => s + (r.totalPageMs || 0), 0)
}

function openBatchModal(rec) {
  batchModal.value = { visible: true, recordId: rec.id, businessCode: rec.businessCode, current: 1, data: null }
  loadBatchModal(1)
}

async function loadBatchModal(page) {
  if (!batchModal.value.recordId) return
  try {
    const res = await getBatchMetrics(batchModal.value.recordId, page, 50)
    batchModal.value.data = res.data
    batchModal.value.current = page
  } catch (e) {
    console.error('加载批次耗时失败', e.message)
  }
}

function fmtMs(ms) {
  if (!ms && ms !== 0) return '-'
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's'
  return Math.floor(ms / 60000) + 'm' + Math.floor((ms % 60000) / 1000) + 's'
}

function fmtRate(rec) {
  const total = (rec.saveDurationMs || 0) + (rec.fetchDurationMs || 0) + (rec.transformDurationMs || 0)
  if (total === 0) return '-'
  const rowsPerSec = Math.round((rec.pulledCount || 0) / (total / 1000))
  return rowsPerSec + '条/s'
}

function bizTotalMs(rec) {
  return (rec.fetchDurationMs || 0) + (rec.transformDurationMs || 0) + (rec.saveDurationMs || 0)
}

function bizAvgFetchMs(rec) {
  return rec.fetchPageCount ? (rec.fetchDurationMs || 0) / rec.fetchPageCount : 0
}

function bizAvgSaveMs(rec) {
  return rec.saveBatchCount ? (rec.saveDurationMs || 0) / rec.saveBatchCount : 0
}
</script>

<template>
  <div class="max-w-4xl mx-auto animate-fade-in">

    <!-- ============================================================
         错误提示
         ============================================================ -->
    <div
      v-if="taskError"
      class="mb-4 bg-red-50 border border-red-200 text-red-700 rounded-xl px-5 py-3 text-sm"
    >
      {{ taskError }}
      <button @click="taskError = ''" class="ml-3 underline">关闭</button>
    </div>

    <!-- ============================================================
         同步任务主卡片
         ============================================================ -->
    <div class="bg-white rounded-xl border border-slate-200 p-6">

      <!-- 标题栏 -->
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
            @click="openForceForm"
            class="px-5 py-2 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 transition-colors"
          >
            强制刷新
          </button>
          <button
            @click="openForm"
            class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors"
          >
            + 发起同步
          </button>
        </div>
      </div>

      <!-- 实时更新提示 -->
      <div
        v-if="task?.running"
        class="mb-4 bg-amber-50 border border-amber-200 text-amber-700 rounded-lg px-4 py-2.5 text-sm flex items-center gap-2"
      >
        <span class="inline-block w-2 h-2 bg-amber-500 rounded-full animate-pulse"></span>
        任务运行中，正在实时更新进度
      </div>

      <!-- ============================================================
           任务详情
           ============================================================ -->
      <div v-if="task && task.status !== 'IDLE'" class="space-y-4">

        <!-- 基础信息 -->
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

        <!-- 时间信息 -->
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

        <!-- 总体进度条 -->
        <div>
          <div class="flex justify-between text-xs text-slate-400 mb-1.5">
            <span>总进度</span>
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

        <!-- 运行消息 -->
        <div
          v-if="task.message && task.status === 'RUNNING'"
          class="bg-indigo-50 border border-indigo-100 text-indigo-700 rounded-lg px-4 py-2.5 text-sm"
        >
          {{ task.message }}
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

        <div v-if="task.message && task.status !== 'RUNNING' && task.status !== 'FAILED'" class="text-xs text-slate-400">
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

    <!-- ============================================================
         业务同步详情（每张表的同步状况）
         ============================================================ -->
    <div v-if="details.length > 0" class="mt-6">
      <h4 class="text-base font-semibold text-slate-800 mb-4">业务同步详情</h4>
      <div class="grid grid-cols-2 gap-4">
        <div
          v-for="rec in details"
          :key="rec.businessCode"
          class="bg-white border border-slate-200 rounded-xl p-4"
        >
          <!-- 表头：业务编码 + 状态标签 -->
          <div class="flex items-center justify-between mb-3">
            <span class="font-semibold text-slate-800">{{ rec.businessCode }}</span>
            <span
              class="px-2 py-0.5 rounded-md text-xs font-medium"
              :class="bizStatusInfo(rec.status).class"
            >
              {{ bizStatusInfo(rec.status).label }}
            </span>
          </div>

          <!-- 开始时间 -->
          <div class="text-xs text-slate-400 mb-3">
            开始时间: {{ rec.startedAt || '-' }}
            <span v-if="rec.finishedAt" class="ml-3">结束时间: {{ rec.finishedAt }}</span>
          </div>

          <!-- 统计数据（三列：已拉取 / 已落库 / 积压） -->
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

          <!-- 进度条 -->
          <div>
            <div class="flex justify-between text-xs text-slate-400 mb-1">
              <span>写入进度</span>
              <span>{{ bizProgress(rec) }}%</span>
            </div>
            <div class="w-full bg-slate-100 rounded-full h-1.5">
              <div
                class="h-1.5 rounded-full transition-all duration-500"
                :class="rec.status === 'FAILED' ? 'bg-red-500' : rec.status === 'SUCCESS' ? 'bg-emerald-500' : 'bg-indigo-500'"
                :style="{ width: bizProgress(rec) + '%' }"
              ></div>
            </div>
          </div>

          <!-- 批次概览（同步完成后展示，详细数据在下面的批次表中） -->
          <div v-if="rec.status === 'SUCCESS'" class="mt-3 pt-3 border-t border-slate-100 text-xs text-slate-400">
            <span>共 {{ rec.pageCount || '-' }} 页, {{ rec.pulledCount || 0 }} 条数据</span>
            <span v-if="rec.finishedAt" class="ml-3">完成于 {{ rec.finishedAt }}</span>
          </div>

          <!-- 批次耗时按钮（运行中也可查看，数据实时写入 sync_batch_metrics） -->
          <div v-if="rec.id && rec.pulledCount > 0" class="mt-2">
            <button
              @click="openBatchModal(rec)"
              class="text-xs text-indigo-500 hover:text-indigo-700 font-medium"
            >
              查看批次耗时 →
            </button>
          </div>

          <!-- 错误信息 -->
          <div
            v-if="rec.errorMessage"
            class="mt-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-3 py-2 text-xs"
          >
            {{ rec.errorMessage }}
          </div>
        </div>
      </div>
    </div>

    <!-- ============================================================
         发起同步弹窗
         ============================================================ -->
    <div
      v-if="showForm"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="showForm = false"
    >
      <div class="bg-white rounded-2xl w-full max-w-md mx-4 p-6 shadow-2xl">
        <h3 class="text-lg font-semibold text-slate-800 mb-5">{{ forceMode ? '强制刷新' : '发起同步' }}</h3>

        <!-- 强制刷新警告 -->
        <div
          v-if="forceMode"
          class="mb-4 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm"
        >
          <div class="font-medium mb-1">此操作将清除以下数据：</div>
          <ul class="list-disc list-inside space-y-0.5 text-xs">
            <li>中台库全部清洗数据（clean_stock / clean_trade / clean_position / clean_asset）</li>
            <li>所有同步任务记录（sync_task / sync_business_record）</li>
            <li>Redis 中的已同步 ID 缓存</li>
          </ul>
          <div class="mt-1.5 font-medium">然后重新全量同步一次。</div>
        </div>

        <div class="space-y-4">
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

          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">每页条数</label>
            <input
              v-model.number="form.pageSize"
              type="number"
              min="1"
              max="100000"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
            <div class="mt-1 text-xs text-slate-400">范围 1 ~ 100000</div>
          </div>
        </div>

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
            class="px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            :class="forceMode ? 'bg-red-600 text-white hover:bg-red-700' : 'bg-indigo-600 text-white hover:bg-indigo-700'"
          >
            {{ submitting ? '提交中...' : forceMode ? '确认强制刷新' : '开始同步' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         批次耗时弹窗
         ============================================================ -->
    <div
      v-if="batchModal.visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="batchModal.visible = false"
    >
      <div class="bg-white rounded-xl shadow-2xl w-[90vw] max-w-3xl max-h-[80vh] flex flex-col">
        <!-- 弹窗头部 -->
        <div class="flex items-center justify-between px-5 py-3 border-b border-slate-200">
          <div>
            <span class="font-semibold text-slate-800">{{ batchModal.businessCode }}</span>
            <span class="text-sm text-slate-400 ml-2">批次耗时明细</span>
          <div class="text-[11px] text-slate-500 mt-1 leading-relaxed space-y-0.5">
            <div class="font-medium">三大步骤 + 子步骤说明：</div>
            <div><b class="text-sky-600">①拉取</b> — 上游数据库查询耗时。包含 SQL 执行 + 网络传输。排队=数据在队列中等待处理的时间，排队久说明落库线程是瓶颈。</div>
            <div><b class="text-emerald-600">②转换</b> — 上游字段映射为中台字段的耗时（每行逐一转换）。高说明转换逻辑重或行数多。</div>
            <div><b class="text-indigo-600">③落库</b> — 写入中台库总耗时。子步骤从左到右：</div>
            <div class="pl-4 text-slate-400">查重=判断是否已存在 | 拆分=分成insert/update两组 | INSERT=新增写入 | 写缓存=同步Redis | 查ID=查询已有行的主键 | UPDATE=已有行更新</div>
          </div>
          </div>
          <button @click="batchModal.visible = false" class="text-slate-400 hover:text-slate-600 text-xl leading-none">&times;</button>
        </div>

        <!-- 弹窗表格 -->
        <div class="flex-1 overflow-auto px-5 py-3">
          <table v-if="batchModal.data" class="w-full text-sm text-slate-700 border-collapse">
            <!-- 表头 -->
            <thead>
              <tr class="bg-slate-50 text-slate-500 text-xs sticky top-0">
                <th class="px-3 py-2 text-left w-12">批次</th>
                <th class="px-3 py-2 text-right w-16">行数</th>
                <th class="px-3 py-2 text-center border-x-2 border-sky-200 bg-sky-50/60 w-28" colspan="2">①拉取</th>
                <th class="px-3 py-2 text-center border-r-2 border-emerald-200 bg-emerald-50/60 w-28" colspan="2">②转换</th>
                <th class="px-3 py-2 text-center border-r-2 border-indigo-200 bg-indigo-50/60" colspan="7">③落库</th>
                <th class="px-3 py-2 text-right w-24" rowspan="2">总耗时</th>
                <th class="px-3 py-2 text-right w-16" rowspan="2">速率</th>
              </tr>
              <tr class="bg-slate-50 text-slate-400 text-[10px] sticky top-[26px]">
                <!-- 拉取子列 -->
                <th class="px-3 py-1 text-right border-x-2 border-sky-200 bg-sky-50/60" colspan="2">查询数据库</th>
                <!-- 转换子列 -->
                <th class="px-3 py-1 text-right border-r-2 border-emerald-200 bg-emerald-50/60" colspan="2">字段映射</th>
                <!-- 落库子列 -->
                <th class="px-3 py-1 text-right border-l-2 border-indigo-200 bg-indigo-50/60">总</th>
                <th class="px-3 py-1 text-right bg-indigo-50/30">查重</th>
                <th class="px-3 py-1 text-right bg-indigo-50/30">拆分</th>
                <th class="px-3 py-1 text-right bg-indigo-50/30">INSERT</th>
                <th class="px-3 py-1 text-right bg-indigo-50/30">写缓存</th>
                <th class="px-3 py-1 text-right bg-indigo-50/30">查ID</th>
                <th class="px-3 py-1 text-right bg-indigo-50/30">UPDATE</th>
              </tr>
            </thead>
            <!-- 数据体 -->
            <tbody>
              <template v-for="bm in batchModal.data.records" :key="bm.batchNo">
                <!-- 主行：批次概览 -->
                <tr class="border-t border-slate-100 hover:bg-slate-50 text-xs">
                  <td class="px-3 py-2.5 font-mono font-semibold text-slate-700">{{ bm.batchNo }}</td>
                  <td class="px-3 py-2.5 text-right font-mono">{{ (bm.pulledCount || 0).toLocaleString() }}</td>
                  <!-- ①拉取 -->
                  <td class="px-3 py-2.5 text-right font-mono border-x-2 border-sky-200" :class="slowClass(bm.fetchDurationMs)">{{ fmtMs(bm.fetchDurationMs) }}</td>
                  <td class="px-2 py-2.5 text-xs text-slate-300 border-r-2 border-sky-200">{{ pct(bm.fetchDurationMs, bm.totalPageMs) }}</td>
                  <!-- ③转换 -->
                  <td class="px-3 py-2.5 text-right font-mono border-r-2 border-emerald-200" :class="slowClass(bm.transformDurationMs)">{{ fmtMs(bm.transformDurationMs) }}</td>
                  <td class="px-2 py-2.5 text-xs text-slate-300 border-r-2 border-emerald-200">{{ pct(bm.transformDurationMs, bm.totalPageMs) }}</td>
                  <!-- ④落库主 -->
                  <td class="px-3 py-2.5 text-right font-mono border-l-2 border-indigo-200" :class="slowClass(bm.saveDurationMs)">{{ fmtMs(bm.saveDurationMs) }}</td>
                  <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.cacheLookupDurationMs) }}</td>
                  <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.splitCheckMs) }}</td>
                  <td class="px-3 py-2.5 text-right font-mono" :class="slowClass(bm.insertDurationMs)">{{ fmtMs(bm.insertDurationMs) }}</td>
                  <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.cacheAddDurationMs) }}</td>
                  <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.globalIdQueryDurationMs) }}</td>
                  <td class="px-3 py-2.5 text-right font-mono" :class="slowClass(bm.updateDurationMs)">{{ fmtMs(bm.updateDurationMs) }}</td>
                  <!-- 总计/速率 -->
                  <td class="px-3 py-2.5 text-right font-mono font-semibold" :class="slowClass(bm.totalPageMs)">{{ fmtMs(bm.totalPageMs) }}</td>
                  <td class="px-3 py-2.5 text-right font-mono text-slate-400">{{ bm.rowsPerSecond ? bm.rowsPerSecond.toFixed(0) + '/s' : '-' }}</td>
                </tr>
                <!-- 子行：排队 + 设ID（次要指标折叠） -->
                <tr class="text-[10px] text-slate-300 border-0 -mt-1">
                  <td colspan="2"></td>
                  <td class="px-3 pb-1 border-x-2 border-sky-100" colspan="2">排队 {{ fmtMs(bm.queueWaitMs) }}</td>
                  <td class="px-3 pb-1 border-r-2 border-emerald-100" colspan="2"></td>
                  <td class="px-3 pb-1 border-l-2 border-indigo-100" colspan="7">
                    设ID {{ fmtMs(bm.setIdDurationMs) }}
                    <span v-if="bm.insertCount"> | 新增 {{ bm.insertCount }} 行</span>
                    <span v-if="bm.updateCount"> | 更新 {{ bm.updateCount }} 行</span>
                  </td>
                  <td colspan="2"></td>
                </tr>
              </template>
              <tr v-if="!batchModal.data.records || batchModal.data.records.length === 0">
                <td colspan="15" class="px-3 py-8 text-center text-slate-400 text-sm">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 弹窗分页 -->
        <div v-if="batchModal.data && batchModal.data.pages > 1"
          class="flex items-center justify-end gap-3 px-5 py-3 border-t border-slate-200 text-xs">
          <button
            @click="loadBatchModal(batchModal.current - 1)"
            :disabled="batchModal.current <= 1"
            class="px-3 py-1 rounded border border-slate-200 disabled:opacity-30 hover:bg-slate-50"
          >上一页</button>
          <span class="text-slate-400">{{ batchModal.current }}/{{ batchModal.data.pages }}</span>
          <button
            @click="loadBatchModal(batchModal.current + 1)"
            :disabled="batchModal.current >= batchModal.data.pages"
            class="px-3 py-1 rounded border border-slate-200 disabled:opacity-30 hover:bg-slate-50"
          >下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>
