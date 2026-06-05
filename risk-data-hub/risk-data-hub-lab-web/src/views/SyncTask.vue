<!--
  SyncTask — 同步任务页

  这是整个系统的核心功能页面，负责：
  1. 查看当前同步任务的状态（进度、拉取/落库数量、运行信息）
  2. 发起新的同步任务（选择数据源、设置分页大小）
  3. 任务运行中自动刷新（每 3 秒查一次状态）

  数据来源：
  - getSyncTask() → POST /api-hub-sync-task → 当前任务状态
  - startSync(key, pageSize) → POST /api-hub-sync → 提交新任务
  - listDatasources() → POST /api-datasource-list → 数据源列表（不含中台库）

  实时进度：任务运行中，后端每 1 秒将当前进度写入数据库，前端每 3 秒轮询刷新。
  进度信息通过 message 字段展示（如"正在同步 STOCK: 已拉取 200, 已落库 150"）。
-->
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getSyncTask, startSync, listDatasources } from '@/api/index.js'

// ============================================================
// 数据状态
// ============================================================

// task：当前同步任务对象，来自后端。包含 status/progress/message/pulledCount/savedCount 等
//        无任务时返回 status=IDLE 的空任务
// loading：页面数据加载中（首次加载和手动刷新时 = true）
// taskError：任务相关的错误信息，显示在页面顶部的红色提示条
const task = ref(null)
const loading = ref(false)
const taskError = ref('')

// ============================================================
// 发起同步弹窗状态
// ============================================================

// showForm：控制"发起同步"弹窗的显示/隐藏
// submitting：提交中标志（防止重复点击）
const showForm = ref(false)
const submitting = ref(false)

// 同步任务表单
const form = ref({
  dataSourceKey: '',  // 选择的数据源 key
  pageSize: 100       // 每页拉取条数
})

// 可选数据源列表（来自 listDatasources 接口，已过滤掉 HUB 类型）
const dsOptions = ref([])

// 自动刷新定时器 ID（用于在组件卸载时清理）
let timer = null

// ============================================================
// 生命周期
// ============================================================

// 组件挂载时：加载当前任务 + 获取数据源列表（用于弹窗下拉框）
onMounted(async () => {
  await loadTask()
  loadDsOptions()
})

