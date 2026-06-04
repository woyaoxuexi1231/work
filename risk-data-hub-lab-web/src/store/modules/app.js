/**
 * 全局应用状态
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const globalLoading = ref(false)
  function setLoading(v) { globalLoading.value = v }
  return { globalLoading, setLoading }
})
