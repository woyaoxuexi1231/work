/**
 * 用户状态管理 — 登录态 / 用户信息
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, removeToken } from '@/api/request'
import { getCurrentUser, logout as apiLogout } from '@/api/modules/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const userInfo = ref(null)
  const loading = ref(false)

  /** 获取当前用户信息 */
  async function fetchUser() {
    loading.value = true
    try {
      const res = await getCurrentUser()
      if (res.code === 200) {
        userInfo.value = res.data
      }
    } catch (e) {
      console.error('[UserStore] 获取用户信息失败:', e.message)
    } finally {
      loading.value = false
    }
  }

  /** 退出登录 */
  async function logout() {
    try {
      await apiLogout()
    } catch (e) {
      console.error('[UserStore] 退出登录失败:', e.message)
    } finally {
      removeToken()
      token.value = ''
      userInfo.value = null
    }
  }

  /** 是否有 token（模拟登录态） */
  const isLoggedIn = () => !!token.value

  return { token, userInfo, loading, fetchUser, logout, isLoggedIn }
})
