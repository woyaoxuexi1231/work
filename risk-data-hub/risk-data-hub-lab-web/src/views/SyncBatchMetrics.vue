<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBatchMetrics } from '@/api/index.js'

const route = useRoute()
const router = useRouter()
const recordId = Number(route.params.recordId)
const businessCode = route.query.biz || ''
const pageTitle = ref('加载中...')

const data = ref(null)
const loading = ref(true)

onMounted(async () => {
  pageTitle.value = businessCode + ' 批次耗时明细'
  await loadPage(1)
})

async function loadPage(page) {
  loading.value = true
  try {
    const res = await getBatchMetrics(recordId, page, 50)
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

// ====== 辅助函数 ======
function fmtMs(ms) {
  if (!ms && ms !== 0) return '-'
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's'
  return Math.floor(ms / 60000) + 'm' + Math.floor((ms % 60000) / 1000) + 's'
}

function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return hh + ':' + mm + ':' + ss
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

function avgPageMs(total, count) {
  if (!total || !count) return '-'
  return fmtMs(total / count)
}
</script>

<template>
  <div class="max-w-7xl mx-auto">

    <!-- 面包屑导航 -->
    <div class="flex items-center gap-2 text-sm text-slate-400 mb-4">
      <button @click="goBack" class="hover:text-indigo-600 transition-colors">← 同步任务</button>
      <span>/</span>
      <span class="text-slate-700 font-medium">{{ businessCode }} 批次耗时明细</span>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="py-12 text-center text-slate-400 text-sm">加载中...</div>

    <!-- 数据表格 -->
    <div v-else-if="data" class="bg-white border border-slate-200 rounded-xl shadow-sm">
      <!-- 统计摘要栏 -->
      <div class="px-5 py-3 border-b border-slate-100 flex items-center gap-6 text-sm text-slate-500">
        <span>共 <b class="text-slate-700">{{ data.total || 0 }}</b> 批</span>
        <span>总耗时 <b class="text-slate-700">{{ data.records ? avgPageMs(data.records.reduce((s,r) => s + (r.totalPageMs||0), 0), data.records.length) : '-' }}</b></span>
        <span class="text-xs text-slate-400 ml-auto">左/右拖动表格查看更多列</span>
      </div>

      <!-- 步骤说明 -->
      <div class="px-5 py-2.5 border-b border-slate-100 text-[11px] text-slate-400 leading-relaxed">
        <b class="text-sky-600">①拉取</b>=上游查询 ｜ <b>排队</b>=队列等待 ｜
        <b class="text-emerald-600">②转换</b>=字段映射 ｜ <b>ID生成</b>=Leaf批量获ID ｜
        <b class="text-indigo-600">落库</b>=写入总时 ｜ <b>查重</b>=判存 ｜ <b>拆分</b>=分组 ｜
        <b>INSERT</b>=新增 ｜ <b>写缓存</b>=同步Redis ｜
        <b>查ID</b>=查主键 ｜ <b>设ID</b>=设主键 ｜ <b>UPDATE</b>=更新 ｜
        <b>行数(增/改)</b>=总行数(新增行数/更新行数)
      </div>

      <!-- 表格区域（左右可滑动） -->
      <div class="overflow-x-auto px-5 py-3">
        <table class="w-full text-sm text-slate-700 border-collapse min-w-[1100px]">
          <thead>
            <tr class="bg-slate-50 text-slate-500 text-xs border-b border-slate-200">
              <th class="px-3 py-2.5 text-left w-14 sticky left-0 bg-slate-50 z-10">批次</th>
              <th class="px-3 py-2.5 text-left w-28 sticky left-14 bg-slate-50 z-10">起止时间</th>
              <th class="px-3 py-2.5 text-right w-24">行数(增/改)</th>
              <th class="px-3 py-2.5 text-right w-24 bg-sky-50 text-sky-700 border-x border-sky-200">①拉取</th>
              <th class="px-3 py-2.5 text-right w-24 border-r-2 border-sky-200">排队</th>
              <th class="px-3 py-2.5 text-right w-24 bg-emerald-50 text-emerald-700 border-x border-emerald-200">②转换</th>
              <th class="px-3 py-2.5 text-right w-16">%</th>
              <th class="px-3 py-2.5 text-right w-24 border-r-2 border-emerald-200">ID生成</th>
              <th class="px-3 py-2.5 text-right w-24 bg-indigo-50 text-indigo-700 border-x border-indigo-200">落库</th>
              <th class="px-3 py-2.5 text-right w-20">查重</th>
              <th class="px-3 py-2.5 text-right w-16">拆分</th>
              <th class="px-3 py-2.5 text-right w-24">INSERT</th>
              <th class="px-3 py-2.5 text-right w-24">写缓存</th>
              <th class="px-3 py-2.5 text-right w-20">查ID</th>
              <th class="px-3 py-2.5 text-right w-20">设ID</th>
              <th class="px-3 py-2.5 text-right w-24 border-r-2 border-indigo-200">UPDATE</th>
              <th class="px-3 py-2.5 text-right w-24">总耗时</th>
              <th class="px-3 py-2.5 text-right w-20">速率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(bm, idx) in data.records" :key="bm.batchNo"
              class="border-t border-slate-100 hover:bg-slate-50">
              <td class="px-3 py-2.5 font-mono font-semibold text-slate-700 sticky left-0 bg-white z-10">{{ bm.batchNo }}</td>
              <td class="px-3 py-2.5 font-mono text-[10px] text-slate-400 sticky left-14 bg-white z-10">{{ fmtTime(bm.batchStartedAt) }}→{{ fmtTime(bm.batchFinishedAt) }}</td>
              <td class="px-3 py-2.5 text-right font-mono text-xs">
                {{ (bm.pulledCount || 0).toLocaleString() }}
                <span class="text-slate-300">({{ bm.insertCount || 0 }}/{{ bm.updateCount || 0 }})</span>
              </td>
              <td class="px-3 py-2.5 text-right font-mono bg-sky-50/30 border-x border-sky-200" :class="slowClass(bm.fetchDurationMs)">{{ fmtMs(bm.fetchDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono text-slate-400 border-r-2 border-sky-200">{{ fmtMs(bm.queueWaitMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono bg-emerald-50/30 border-x border-emerald-200" :class="slowClass(bm.transformDurationMs)">{{ fmtMs(bm.transformDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono text-slate-300">{{ pct(bm.transformDurationMs, bm.totalPageMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono border-r-2 border-emerald-200" :class="slowClass(bm.idGenDurationMs)">{{ fmtMs(bm.idGenDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono bg-indigo-50/30 border-x border-indigo-200" :class="slowClass(bm.saveDurationMs)">{{ fmtMs(bm.saveDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.cacheLookupDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.splitCheckMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono" :class="slowClass(bm.insertDurationMs)">{{ fmtMs(bm.insertDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.cacheAddDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.globalIdQueryDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.setIdDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono border-r-2 border-indigo-200" :class="slowClass(bm.updateDurationMs)">{{ fmtMs(bm.updateDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono font-semibold" :class="slowClass(bm.totalPageMs)">{{ fmtMs(bm.totalPageMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono text-slate-400">{{ bm.rowsPerSecond ? bm.rowsPerSecond.toFixed(0) + '/s' : '-' }}</td>
            </tr>
            <tr v-if="!data.records || data.records.length === 0">
              <td colspan="18" class="px-3 py-8 text-center text-slate-400">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div v-if="data.pages > 1"
        class="flex items-center justify-between px-5 py-3 border-t border-slate-100 text-sm">
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

    <!-- 无数据 -->
    <div v-else class="py-12 text-center text-slate-400 text-sm">未找到批次数据</div>
  </div>
</template>
