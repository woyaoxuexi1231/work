/**
 * 全局应用状态 — 侧边栏 / loading
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const globalLoading = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setLoading(val) {
    globalLoading.value = val
  }

  return { sidebarCollapsed, globalLoading, toggleSidebar, setLoading }
})