// 组件卸载时：清除定时器，防止内存泄漏
onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// ============================================================
// 加载当前任务
// 从后端获取最近一条同步任务的状态
// ============================================================
async function loadTask() {
  loading.value = true
  taskError.value = ''
  try {
    const res = await getSyncTask()
    task.value = res.data

    // 任务正在运行 → 启动自动刷新，每 3 秒查一次最新状态
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

// ============================================================
// 加载数据源选项（供弹窗下拉框使用）
// 注意：过滤掉 datasourceType === 'HUB' 的中台库，自己不能同步自己
// ============================================================
async function loadDsOptions() {
  try {
    const res = await listDatasources()
    // filter 过滤掉 HUB 类型数据源（中台库不能作为同步来源）
    dsOptions.value = (res.data || []).filter(ds => ds.datasourceType !== 'HUB')
  } catch (e) {
    console.error('[同步] 获取数据源列表失败', e.message)
  }
}

// ============================================================
// 自动刷新机制
// 任务运行时每 3 秒轮询后端，获取最新进度
// 任务完成或失败后自动停止刷新
// ============================================================

// 启动 3 秒定时器，不断拉取最新任务状态
function startAutoRefresh() {
  stopAutoRefresh()
  timer = setInterval(() => {
    getSyncTask().then(res => {
      task.value = res.data
      // 任务不再运行 → 停止刷新
      if (!res.data?.running) {
        stopAutoRefresh()
      }
    }).catch(() => {}) // 轮询出错不处理，等待下次
  }, 3000)
}

// 停止自动刷新
function stopAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

// ============================================================
// 发起同步
// 用户选择数据源和分页大小后提交同步任务
// ============================================================

// 打开"发起同步"弹窗，默认选中第一个数据源
function openForm() {
  form.value = { dataSourceKey: dsOptions.value[0]?.key || '', pageSize: 100 }
  showForm.value = true
}

// 提交同步任务 → 关闭弹窗 → 重新加载任务（此时应该看到 QUEUED 状态）
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

// ============================================================
// 辅助函数
// ============================================================

// 根据任务状态返回显示文字和标签样式
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

    <!-- ============================================================
         错误提示（红色）
         加载失败或操作失败时显示，可手动关闭
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

      <!-- ============================================================
           卡片标题栏：左侧标题+状态标签，右侧刷新/发起按钮
           ============================================================ -->
      <div class="flex items-center justify-between mb-5">
        <div class="flex items-center gap-3">
          <h3 class="text-base font-semibold text-slate-800">同步任务</h3>
          <!-- 状态标签（排队中/运行中/已完成/失败/空闲） -->
          <span
            v-if="task"
            class="px-2.5 py-0.5 rounded-md text-xs font-medium"
            :class="statusInfo(task.status).class"
          >
            {{ statusInfo(task.status).label }}
          </span>
        </div>

        <!-- 操作按钮 -->
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

      <!-- ============================================================
           自动刷新提示（任务运行时显示）
           琥珀色背景 + 闪烁圆点，提醒用户当前正在实时更新
           ============================================================ -->
      <div
        v-if="task?.running"
        class="mb-4 bg-amber-50 border border-amber-200 text-amber-700 rounded-lg px-4 py-2.5 text-sm flex items-center gap-2"
      >
        <span class="inline-block w-2 h-2 bg-amber-500 rounded-full animate-pulse"></span>
        任务运行中，正在实时更新进度
      </div>

      <!-- ============================================================
           任务详情（当有任务且状态不是 IDLE 时显示）
           展示数据源信息、时间、进度条、统计数字、消息
           ============================================================ -->
      <div v-if="task && task.status !== 'IDLE'" class="space-y-4">

        <!-- 基础信息行：数据源 key / 名称 / 分页大小 -->
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

        <!-- 时间信息行：提交/开始/结束时间 -->
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

        <!-- ============================================================
             进度条
             根据状态改变颜色：进行中=靛蓝，失败=红色，完成=绿色
             后端每 1 秒更新一次进度值，前端每 3 秒轮询获取
             ============================================================ -->
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

        <!-- ============================================================
             运行消息（任务运行时显示当前正在同步的业务类型和进度）
             后端在 sync 过程中写入 message 字段，如：
             "正在同步 STOCK: 已拉取 200, 已落库 150"
             前端轮询时自动获取并显示
             ============================================================ -->
        <div
          v-if="task.message && task.status === 'RUNNING'"
          class="bg-indigo-50 border border-indigo-100 text-indigo-700 rounded-lg px-4 py-2.5 text-sm"
        >
          {{ task.message }}
        </div>

        <!-- 统计行：拉取条数 / 落库条数 -->
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

        <!-- 错误信息（任务失败时显示红色详情） -->
        <div
          v-if="task.errorMessage"
          class="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-2.5 text-xs"
        >
          {{ task.errorMessage }}
        </div>

        <!-- 常规消息（非失败状态的消息，如"同步任务已完成"） -->
        <div v-if="task.message && task.status !== 'RUNNING' && task.status !== 'FAILED'" class="text-xs text-slate-400">
          {{ task.message }}
        </div>
      </div>

      <!-- 空闲状态：没有任务时显示 -->
      <div v-else-if="!loading" class="py-12 text-center text-slate-400 text-sm">
        暂无同步任务，点击「发起同步」开始
      </div>

      <!-- 加载中状态 -->
      <div v-if="loading" class="py-12 text-center text-slate-400 text-sm">加载中...</div>
    </div>

    <!-- ============================================================
         发起同步弹窗
         用 CSS 遮罩层模拟，点击遮罩关闭
         包含：数据源下拉选择 + 分页大小输入
         ============================================================ -->
    <div
      v-if="showForm"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="showForm = false"
    >
      <div class="bg-white rounded-2xl w-full max-w-md mx-4 p-6 shadow-2xl">
        <h3 class="text-lg font-semibold text-slate-800 mb-5">发起同步</h3>

        <!-- 表单区域 -->
        <div class="space-y-4">

          <!-- 选择数据源（下拉框） -->
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">数据源</label>
            <select
              v-model="form.dataSourceKey"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            >
              <option value="" disabled>请选择数据源</option>
              <!-- 遍历 dsOptions，只显示过滤后的非 HUB 数据源 -->
              <option v-for="ds in dsOptions" :key="ds.key" :value="ds.key">
                {{ ds.name }} ({{ ds.key }})
              </option>
            </select>
            <div v-if="dsOptions.length === 0" class="mt-1 text-xs text-slate-400">
              暂无可用数据源，请先在「数据源管理」页面注册
            </div>
          </div>

          <!-- 每页条数（数字输入框） -->
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

        <!-- 底部按钮 -->
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
