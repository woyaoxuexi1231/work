<!--
  Login — Risk Data Hub Lab 登录页
-->
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/modules/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const formRef = ref(null)
const loginForm = ref({ username: '', password: '' })
const loading = ref(false)

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function doLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await login(loginForm.value.username, loginForm.value.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) { console.error('[Login] 登录失败:', e.message) }
  finally { loading.value = false }
}
</script>

<template>
  <div class="login-container">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="card-header">
          <el-icon size="32" color="#67c23a"><DataLine /></el-icon>
          <span>Risk Data Hub Lab</span>
        </div>
      </template>
      <el-form ref="formRef" :model="loginForm" :rules="rules" label-position="top" @submit.prevent="doLogin">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" show-password @keyup.enter="doLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" :loading="loading" class="login-btn" @click="doLogin">
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1a3a2a 0%, #2d6a4f 100%); }
.login-card { width: 420px; border-radius: 16px; box-shadow: 0 12px 40px rgba(0,0,0,0.3); }
.card-header { display: flex; align-items: center; justify-content: center; gap: 12px; font-size: 22px; font-weight: 700; color: #303133; }
.login-btn { width: 100%; height: 44px; font-size: 16px; border-radius: 8px; }
</style>
