/**
 * 路由配置
 *
 * 所有页面都在 MainLayout 内部显示。
 * 路由使用懒加载，访问时才加载对应组件。
 */
import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    // MainLayout 包含侧边栏导航 + 内容区域
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
