/**
 * Axios 请求封装
 *
 * 这个文件是对 axios 的简单封装，所有 API 请求都通过这里发出。
 * 主要做了两件事：
 * 1. 设置请求超时时间（30秒）
 * 2. 统一处理响应和错误，组件里不用每个地方都写 try-catch 处理网络错误
 *
 * 用法：import request from '@/api/request'
 *       const res = await request.post('/api-hub-overview')
 *       console.log(res.data)  // 拿到后端返回的业务数据
 *
 * 后端统一响应格式：{ code: 200, message: "xxx", status: "SUCCESS", data: {...} }
 * code===200 表示成功，data 里放真正的数据。
 */
import axios from 'axios'

// ----- 1. 创建 axios 实例 -----
// 设置 30 秒超时和 JSON 请求头，后端所有接口都是 POST + application/json
const request = axios.create({
  timeout: 30000, // 30 秒超时（网络慢或后端挂了不会一直等）
  headers: { 'Content-Type': 'application/json' }
})

/**
 * ----- 2. 响应拦截器 -----
 * 每次请求返回后先经过这里，统一处理错误。
 * 组件里只需要关心业务数据（data），不用每个地方都判断状态码。
 *
 * 处理逻辑：
 * - 后端返回 code !== 200 → 视为业务错误，reject 让组件 catch
 * - HTTP 状态码 4xx/5xx → 网络或服务端错误，统一提示
 * - 请求超时 → 友好提示
 * - 其他网络断开等情况 → 统一提示
 */
request.interceptors.response.use(
  // ---- 2a. 后端正常返回了 HTTP 响应 ----
  (response) => {
    const body = response.data
    // 检查业务状态码：后端接口全部返回 { code: 200, data: ... }
    if (body.code && body.code !== 200) {
      console.error('[API 错误]', body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 走到这里说明请求成功，返回 { code, message, status, data }
    // 组件里用 res.data 拿到业务数据
    return body
  },
  // ---- 2b. HTTP 层面出错（网络断开、500 错误等） ----
  (error) => {
    // HTTP 状态码错误（4xx、5xx）
    if (error.response) {
      const status = error.response.status
      const msg = error.response.data?.message || `请求错误 ${status}`
      console.error('[HTTP 错误]', msg)
      return Promise.reject(new Error(msg))
    }
    // 请求超时
    if (error.code === 'ECONNABORTED') {
      console.error('[超时] 请求超时')
      return Promise.reject(new Error('请求超时'))
    }
    // 网络断开（比如后端没启动）
    console.error('[网络错误]', error.message)
    return Promise.reject(new Error('网络错误，请检查连接'))
  }
)

export default request
