import { createRouter, createWebHistory } from 'vue-router'
import { post } from '../api/request.js'

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
        component: () => import('../views/Dashboard.vue')
      },
      // ---- mlm-anime-platform 页面 ----
      {
        path: 'mlm/projects',
        name: 'MlmProjects',
        component: () => import('../views/mlm/ProjectList.vue')
      },
      {
        path: 'mlm/resources',
        name: 'MlmResources',
        component: () => import('../views/mlm/ResourceList.vue')
      },
      {
        path: 'mlm/models',
        name: 'MlmModels',
        component: () => import('../views/mlm/ModelList.vue')
      },
      // ---- risk-data-hub-lab 页面 ----
      {
        path: 'risk',
        name: 'RiskHub',
        component: () => import('../views/risk/DataHub.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：通过 Gateway 校验登录态
router.beforeEach(async (to, from, next) => {
  if (to.meta.noAuth) {
    next()
    return
  }
  try {
    const data = await post('/api/auth/me')
    if (data.code === 0 && data.data) {
      next()
    } else {
      next('/login')
    }
  } catch {
    next('/login')
  }
})

export default router
