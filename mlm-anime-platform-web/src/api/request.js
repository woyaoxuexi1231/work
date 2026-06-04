/**
 * Axios 请求封装 — 统一拦截器、Token 注入、错误处理
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'mlm_auth_token'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// ——— 请求拦截器：自动注入 Token ———
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ——— 响应拦截器：统一错误处理 ———
request.interceptors.response.use(
  (response) => {
    const data = response.data
    // 业务级错误
    if (data.code && data.code !== 200) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (error) => {
    // HTTP 级错误
    if (error.response) {
      const { status } = error.response
      if (status === 401) {
        removeToken()
        window.location.hash = '#/login'
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error('没有权限访问')
      } else if (status >= 500) {
        ElMessage.error('服务器异常，请稍后重试')
      } else {
        ElMessage.error(error.response.data?.message || `请求错误 ${status}`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时')
    } else {
      ElMessage.error('网络错误，请检查连接')
    }
    console.error('[MLM API] 请求异常:', error.message)
    return Promise.reject(error)
  }
)

// ——— Token 工具函数 ———
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export default request
