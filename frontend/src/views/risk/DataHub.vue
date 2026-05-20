<script setup>
import { ref, onMounted } from 'vue'
import { post } from '../../api/request.js'

const overview = ref(null)
const loading = ref(true)

onMounted(async () => {
  const res = await post('/risk-api/api/hub/overview')
  if (res.code === 0) overview.value = res.data
  loading.value = false
})
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold mb-4">数据中台同步实验室</h1>
    <p class="text-sm text-gray-500 mb-6">Risk Data Hub — 数据源维护与 ETL 同步</p>

    <div v-if="loading" class="text-gray-400 text-sm">加载中...</div>
    <div v-else-if="overview" class="grid gap-4 md:grid-cols-4 mb-6">
      <div class="rounded-2xl bg-white p-4 shadow-sm">
        <div class="text-xs text-gray-500">数据源数量</div>
        <div class="mt-2 text-2xl font-bold">{{ overview.datasourceCount || 0 }}</div>
      </div>
      <div class="rounded-2xl bg-white p-4 shadow-sm">
        <div class="text-xs text-gray-500">上游库数量</div>
        <div class="mt-2 text-2xl font-bold">{{ (overview.topology?.upstreams || []).length }}</div>
      </div>
      <div class="rounded-2xl bg-white p-4 shadow-sm">
        <div class="text-xs text-gray-500">中台落库条数</div>
        <div class="mt-2 text-2xl font-bold">
          {{ (overview.hubTableStats?.clean_stock || 0) + (overview.hubTableStats?.clean_trade || 0) + (overview.hubTableStats?.clean_position || 0) + (overview.hubTableStats?.clean_asset || 0) }}
        </div>
      </div>
      <div class="rounded-2xl bg-white p-4 shadow-sm">
        <div class="text-xs text-gray-500">业务表数量</div>
        <div class="mt-2 text-2xl font-bold">{{ Object.keys(overview.businessTableStats?.trade_oms || {}).length + Object.keys(overview.businessTableStats?.trade_broker || {}).length }}</div>
      </div>
    </div>
    <div v-else class="text-gray-400 text-sm py-12 text-center">
      暂无概览数据
    </div>
  </div>
</template>
