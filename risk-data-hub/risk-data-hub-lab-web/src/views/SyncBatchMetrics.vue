<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBatchMetrics } from '@/api/index.js'

const route = useRoute()
const router = useRouter()
const recordId = Number(route.params.recordId)
const businessCode = route.query.biz || ''

const data = ref(null)
const loading = ref(true)

const NODES = [
  { key: 'fetchStartedAt', label: '拉取开始' },
  { key: 'fetchFinishedAt', label: '拉取完成' },
  { key: 'fetchQueuedAt', label: '入队开始' },
  { key: 'fetchQueuedFinishedAt', label: '入队完成' },
  { key: 'idGenStartedAt', label: 'ID生成开始' },
  { key: 'idGenFinishedAt', label: 'ID生成完成' },
  { key: 'transformStartedAt', label: '转换开始' },
  { key: 'transformFinishedAt', label: '转换完成' },
  { key: 'insertStartedAt', label: '新增写入开始' },
  { key: 'insertFinishedAt', label: '新增写入完成' },
  { key: 'updateStartedAt', label: '更新写入开始' },
  { key: 'updateFinishedAt', label: '更新写入完成' },
]

const DURATION_PAIRS = [
  { start: 'fetchStartedAt', end: 'fetchFinishedAt' },
  { start: 'fetchQueuedAt', end: 'fetchQueuedFinishedAt' },
  { start: 'idGenStartedAt', end: 'idGenFinishedAt' },
  { start: 'transformStartedAt', end: 'transformFinishedAt' },
  { start: 'insertStartedAt', end: 'insertFinishedAt' },
  { start: 'updateStartedAt', end: 'updateFinishedAt' },
]

onMounted(() => loadPage(1))

