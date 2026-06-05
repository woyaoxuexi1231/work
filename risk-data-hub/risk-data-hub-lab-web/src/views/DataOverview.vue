<!--
  DataOverview — 数据总览页

  这是系统的"仪表盘"，展示整体运行状况，包含 4 个区域：
  1. 顶部统计卡片（4 个关键数字）
  2. 系统拓扑（当前有哪些数据源在运行）
  3. 业务表统计和中台表统计（每个表的记录数）
  4. 最近清洗交易记录（最近的 30 条交易明细）

  数据来源：
  - getOverview() → POST /api-hub-overview → 系统总览、拓扑、统计、Leaf 状态
  - getCleanedTrades() → POST /api-hub-cleaned-trades → 最近 30 条清洗记录

  注意：两个 API 是独立调用的，各自加载各自的，一个失败不影响另一个。
-->
<script setup>
import { ref, onMounted } from 'vue'
import { getOverview, getCleanedTrades } from '@/api/index.js'

// ----- 页面数据状态 -----
// overview：系统总览对象，包含 topology / businessTableStats / hubTableStats / leafState 等
// trades：清洗交易记录数组
// loading：总览数据是否正在加载中（用于显示加载提示）
// error：加载总览数据出错时的错误信息
const overview = ref(null)
const trades = ref([])
const loading = ref(false)
const error = ref('')

// ============================================================
// 生命周期：组件挂载时自动加载数据
// ============================================================
onMounted(() => {
  fetchOverview()
  fetchTrades()
})

// ============================================================
// 获取系统总览数据
// ============================================================
async function fetchOverview() {
  loading.value = true
  error.value = ''
  try {
    const res = await getOverview()
    // res.data 是后端返回的 overview 对象，包含 topology/stats 等
    overview.value = res.data
  } catch (e) {
    error.value = '获取总览数据失败: ' + e.message
    console.error('[总览]', e.message)
  } finally {
    loading.value = false
  }
}

// ============================================================
// 获取清洗交易记录
// ============================================================
async function fetchTrades() {
  try {
    const res = await getCleanedTrades()
    // res.data 是交易记录数组，每个元素包含 globalId/vendorTradeNo/direction/amount 等
    trades.value = res.data || []
  } catch (e) {
    console.error('[交易记录]', e.message)
  }
}

// ============================================================
// 辅助函数：根据交易方向返回状态标签的样式类
// ============================================================
function statusBadge(status) {
  const map = { BUY: 'text-green-600 bg-green-50', SELL: 'text-red-600 bg-red-50' }
  return map[status] || 'text-slate-600 bg-slate-50'
}
</script>

