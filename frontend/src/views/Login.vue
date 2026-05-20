<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { postForm, setToken } from '../api/request.js'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loginForm = ref({
  username: '',
  password: ''
})
const loading = ref(false)
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function doLogin() {
  loading.value = true
  try {
    const data = await postForm('/login', { username: loginForm.value.username, password: loginForm.value.password })
    if (data.code === 200) {
      setToken(data.data.token)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } else {
      ElMessage.error(data.message || '登录失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <el-icon size="32" color="#409EFF"><Monitor /></el-icon>
          <span>MLM Anime Platform</span>
        </div>
      </template>
      <el-form :model="loginForm" :rules="rules" label-position="top" @submit.prevent="doLogin">
        <el-form-item label="用户名" prop="username">
          <el-input 
            v-model="loginForm.username" 
            placeholder="请输入用户名"
            prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input 
            v-model="loginForm.password" 
            type="password" 
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            @keyup.enter="doLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="doLogin">
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.login-btn {
  width: 100%;
  height: 40px;
  font-size: 16px;
}
</style>
