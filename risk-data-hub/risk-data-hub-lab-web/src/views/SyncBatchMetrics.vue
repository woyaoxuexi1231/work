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
    // 给每条记录补充前端计算字段（后端只存时间戳，不存耗时）
    if (res.data?.records) {
      res.data.records = res.data.records.map(calcRec)
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

// ====== 耗时计算（从时间戳差值得出） ======

/** 两个 ISO 时间戳的毫秒差 */
function msDiff(a, b) {
  if (!a || !b) return undefined
  return new Date(b).getTime() - new Date(a).getTime()
}

/**
 * 将后端原始记录（只有时间戳）转换为含前端计算耗时字段的对象。
 * 子步骤按 save 内顺序排列：查缓存→新增写入→写缓存→查主键→设主键→更新写入。
 */
function calcRec(raw) {
  const r = { ...raw }

  // 主要阶段
  r.fetchDurationMs = msDiff(raw.fetchStartedAt, raw.fetchQueuedAt)
  r.queueWaitMs = msDiff(raw.fetchQueuedAt, raw.processStartedAt)
  r.idGenDurationMs = msDiff(raw.idGenStartedAt, raw.idGenFinishedAt)
  r.transformDurationMs = msDiff(raw.transformStartedAt, raw.transformFinishedAt)
  r.saveDurationMs = msDiff(raw.saveStartedAt, raw.saveFinishedAt)

  // 落库子步骤（前一个完成时间 → 当前完成时间）
  r.cacheLookupDurationMs = msDiff(raw.saveStartedAt, raw.cacheLookupFinishedAt)
  r.insertDurationMs = msDiff(raw.cacheLookupFinishedAt, raw.insertFinishedAt)
  r.cacheAddDurationMs = msDiff(raw.insertFinishedAt, raw.cacheAddFinishedAt)
  r.globalIdQueryDurationMs = msDiff(raw.cacheAddFinishedAt, raw.globalIdQueryFinishedAt)
  r.setIdDurationMs = msDiff(raw.globalIdQueryFinishedAt, raw.setIdFinishedAt)
  r.updateDurationMs = msDiff(raw.setIdFinishedAt, raw.updateFinishedAt)

  // 本批起止时间 = 拉取开始 → 落库完成
  r.batchStartedAt = raw.fetchStartedAt
  r.batchFinishedAt = raw.saveFinishedAt

  // 总耗时 & 速率
  r.totalPageMs = msDiff(raw.fetchStartedAt, raw.saveFinishedAt)
  r.rowsPerSecond = r.totalPageMs > 0 ? Math.round((raw.pulledCount || 0) / (r.totalPageMs / 1000)) : 0

  return r
}

// ====== 格式化 ======

function fmtMs(ms) {
  if (ms === undefined || ms === null) return '-'
  if (ms === 0) return '0'
  if (ms < 10) return (ms).toFixed(1) + 'ms'
  if (ms < 1000) return Math.round(ms) + 'ms'
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
        <span class="text-xs text-slate-400 ml-auto">左/右拖动表格查看更多列</span>
      </div>

      <!-- 每列详细说明 -->
      <div class="px-5 py-2.5 border-b border-slate-100 text-[11px] text-slate-400 leading-relaxed space-y-1">
        <div><b class="text-sky-600">拉取耗时</b> — 上游数据库执行一次分页查询的耗时（SELECT WHERE id > ? ORDER BY id LIMIT ?），包含 SQL 执行时间 + 网络传输时间 + 结果集传输时间。如果这个值高，说明上游表缺索引、有大数据字段、或网络延迟高。</div>
        <div><b>排队等待</b> — 数据拉取完成放入队列后，到落库线程开始处理的时间差（单位毫秒）。排队越久说明"落库阶段"处理速度跟不上"拉取阶段"，数据在队列中积压。瓶颈在落库子步骤。如果排队为 0 说明队列空闲，数据立刻被消费。</div>
        <div><b class="text-emerald-600">转换耗时</b> — 上游字段映射为中台实体的耗时。每行逐一执行 transform()，包含：从预分配队列取 Leaf ID、字段类型转换（String→BigDecimal 等）、状态码字典翻译（OMS NEW→待确认）、CleanRecord 对象构建。值高说明转换逻辑复杂或行数多。</div>
        <div><b>占比</b> — 转换耗时占总耗时的百分比（转换耗时 ÷ 总耗时 × 100%）。占比高说明整个同步的瓶颈在转换阶段。</div>
        <div><b>ID生成</b> — Leaf 号段发号器批量获取全局唯一 ID 的耗时。底层调用 nextIdBatch(tag, count) 一次 synchronized 获取全部 ID（不逐条竞争锁）。值高说明 Leaf 号段耗尽回源数据库 SELECT ... FOR UPDATE 时性能慢（但步长已改为 20000，正常情况下应 &lt;10ms）。</div>
        <div><b class="text-indigo-600">落库耗时</b> — saveBatch 方法总耗时，包含从查缓存到写入的全部子步骤。= 查缓存 + 新增写入 + 写缓存 + 查主键 + 设主键 + 更新写入。</div>
        <div><b>查缓存</b> — 查询当前批所有 sourceRowId 是否已存在的耗时（Redis SMEMBERS / BloomFilter / DB SELECT IN）。决定每条数据走 INSERT 还是 UPDATE。首次全量同步时所有数据都是"不存在"。</div>
        <div><b>新增写入</b> — 新数据批量写入中台库的耗时（MyBatis-Plus batchInsert）。首次全量同步所有数据都在这里写入。值高说明批量插入性能差（可启用 JDBC rewriteBatchedInserts=true）。</div>
        <div><b>写缓存</b> — 新写入的 sourceRowId 同步到 Redis 缓存的耗时（SADD 批量添加）。值高说明 Redis 网络延迟高或性能瓶颈。</div>
        <div><b>查主键</b> — 已有行需要先查询主键 ID（globalId）才能执行 UPDATE 的耗时（SELECT globalId WHERE sourceRowId IN）。首次全量同步为 0。</div>
        <div><b>设主键</b> — 将查询到的 globalId 设到实体字段上的耗时（循环 + Map.get + setGlobalId）。</div>
        <div><b>更新写入</b> — 已有行批量更新到中台库的耗时（MyBatis-Plus updateById）。首次全量同步为 0。</div>
        <div><b>总耗时</b> — 本批实际墙钟时间（毫秒）。= 排队等待 + 拉取耗时 + 转换耗时 + 落库耗时。这个值应接近相邻两批开始处理时间的时间差。</div>
        <div><b>速率</b> — 本批处理速率（行/秒）。= 行数 ÷ 总耗时 × 1000。</div>
        <div><b>行数(增/改)</b> — 本批拉取的总行数，括号内为（新增行数 / 更新行数）。首次全量同步全部为新增。</div>
        <div><b>起止时间</b> — 本批开始处理到处理完成的时间戳（HH:mm:ss→HH:mm:ss）。</div>
        <div><b>记录时间</b> — 该条批次记录写入 sync_batch_metrics 表的时间。</div>
      </div>

      <!-- 表格区域（左右可滑动） -->
      <div class="overflow-x-auto px-5 py-3">
        <table class="w-full text-sm text-slate-700 border-collapse min-w-[1100px]">
          <thead>
            <tr class="bg-slate-50 text-slate-500 text-xs border-b border-slate-200">
              <th class="px-3 py-2.5 text-left w-14 sticky left-0 bg-slate-50 z-10">批次</th>
              <th class="px-3 py-2.5 text-left w-28 sticky left-14 bg-slate-50 z-10">起止时间</th>
              <th class="px-3 py-2.5 text-right w-24">行数(增/改)</th>
              <th class="px-3 py-2.5 text-right w-24 bg-sky-50 text-sky-700 border-x border-sky-200">拉取耗时</th>
              <th class="px-3 py-2.5 text-right w-24 border-r-2 border-sky-200">排队等待</th>
              <th class="px-3 py-2.5 text-right w-24 bg-emerald-50 text-emerald-700 border-x border-emerald-200">转换耗时</th>
              <th class="px-3 py-2.5 text-right w-16">占比</th>
              <th class="px-3 py-2.5 text-right w-24 border-r-2 border-emerald-200">ID生成</th>
              <th class="px-3 py-2.5 text-right w-24 bg-indigo-50 text-indigo-700 border-x border-indigo-200">落库耗时</th>
              <th class="px-3 py-2.5 text-right w-20">查缓存</th>
              <th class="px-3 py-2.5 text-right w-24">新增写入</th>
              <th class="px-3 py-2.5 text-right w-24">写缓存</th>
              <th class="px-3 py-2.5 text-right w-20">查主键</th>
              <th class="px-3 py-2.5 text-right w-20">设主键</th>
              <th class="px-3 py-2.5 text-right w-24 border-r-2 border-indigo-200">更新写入</th>
              <th class="px-3 py-2.5 text-right w-24">总耗时</th>
              <th class="px-3 py-2.5 text-right w-20">速率</th>
              <th class="px-3 py-2.5 text-right w-24">记录时间</th>
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
              <td class="px-3 py-2.5 text-right font-mono" :class="slowClass(bm.insertDurationMs)">{{ fmtMs(bm.insertDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.cacheAddDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.globalIdQueryDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono">{{ fmtMs(bm.setIdDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono border-r-2 border-indigo-200" :class="slowClass(bm.updateDurationMs)">{{ fmtMs(bm.updateDurationMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono font-semibold" :class="slowClass(bm.totalPageMs)">{{ fmtMs(bm.totalPageMs) }}</td>
              <td class="px-3 py-2.5 text-right font-mono text-slate-400">{{ bm.rowsPerSecond ? bm.rowsPerSecond + '/s' : '-' }}</td>
              <td class="px-3 py-2.5 text-right font-mono text-[10px] text-slate-300">{{ fmtTime(bm.recordedAt) }}</td>
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
