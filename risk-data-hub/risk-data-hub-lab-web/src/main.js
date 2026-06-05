/**
 * 应用入口 — Risk Data Hub Lab
 *
 * 技术栈：Vue 3 + Vue Router + Tailwind CSS
 * 没有使用 Pinia（当前应用足够简单，全部用组件本地状态管理）
 */
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.use(router)
app.mount('#app')
