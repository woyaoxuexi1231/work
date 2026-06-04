/**
 * 用户状态 — Risk Data Hub
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, removeToken } from '@/api/request'
import { getCurrentUser, logout as apiLogout } from '@/api/modules/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const userInfo = ref(null)
  const loading = ref(false)

  async function fetchUser() {
    loading.value = true
    try {
      const res = await getCurrentUser()
      if (res.code === 200) userInfo.value = res.data
    } catch (e) {
      console.error('[UserStore] 获取用户失败:', e.message)
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try { await apiLogout() } catch (e) { console.error('[UserStore] 退出失败:', e.message) }
    finally {
      removeToken()
      token.value = ''
      userInfo.value = null
    }
  }

  const isLoggedIn = () => !!token.value
  return { token, userInfo, loading, fetchUser, logout, isLoggedIn }
})
