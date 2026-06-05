/**
 * Axios 请求封装 — Risk Data Hub Lab（无认证，后端全部 POST + JSON body）
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 响应拦截：业务 code != 200 统一提示，HTTP 错误统一处理
request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code && data.code !== 200) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (error) => {
    if (error.response) {
      const { status } = error.response
      if (status >= 500) {
        ElMessage.error('服务器异常')
      } else {
        ElMessage.error(error.response.data?.message || `请求错误 ${status}`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时')
    } else {
      ElMessage.error('网络错误')
    }
    console.error('[Risk API] 请求异常:', error.message)
    return Promise.reject(error)
  }
)

export default request
