/**
 * Axios 请求封装
 *
 * 用法：import request from '@/api/request'
 *       const res = await request.post('/api-hub-overview')
 *       console.log(res.data)  // 后端返回的业务数据
 *
 * 响应格式：{ code: 200, message: "xxx", status: "SUCCESS", data: {...} }
 */
import axios from 'axios'

// 创建 axios 实例
const request = axios.create({
  timeout: 30000, // 30 秒超时
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 响应拦截器
 * - code !== 200 时打印错误并 reject
 * - HTTP 错误（网络断开、500、404 等）统一处理
 */
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code && body.code !== 200) {
      console.error('[API 错误]', body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body // 返回 { code, message, status, data }
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      const msg = error.response.data?.message || `请求错误 ${status}`
      console.error('[HTTP 错误]', msg)
      return Promise.reject(new Error(msg))
    }
    if (error.code === 'ECONNABORTED') {
      console.error('[超时] 请求超时')
      return Promise.reject(new Error('请求超时'))
    }
    console.error('[网络错误]', error.message)
    return Promise.reject(new Error('网络错误，请检查连接'))
  }
)

export default request
