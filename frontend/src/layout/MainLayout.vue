<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { post, removeToken } from '../api/request.js'

const router = useRouter()
const currentUser = ref(null)

// 通过 Gateway 获取当前用户
post('/api/auth/me').then(data => {
  if (data.code === 0) currentUser.value = data.data
})

async function handleLogout() {
  await post('/logout')
  removeToken()
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen bg-gray-100">
    <header class="bg-white shadow-sm border-b">
      <div class="max-w-7xl mx-auto px-4 h-14 flex items-center justify-between">
        <nav class="flex items-center gap-6 text-sm">
          <router-link to="/dashboard" class="font-semibold text-gray-800 hover:text-blue-600">
            首页
          </router-link>
          <span class="text-gray-300">|</span>
          <span class="text-gray-500 text-xs">MLM Anime</span>
          <router-link to="/mlm/projects" class="text-gray-600 hover:text-blue-600">项目列表</router-link>
          <router-link to="/mlm/resources" class="text-gray-600 hover:text-blue-600">资源库</router-link>
          <router-link to="/mlm/models" class="text-gray-600 hover:text-blue-600">模型配置</router-link>
          <span class="text-gray-300">|</span>
          <span class="text-gray-500 text-xs">Risk Hub</span>
          <router-link to="/risk" class="text-gray-600 hover:text-blue-600">数据中台</router-link>
        </nav>
        <div class="flex items-center gap-3 text-sm">
          <span class="text-gray-500">{{ currentUser?.username || '未登录' }}</span>
          <button @click="handleLogout" class="text-gray-400 hover:text-red-500 text-xs">退出</button>
        </div>
      </div>
    </header>

    <main class="max-w-7xl mx-auto px-4 py-6">
      <router-view />
    </main>
  </div>
</template>
