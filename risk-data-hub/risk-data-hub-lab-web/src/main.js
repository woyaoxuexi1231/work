/**
 * 应用入口文件
 *
 * 这个文件是 Vue 3 应用的启动入口，负责：
 * 1. 创建 Vue 应用实例
 * 2. 安装 Vue Router（路由管理）
 * 3. 加载 Tailwind CSS 样式
 * 4. 挂载到 index.html 的 <div id="app"> 上
 *
 * 技术栈：Vue 3 + Vue Router + Tailwind CSS
 * 没有使用 Pinia（当前应用页面少、数据简单，全部用组件本地状态管理就够了）
 * 没有使用 Element UI 等组件库（全部手工用 Tailwind 写，更轻量灵活）
 */
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'

// 创建应用 → 装路由 → 挂载到页面
const app = createApp(App)
app.use(router)
app.mount('#app')