<template>
  <!--
    max-w-6xl：限制最大宽度，大屏上内容居中不显得太宽
    space-y-8：区块之间的垂直间距
    animate-fade-in：页面加载时的淡入动画
  -->
  <div class="max-w-6xl mx-auto space-y-8 animate-fade-in">

    <!-- ============================================================
         错误提示
         获取总览数据失败时显示，可以点击重试
         ============================================================ -->
    <div v-if="error" class="bg-red-50 border border-red-200 text-red-700 rounded-xl px-5 py-3 text-sm">
      {{ error }}
      <button @click="fetchOverview" class="ml-3 underline text-red-600 hover:no-underline">重试</button>
    </div>

    <!-- ============================================================
         顶部统计卡片（4 个关键指标）
         网格布局：小屏 2 列，md 以上 4 列
         每个卡片显示一个数字指标：数据源数量、清洗交易数、事件消息数、Leaf 号段游标
         ============================================================ -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-5">
      <!-- 数据源数量 -->
      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <div class="text-xs text-slate-400 font-medium uppercase tracking-wide">数据源</div>
        <div class="mt-2 text-3xl font-bold text-indigo-600">
          {{ overview?.datasourceCount ?? '-' }}
        </div>
        <div class="mt-1 text-xs text-slate-400">已注册数据源</div>
      </div>

      <!-- 中台清洗记录数 -->
      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <div class="text-xs text-slate-400 font-medium uppercase tracking-wide">清洗交易</div>
        <div class="mt-2 text-3xl font-bold text-emerald-600">
          {{ overview?.cleanTradeCount ?? '-' }}
        </div>
        <div class="mt-1 text-xs text-slate-400">中台库交易表</div>
      </div>

      <!-- 事件消息 -->
      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <div class="text-xs text-slate-400 font-medium uppercase tracking-wide">事件消息</div>
        <div class="mt-2 text-3xl font-bold text-amber-600">
          {{ overview?.eventCount ?? '-' }}
        </div>
        <div class="mt-1 text-xs text-slate-400">已发送事件</div>
      </div>

      <!-- Leaf 发号器当前号段游标 -->
      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <div class="text-xs text-slate-400 font-medium uppercase tracking-wide">Leaf ID</div>
        <div class="mt-2 text-3xl font-bold text-sky-600">
          {{ overview?.leafState?.currentNext ?? '-' }}
        </div>
        <div class="mt-1 text-xs text-slate-400">当前号段游标</div>
      </div>
    </div>

    <!-- ============================================================
         系统拓扑
         显示当前系统中有哪些数据源在工作。
         topology 是一个 Map，包含"上游业务系统"和"中台库"两个列表。
         ============================================================ -->
    <div class="bg-white rounded-xl border border-slate-200 p-6">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-base font-semibold text-slate-800">系统拓扑</h3>
        <button
          @click="fetchOverview"
          class="text-xs text-indigo-600 hover:text-indigo-800 font-medium"
          :class="{ 'opacity-50': loading }"
          :disabled="loading"
        >
          {{ loading ? '刷新中...' : '刷新' }}
        </button>
      </div>

      <!-- 显示拓扑数据 -->
      <div v-if="overview?.topology" class="space-y-4">
        <!-- 上游业务系统列表 -->
        <div v-if="overview.topology['上游业务系统']?.length">
          <div class="text-xs text-slate-400 font-medium mb-2">上游业务系统</div>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="item in overview.topology['上游业务系统']"
              :key="item"
              class="px-3 py-1.5 bg-indigo-50 text-indigo-700 rounded-lg text-sm"
            >
              {{ item }}
            </span>
          </div>
        </div>

        <!-- 中台库 -->
        <div v-if="overview.topology['中台库']?.length">
          <div class="text-xs text-slate-400 font-medium mb-2">中台库</div>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="item in overview.topology['中台库']"
              :key="item"
              class="px-3 py-1.5 bg-emerald-50 text-emerald-700 rounded-lg text-sm"
            >
              {{ item }}
            </span>
          </div>
        </div>
      </div>

      <!-- 加载占位 -->
      <div v-else-if="loading" class="text-sm text-slate-400">加载中...</div>
      <!-- 无数据 -->
      <div v-else class="text-sm text-slate-400">暂无拓扑数据</div>
    </div>

    <!-- ============================================================
         业务表统计 & 中台表统计（左右两栏）
         左侧：上游业务系统的各表记录数（trade_oms / trade_broker）
         右侧：中台库各表记录数（clean_stock / clean_trade / ...）
         ============================================================ -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <!-- 上游业务表统计 -->
      <div class="bg-white rounded-xl border border-slate-200 p-6">
        <h3 class="text-base font-semibold text-slate-800 mb-4">上游业务表统计</h3>

        <div v-if="overview?.businessTableStats" class="space-y-4">
          <!-- OMS 交易系统的各表记录数 -->
          <div v-if="overview.businessTableStats.trade_oms">
            <div class="text-xs text-slate-400 font-medium mb-2">OMS (交易系统)</div>
            <div class="space-y-1">
              <div
                v-for="(count, table) in overview.businessTableStats.trade_oms"
                :key="table"
                class="flex justify-between text-sm px-3 py-1.5 bg-slate-50 rounded-md"
              >
                <span class="text-slate-600">{{ table }}</span>
                <span class="font-medium text-slate-800">{{ count }}</span>
              </div>
            </div>
          </div>

          <!-- Broker 券商系统的各表记录数 -->
          <div v-if="overview.businessTableStats.trade_broker">
            <div class="text-xs text-slate-400 font-medium mb-2 mt-4">Broker (券商)</div>
            <div class="space-y-1">
              <div
                v-for="(count, table) in overview.businessTableStats.trade_broker"
                :key="table"
                class="flex justify-between text-sm px-3 py-1.5 bg-slate-50 rounded-md"
              >
                <span class="text-slate-600">{{ table }}</span>
                <span class="font-medium text-slate-800">{{ count }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="loading" class="text-sm text-slate-400">加载中...</div>
        <div v-else class="text-sm text-slate-400">暂无统计</div>
      </div>

      <!-- 中台表统计 -->
      <div class="bg-white rounded-xl border border-slate-200 p-6">
        <h3 class="text-base font-semibold text-slate-800 mb-4">中台表统计</h3>

        <div v-if="overview?.hubTableStats" class="space-y-1">
          <div
            v-for="(count, table) in overview.hubTableStats"
            :key="table"
            class="flex justify-between text-sm px-3 py-1.5 bg-slate-50 rounded-md"
          >
            <span class="text-slate-600">{{ table }}</span>
            <span class="font-medium text-slate-800">{{ count }}</span>
          </div>
        </div>

        <div v-else-if="loading" class="text-sm text-slate-400">加载中...</div>
        <div v-else class="text-sm text-slate-400">暂无统计</div>
      </div>
    </div>

    <!-- ============================================================
         最近清洗交易记录
         一个简单的表格，展示最近 30 条清洗交易。
         数据来自 getCleanedTrades() 接口。
         ============================================================ -->
    <div class="bg-white rounded-xl border border-slate-200 p-6">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-base font-semibold text-slate-800">最近清洗交易</h3>
        <button
          @click="fetchTrades"
          class="text-xs text-indigo-600 hover:text-indigo-800 font-medium"
        >
          刷新
        </button>
      </div>

      <!-- 表格区域 -->
      <div v-if="trades.length > 0" class="overflow-x-auto">
        <table class="w-full text-sm">
          <!-- 表头 -->
          <thead>
            <tr class="border-b border-slate-200 text-slate-400 text-xs uppercase tracking-wider">
              <th class="text-left py-3 px-3 font-medium">单号</th>
              <th class="text-left py-3 px-3 font-medium">类型</th>
              <th class="text-left py-3 px-3 font-medium">方向</th>
              <th class="text-right py-3 px-3 font-medium">金额</th>
              <th class="text-left py-3 px-3 font-medium">状态</th>
              <th class="text-left py-3 px-3 font-medium">来源</th>
              <th class="text-left py-3 px-3 font-medium">交易时间</th>
            </tr>
          </thead>
          <!-- 表体 -->
          <tbody>
            <tr
              v-for="t in trades"
              :key="t.globalId"
              class="border-b border-slate-100 hover:bg-slate-50 transition-colors"
            >
              <td class="py-3 px-3 text-slate-800 font-mono text-xs">{{ t.vendorTradeNo }}</td>
              <td class="py-3 px-3 text-slate-600">{{ t.bizType }}</td>
              <td class="py-3 px-3">
                <!-- 交易方向：买涨（BUY）绿色，卖跌（SELL）红色 -->
                <span class="px-2 py-0.5 rounded text-xs font-medium" :class="statusBadge(t.direction)">
                  {{ t.direction }}
                </span>
              </td>
              <td class="py-3 px-3 text-right font-mono text-slate-800">{{ t.amount }}</td>
              <td class="py-3 px-3 text-slate-600">{{ t.statusName }}</td>
              <td class="py-3 px-3">
                <span class="text-xs text-slate-400">{{ t.sourceSystem }}</span>
              </td>
              <td class="py-3 px-3 text-slate-500 text-xs">{{ t.tradeTime }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 无数据提示 -->
      <div v-else class="text-sm text-slate-400 py-6 text-center">
        暂无清洗交易记录，请先执行同步任务
      </div>
    </div>
  </div>
</template>
