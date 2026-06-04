/**
 * 路由配置 — Risk Data Hub Lab
 */
import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/api/request'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { noAuth: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'overview',
        name: 'DataOverview',
        component: () => import('../views/overview/DataOverview.vue'),
        meta: { title: '数据总览' }
      },
      {
        path: 'datasources',
        name: 'DataSourceList',
        component: () => import('../views/datasource/DataSourceList.vue'),
        meta: { title: '数据源管理' }
      },
      {
        path: 'sync',
        name: 'SyncTask',
        component: () => import('../views/sync/SyncTask.vue'),
        meta: { title: '同步任务' }
      },
      {
        path: 'init',
        name: 'InitTask',
        component: () => import('../views/init/InitTask.vue'),
        meta: { title: '初始化任务' }
      }
    ]
  }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to, from, next) => {
  if (to.meta.noAuth) return next()
  if (!getToken()) { console.log('[Router] 未登录'); return next('/login') }
  next()
})

export default router
