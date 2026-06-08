<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getSyncTasks, startSync, fullSync, listDatasources } from '@/api/index.js'

const router = useRouter()

const taskList = ref([])
const taskPage = ref({ total: 0, pages: 0, current: 1 })
const loading = ref(false)
const error = ref('')

// 弹窗
const showForm = ref(false)
const submitting = ref(false)
const syncType = ref('INCREMENTAL')
const form = ref({ dataSourceKey: '', pageSize: 10000 })
const dsOptions = ref([])

onMounted(async () => {
  await loadList()
  loadDsOptions()
})

async function loadList(page = 1) {
  loading.value = true
  error.value = ''
  try {
    const res = await getSyncTasks(page, 20)
    taskList.value = res.data?.records || []
    taskPage.value = { total: res.data?.total || 0, pages: res.data?.pages || 0, current: res.data?.current || 1 }
  } catch (e) {
    error.value = '加载失败: ' + e.message
  } finally {
    loading.value = false
  }
}

async function loadDsOptions() {
  try {
    const res = await listDatasources()
    dsOptions.value = (res.data || []).filter(ds => ds.datasourceType !== 'HUB')
  } catch (e) {
    console.error('[同步] 获取数据源列表失败', e.message)
  }
}

function openForm(type) {
  form.value = { dataSourceKey: dsOptions.value[0]?.key || '', pageSize: 10000 }
  syncType.value = type
  showForm.value = true
}

