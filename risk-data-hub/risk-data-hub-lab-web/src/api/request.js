/**
 * Axios 请求封装 — Risk Data Hub Lab
 * 开发环境通过 Vite proxy 转发 /api → localhost:8501，无需 baseURL
 * 生产环境前端与后端同域部署，相对路径即可
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

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
