<script setup>
import { ref, onMounted } from 'vue'
import { post } from '../../api/request.js'

const projects = ref([])
const loading = ref(true)

onMounted(async () => {
  const res = await post('/mlm-api/api/projects/list')
  if (res.code === 0) projects.value = res.data || []
  loading.value = false
})
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold mb-4">项目列表</h1>
    <p class="text-sm text-gray-500 mb-6">MLM Anime 流水线管理</p>

    <div v-if="loading" class="text-gray-400 text-sm">加载中...</div>
    <div v-else-if="projects.length === 0" class="text-gray-400 text-sm py-12 text-center">
      暂无项目
    </div>
    <div v-else class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <div v-for="p in projects" :key="p.id"
        class="rounded-2xl bg-white p-5 shadow-sm">
        <h3 class="font-semibold">{{ p.name || p.title || `项目 #${p.id}` }}</h3>
        <p class="mt-1 text-xs text-gray-500">{{ p.description || p.status || '' }}</p>
      </div>
    </div>
  </div>
</template>
