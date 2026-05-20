<script setup>
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-bold">控制台</h1>

    <div class="grid gap-6 md:grid-cols-2">
      <!-- MLM Anime -->
      <router-link to="/mlm/projects"
        class="block rounded-2xl bg-white p-6 shadow-sm hover:shadow-md transition">
        <h2 class="text-xl font-bold text-indigo-700">MLM Anime Platform</h2>
        <p class="mt-2 text-sm text-gray-500">动漫流水线管理 — 项目列表、资源库、模型配置</p>
        <div class="mt-4 flex gap-2">
          <span class="rounded-full bg-indigo-100 px-3 py-1 text-xs text-indigo-700">项目</span>
          <span class="rounded-full bg-indigo-100 px-3 py-1 text-xs text-indigo-700">流水线</span>
          <span class="rounded-full bg-indigo-100 px-3 py-1 text-xs text-indigo-700">资源</span>
        </div>
      </router-link>

      <!-- Risk Hub -->
      <router-link to="/risk"
        class="block rounded-2xl bg-white p-6 shadow-sm hover:shadow-md transition">
        <h2 class="text-xl font-bold text-emerald-700">Risk Data Hub Lab</h2>
        <p class="mt-2 text-sm text-gray-500">数据中台同步实验室 — 数据源维护、数据同步</p>
        <div class="mt-4 flex gap-2">
          <span class="rounded-full bg-emerald-100 px-3 py-1 text-xs text-emerald-700">数据源</span>
          <span class="rounded-full bg-emerald-100 px-3 py-1 text-xs text-emerald-700">ETL</span>
          <span class="rounded-full bg-emerald-100 px-3 py-1 text-xs text-emerald-700">同步</span>
        </div>
      </router-link>
    </div>
  </div>
</template>
