<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { post, removeToken } from '../api/request.js'

const router = useRouter()
const route = useRoute()
const currentUser = ref(null)

// 通过 Gateway 获取当前用户（忽略 HTTP 错误，由路由守卫处理）
post('/api/auth/me').then(data => {
  // 只处理正常响应，HTTP 错误由路由守卫重定向
  if (!data._httpError && data.code === 200) {
    currentUser.value = data.data
  }
})

const handleCommand = async (command) => {
  if (command === 'logout') {
    await post('/logout')
    removeToken()
    router.push('/login')
  }
}

function handleNav(path) {
  router.push(path)
}
</script>

<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="header-left">
        <el-icon size="24" color="#409EFF"><Monitor /></el-icon>
        <span class="logo-text">Admin Portal</span>
      </div>
      
      <el-menu 
        mode="horizontal" 
        :default-active="route.path"
        class="header-menu"
        :ellipsis="false"
      >
        <el-menu-item index="/dashboard" @click="handleNav('/dashboard')">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        
        <el-sub-menu index="mlm">
          <template #title>
            <el-icon><VideoPlay /></el-icon>
            <span>MLM Anime Platform</span>
          </template>
          <el-menu-item index="/mlm/projects" @click="handleNav('/mlm/projects')">
            <el-icon><FolderOpened /></el-icon>
            <span>项目列表</span>
          </el-menu-item>
          <el-menu-item index="/mlm/resources" @click="handleNav('/mlm/resources')">
            <el-icon><Files /></el-icon>
            <span>资源库</span>
          </el-menu-item>
          <el-menu-item index="/mlm/models" @click="handleNav('/mlm/models')">
            <el-icon><Setting /></el-icon>
            <span>模型配置</span>
          </el-menu-item>
        </el-sub-menu>
        
        <el-sub-menu index="risk">
          <template #title>
            <el-icon><DataLine /></el-icon>
            <span>Risk Data Hub Lab</span>
          </template>
          <el-menu-item index="/risk" @click="handleNav('/risk')">
            <el-icon><Connection /></el-icon>
            <span>数据中台</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
      
      <div class="header-right">
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><UserFilled /></el-icon>
            <span>{{ currentUser?.username || '未登录' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="layout-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-container {
  min-height: 100vh;
}
.layout-header {
  display: flex;
  align-items: center;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 40px;
  width: 200px;
}
.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.header-menu {
  flex: 1;
  border-bottom: none;
}
.header-right {
  margin-left: 20px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #606266;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background 0.3s;
}
.user-info:hover {
  background: #f5f7fa;
}
.layout-main {
  background: #f5f7fa;
  padding: 20px;
}
</style>
