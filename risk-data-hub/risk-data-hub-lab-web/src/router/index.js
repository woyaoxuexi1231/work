/**
 * 路由配置
 *
 * 所有页面都在 MainLayout 内部作为子路由显示。
 * 路由使用懒加载（() => import(...)），用户访问时才加载对应组件，
 * 首屏只加载首页代码，其他页面按需加载，加快首次打开速度。
 *
 * 页面结构：
 *   /（根路径）→ 重定向到 /dashboard
 *   ├── /dashboard    → 工作台（功能入口卡片）
 *   ├── /overview     → 数据总览
 *   ├── /datasources  → 数据源管理
 *   └── /sync         → 同步任务
 */
import { createRouter, createWebHashHistory } from 'vue-router'

// 路由表
const routes = [
  {
    // 根路径：使用 MainLayout 作为布局容器
    path: '/',
    // 懒加载 MainLayout 组件（包含侧边栏导航 + 右侧内容区域）
    component: () => import('@/layout/MainLayout.vue'),
    // 默认跳转到工作台
    redirect: '/dashboard',
    // 子路由：在 MainLayout 的 <router-view> 中显示
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'overview',
        name: 'DataOverview',
        component: () => import('@/views/DataOverview.vue'),
        meta: { title: '数据总览' }
      },
      {
        path: 'datasources',
        name: 'DataSourceList',
        component: () => import('@/views/DataSourceList.vue'),
        meta: { title: '数据源管理' }
      },
      {
        path: 'sync',
        name: 'SyncTask',
        component: () => import('@/views/SyncTask.vue'),
        meta: { title: '同步任务' }
      },
      {
        path: 'batch-metrics/:recordId',
        name: 'SyncBatchMetrics',
        component: () => import('@/views/SyncBatchMetrics.vue'),
        meta: { title: '批次耗时明细' }
      }
    ]
  }
]

// 创建路由实例
// 使用 Hash 模式（URL 里有 #），这种模式不需要服务端做额外配置
const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
