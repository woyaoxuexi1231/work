/**
 * Vite 构建配置
 *
 * Vite 是这个前端项目的构建工具（替代 Webpack），负责：
 * 1. 开发环境：启动本地开发服务器，支持热更新（HMR）
 * 2. 生产环境：打包优化，生成最终的 dist/ 目录
 *
 * 关键配置说明：
 * - @ 别名：在 import 路径中使用 @/ 代替 src/，如 import X from '@/api/index'
 * - 代理：开发时将 /api 请求转发到后端 Spring Boot（端口 8501），解决跨域问题
 * - 端口：开发服务器运行在 5174 端口（避免和其他 Vue 项目冲突）
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  // Vue 3 插件 — 让 Vite 能编译 .vue 单文件组件
  plugins: [vue()],

  // 模块解析配置
  resolve: {
    alias: {
      // @ 指向 src/ 目录，方便导入
      // 用法：import request from '@/api/request'
      // 等价于：import request from './src/api/request'
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  // 开发服务器配置
  server: {
    port: 5174,
    // 代理配置：开发时避免跨域问题
    // 前端请求 /api-hub-overview → 代理转发到 http://localhost:8501/api-hub-overview
    proxy: {
      '/api': {
        target: 'http://localhost:8501', // 后端 Spring Boot 地址
        changeOrigin: true               // 修改请求 Host 头为目标地址
      }
    }
  }
})
