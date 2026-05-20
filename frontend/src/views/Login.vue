<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { postForm, setToken } from '../api/request.js'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function doLogin() {
  error.value = ''
  loading.value = true
  try {
    const data = await postForm('/login', { username: username.value, password: password.value })
    if (data.code === 0) {
      setToken(data.data.token)
      router.push('/dashboard')
    } else {
      error.value = data.message || '登录失败'
    }
  } catch (e) {
    error.value = '网络错误'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-100">
    <div class="bg-white p-8 rounded-2xl shadow-md w-full max-w-sm">
      <h1 class="text-2xl font-bold text-center mb-2">MLM Anime Platform</h1>
      <p class="text-sm text-gray-500 text-center mb-6">请登录以继续</p>

      <form @submit.prevent="doLogin" class="space-y-4">
        <div>
          <label class="text-xs font-medium text-gray-600">用户名</label>
          <input v-model="username" type="text" required
            class="mt-1 w-full rounded-xl border px-3 py-2 text-sm focus:ring-2 focus:ring-blue-400 outline-none" />
        </div>
        <div>
          <label class="text-xs font-medium text-gray-600">密码</label>
          <input v-model="password" type="password" required
            class="mt-1 w-full rounded-xl border px-3 py-2 text-sm focus:ring-2 focus:ring-blue-400 outline-none" />
        </div>
        <p v-if="error" class="text-red-500 text-xs">{{ error }}</p>
        <button type="submit" :disabled="loading"
          class="w-full rounded-xl bg-blue-600 py-2.5 text-sm font-semibold text-white hover:bg-blue-500 disabled:bg-gray-300">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
    </div>
  </div>
</template>