async function handleStart() {
  if (!form.value.dataSourceKey) return
  submitting.value = true
  try {
    if (syncType.value === 'FULL') {
      await fullSync(form.value.dataSourceKey, form.value.pageSize)
    } else {
      await startSync(form.value.dataSourceKey, form.value.pageSize)
    }
    showForm.value = false
    await loadList()
  } catch (e) {
    error.value = '发起同步失败: ' + e.message
  } finally {
    submitting.value = false
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
  return map[status] || { label: status || '-', class: 'bg-slate-50 text-slate-500' }
}
</script>

<template>
  <div class="max-w-5xl mx-auto animate-fade-in">

    <div v-if="error" class="mb-4 bg-red-50 border border-red-200 text-red-700 rounded-xl px-5 py-3 text-sm">
      {{ error }}
      <button @click="error = ''" class="ml-3 underline">关闭</button>
    </div>

    <!-- 标题 + 操作按钮 -->
    <div class="flex items-center justify-between mb-5">
      <h3 class="text-base font-semibold text-slate-800">同步任务</h3>
      <div class="flex gap-2">
        <button @click="openForm('INCREMENTAL')"
          class="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors">
          + 增量同步
        </button>
        <button @click="openForm('FULL')"
          class="px-5 py-2 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 transition-colors">
          全量同步
        </button>
      </div>
    </div>

    <!-- 任务列表 -->
    <div class="bg-white border border-slate-200 rounded-xl overflow-hidden">
      <table class="w-full text-sm border-collapse">
        <thead>
          <tr class="bg-slate-50 text-slate-500 text-xs border-b border-slate-200">
            <th class="px-4 py-2.5 text-left w-20">ID</th>
            <th class="px-4 py-2.5 text-left w-16">类型</th>
            <th class="px-4 py-2.5 text-left">数据源</th>
            <th class="px-4 py-2.5 text-left w-20">状态</th>
            <th class="px-4 py-2.5 text-right w-20">拉取</th>
            <th class="px-4 py-2.5 text-right w-20">落库</th>
            <th class="px-4 py-2.5 text-right w-20">进度</th>
            <th class="px-4 py-2.5 text-left w-40">提交时间</th>
            <th class="px-4 py-2.5 text-left w-28">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in taskList" :key="t.id"
            class="border-t border-slate-100 hover:bg-slate-50/60">
            <td class="px-4 py-2.5 font-mono text-xs text-slate-500">#{{ t.id }}</td>
            <td class="px-4 py-2.5">
              <span class="text-xs px-2 py-0.5 rounded font-medium"
                :class="t.syncType === 'FULL' ? 'bg-red-50 text-red-600' : 'bg-sky-50 text-sky-600'">
                {{ t.syncType === 'FULL' ? '全量' : '增量' }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-slate-700">{{ t.dataSourceKey }}</td>
            <td class="px-4 py-2.5">
              <span class="px-2 py-0.5 rounded-md text-xs font-medium" :class="statusInfo(t.status).class">
                {{ statusInfo(t.status).label }}
              </span>
            </td>
            <td class="px-4 py-2.5 font-mono text-xs text-slate-600 text-right">{{ t.totalPulledCount ?? 0 }}</td>
            <td class="px-4 py-2.5 font-mono text-xs text-slate-600 text-right">{{ t.totalSavedCount ?? 0 }}</td>
            <td class="px-4 py-2.5 text-right">
              <span class="text-xs font-mono" :class="t.progress >= 100 ? 'text-emerald-600' : 'text-slate-600'">
                {{ t.progress || 0 }}%
              </span>
            </td>
            <td class="px-4 py-2.5 text-xs text-slate-400">{{ t.submittedAt || '-' }}</td>
            <td class="px-4 py-2.5">
              <button @click="router.push('/sync/' + t.id)"
                class="text-xs text-indigo-600 hover:text-indigo-800 font-medium">
                查看详情 →
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="!loading && taskList.length === 0" class="py-12 text-center text-slate-400 text-sm">
        暂无同步任务
      </div>
      <div v-if="loading" class="py-8 text-center text-slate-400 text-sm">加载中...</div>

      <!-- 分页 -->
      <div v-if="taskPage.pages > 1"
        class="px-4 py-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-400">
        <span>共 {{ taskPage.total }} 条，{{ taskPage.pages }} 页</span>
        <div class="flex items-center gap-2">
          <button @click="loadList(taskPage.current - 1)" :disabled="taskPage.current <= 1"
            class="px-3 py-1.5 rounded border border-slate-200 disabled:opacity-30 hover:bg-slate-50">上一页</button>
          <span>{{ taskPage.current }} / {{ taskPage.pages }}</span>
          <button @click="loadList(taskPage.current + 1)" :disabled="taskPage.current >= taskPage.pages"
            class="px-3 py-1.5 rounded border border-slate-200 disabled:opacity-30 hover:bg-slate-50">下一页</button>
        </div>
      </div>
    </div>

    <!-- 发起同步弹窗 -->
    <div v-if="showForm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="showForm = false">
      <div class="bg-white rounded-2xl w-full max-w-md mx-4 p-6 shadow-2xl">
        <h3 class="text-lg font-semibold text-slate-800 mb-5">{{ syncType === 'FULL' ? '全量同步' : '增量同步' }}</h3>

        <div v-if="syncType === 'FULL'"
          class="mb-4 bg-amber-50 border border-amber-200 text-amber-700 rounded-lg px-4 py-3 text-sm">
          全量同步从头开始读取全部上游数据，通过 upsert 写入中台库，<b>不会删除已有数据</b>。
          增量同步则从上一次中断位置继续。
        </div>

        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">数据源</label>
            <select v-model="form.dataSourceKey"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500">
              <option value="" disabled>请选择数据源</option>
              <option v-for="ds in dsOptions" :key="ds.key" :value="ds.key">{{ ds.name }} ({{ ds.key }})</option>
            </select>
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-700 mb-1">每页条数</label>
            <input v-model.number="form.pageSize" type="number" min="1" max="100000"
              class="w-full px-3.5 py-2.5 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
          </div>
        </div>

        <div class="flex justify-end gap-3 mt-6 pt-4 border-t border-slate-100">
          <button @click="showForm = false" class="px-5 py-2 text-sm text-slate-600 hover:text-slate-800 font-medium">
            取消
          </button>
          <button @click="handleStart" :disabled="!form.dataSourceKey || submitting"
            class="px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            :class="syncType === 'FULL' ? 'bg-red-600 text-white hover:bg-red-700' : 'bg-indigo-600 text-white hover:bg-indigo-700'">
            {{ submitting ? '提交中...' : '开始同步' }}
          </button>
        </div>
      </div>
    </div>

  </div>
</template>
