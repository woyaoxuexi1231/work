/**
 * 路由配置 — 含路由守卫鉴权
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
        path: 'projects',
        name: 'ProjectList',
        component: () => import('../views/project/ProjectList.vue'),
        meta: { title: '项目列表' }
      },
      {
        path: 'project/:id',
        name: 'ProjectDetail',
        component: () => import('../views/project/ProjectDetail.vue'),
        meta: { title: '项目详情' }
      },
      {
        path: 'project/:projectId/episode/:episodeId',
        name: 'EpisodeDetail',
        component: () => import('../views/episode/EpisodeDetail.vue'),
        meta: { title: '剧集详情' }
      },
      {
        path: 'resources',
        name: 'ResourceList',
        component: () => import('../views/resource/ResourceList.vue'),
        meta: { title: '资源库' }
      },
      {
        path: 'models',
        name: 'ModelList',
        component: () => import('../views/model/ModelList.vue'),
        meta: { title: '模型配置' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// ——— 路由守卫：未登录跳转登录页 ———
router.beforeEach((to, from, next) => {
  // 免鉴权页面直接放行
  if (to.meta.noAuth) return next()

  const token = getToken()
  if (!token) {
    console.log('[Router] 未登录，跳转登录页')
    return next('/login')
  }
  next()
})

export default router
