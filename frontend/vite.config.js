import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Gateway 地址 — 通过环境变量覆盖，默认值用于本地开发
const GATEWAY_TARGET = process.env.GATEWAY_TARGET || 'http://localhost:9000'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 所有请求统一走 Gateway（鉴权 + 路由转发）
      '/login':       { target: GATEWAY_TARGET, changeOrigin: true },
      '/logout':      { target: GATEWAY_TARGET, changeOrigin: true },
      '/api/auth':    { target: GATEWAY_TARGET, changeOrigin: true },
      '/mlm-api':     { target: GATEWAY_TARGET, changeOrigin: true },
      '/risk-api':    { target: GATEWAY_TARGET, changeOrigin: true },
    }
  }
})