async function loadPage(page) {
  loading.value = true
  try {
    const res = await getBatchMetrics(recordId, page, 50)
    if (res.data?.records) {
      res.data.records = res.data.records.map(buildRecord)
    }
    data.value = res.data
  } catch (e) {
    console.error('加载失败', e.message)
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/sync')
}

function toTs(t) {
  if (!t) return null
  const ts = new Date(t).getTime()
  return Number.isNaN(ts) ? null : ts
}

function buildRecord(raw) {
  // 计算每对开始→结束的耗时（毫秒），只挂在结束节点上
  const pairDuration = {}
  for (const p of DURATION_PAIRS) {
    const startTs = toTs(raw[p.start])
    const endTs = toTs(raw[p.end])
    if (startTs !== null && endTs !== null && endTs >= startTs) {
      pairDuration[p.end] = endTs - startTs
    }
  }

  // 找出本批最大耗时，用于相对着色
  const durations = Object.values(pairDuration).filter(v => v !== null)
  const maxDur = durations.length > 0 ? Math.max(...durations) : 0

  let prevTs = null
  let prevLabel = null
  const nodes = NODES.map((n, idx) => {
    const ts = toTs(raw[n.key])
    const orderOk = ts === null || prevTs === null || ts >= prevTs
    const dur = pairDuration[n.key] ?? null
    const node = {
      idx: idx + 1,
      key: n.key,
      label: n.label,
      time: raw[n.key] || null,
      ts,
      duration: dur,
      durLevel: dur !== null && maxDur > 0
        ? dur / maxDur >= 0.8 ? 'peak'
          : dur / maxDur >= 0.5 ? 'slow'
            : dur / maxDur >= 0.2 ? 'normal'
              : 'fast'
        : null,
      orderOk,
      orderIssue: !orderOk ? `早于「${prevLabel}」` : null,
    }
    if (ts !== null) {
      prevTs = ts
      prevLabel = n.label
    }
    return node
  })
  return {
    ...raw,
    nodes,
    hasOrderIssue: nodes.some(n => n.orderOk === false),
    missingCount: nodes.filter(n => !n.time).length,
  }
}

function fmtDuration(ms) {
  if (ms === null || ms === undefined) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const m = Math.floor(ms / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return `${m}m${s}s`
}

function fmtTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '—'
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  const ms = String(d.getMilliseconds()).padStart(3, '0')
  return `${y}-${mo}-${da} ${hh}:${mm}:${ss}.${ms}`
}
</script>

<template>
  <div class="max-w-7xl mx-auto">

    <div class="flex items-center gap-2 text-sm text-slate-400 mb-4">
      <button @click="goBack" class="hover:text-indigo-600 transition-colors">← 同步任务</button>
      <span>/</span>
      <span class="text-slate-700 font-medium">{{ businessCode }} 批次时间节点</span>
    </div>

    <div v-if="loading" class="py-12 text-center text-slate-400 text-sm">加载中...</div>

    <div v-else-if="data" class="space-y-4">

      <div class="bg-white border border-slate-200 rounded-xl shadow-sm px-5 py-3 flex items-center gap-6 text-sm text-slate-500">
        <span>共 <b class="text-slate-700">{{ data.total || 0 }}</b> 批</span>
        <span class="text-xs text-slate-400">每对开始→结束自动计算耗时，缺失或倒序的节点不计算</span>
      </div>

      <div
        v-for="bm in data.records"
        :key="bm.batchNo"
        class="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden"
      >
        <!-- 批次摘要 -->
        <div class="px-5 py-3 border-b border-slate-100 flex flex-wrap items-center gap-x-6 gap-y-1 text-sm">
          <span class="font-mono font-semibold text-slate-800">批次 #{{ bm.batchNo }}</span>
          <span class="text-slate-500">
            行数 <b class="text-slate-700 font-mono">{{ (bm.pulledCount || 0).toLocaleString() }}</b>
            <span class="text-slate-300 ml-1">(增 {{ bm.insertCount || 0 }} / 改 {{ bm.updateCount || 0 }})</span>
          </span>
          <span class="text-slate-400 text-xs font-mono">记录于 {{ fmtTime(bm.recordedAt) }}</span>
          <span v-if="bm.hasOrderIssue" class="text-xs text-red-600 bg-red-50 px-2 py-0.5 rounded">
            ⚠ 存在时间倒序（历史数据可能因采集 bug 导致）
          </span>
          <span v-if="bm.missingCount > 0" class="text-xs text-amber-600 bg-amber-50 px-2 py-0.5 rounded">
            {{ bm.missingCount }} 个节点无时间戳
          </span>
        </div>

        <!-- 时间节点表 -->
        <div class="overflow-x-auto">
          <table class="w-full text-sm border-collapse">
            <thead>
              <tr class="bg-slate-50 text-slate-500 text-xs border-b border-slate-200">
                <th class="px-4 py-2 text-left w-10">#</th>
                <th class="px-4 py-2 text-left w-44">节点</th>
                <th class="px-4 py-2 text-left">时间戳</th>
                <th class="px-4 py-2 text-right w-28">耗时</th>
                <th class="px-4 py-2 text-left w-32">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="node in bm.nodes"
                :key="node.key"
                class="border-t border-slate-100"
                :class="!node.orderOk ? 'bg-red-50/60' : 'hover:bg-slate-50/60'"
              >
                <td class="px-4 py-2 font-mono text-slate-400 text-xs">{{ node.idx }}</td>
                <td class="px-4 py-2 text-slate-700">{{ node.label }}</td>
                <td class="px-4 py-2 font-mono text-slate-800 tracking-tight">
                  {{ fmtTime(node.time) }}
                </td>
                <td class="px-4 py-2 text-right font-mono text-xs font-semibold"
                  :class="{
                    'text-emerald-600': node.durLevel === 'fast',
                    'text-indigo-600': node.durLevel === 'normal',
                    'text-amber-600': node.durLevel === 'slow',
                    'text-red-600': node.durLevel === 'peak',
                    'text-slate-200': !node.durLevel
                  }"
                >
                  {{ fmtDuration(node.duration) }}
                </td>
                <td class="px-4 py-2 text-xs">
                  <span v-if="!node.time" class="text-slate-300">缺失</span>
                  <span v-else-if="!node.orderOk" class="text-red-600">{{ node.orderIssue }}</span>
                  <span v-else class="text-emerald-600">正常</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="!data.records || data.records.length === 0"
        class="bg-white border border-slate-200 rounded-xl py-12 text-center text-slate-400 text-sm">
        暂无数据
      </div>

      <!-- 分页 -->
      <div v-if="data.pages > 1"
        class="flex items-center justify-between bg-white border border-slate-200 rounded-xl px-5 py-3 text-sm">
        <span class="text-xs text-slate-400">共 {{ data.total }} 条，{{ data.pages }} 页</span>
        <div class="flex items-center gap-3">
          <button @click="loadPage(data.current - 1)" :disabled="data.current <= 1"
            class="px-3 py-1.5 rounded border border-slate-200 disabled:opacity-30 hover:bg-slate-50 text-xs">上一页</button>
          <span class="text-slate-500 text-xs">{{ data.current }} / {{ data.pages }}</span>
          <button @click="loadPage(data.current + 1)" :disabled="data.current >= data.pages"
            class="px-3 py-1.5 rounded border border-slate-200 disabled:opacity-30 hover:bg-slate-50 text-xs">下一页</button>
        </div>
      </div>
    </div>

    <div v-else class="py-12 text-center text-slate-400 text-sm">未找到批次数据</div>
  </div>
</template>
