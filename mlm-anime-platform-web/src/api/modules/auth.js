/**
 * 认证相关 API
 */
import request, { getToken, setToken, removeToken } from '../request'

/** 登录 */
export function login(username, password) {
  return request.post('/login', { username, password }, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    transformRequest: [(data) => {
      const params = new URLSearchParams()
      for (const key in data) params.append(key, data[key])
      return params.toString()
    }]
  }).then(res => {
    const token = res.data?.token || res.token || res.data
    if (token) setToken(token)
    return res
  })
}

/** 退出登录 */
export function logout() {
  return request.post('/logout').finally(() => removeToken())
}

/** 获取当前用户信息（校验登录态） */
export function getCurrentUser() {
  return request.post('/mlm-api/api/auth/me')
}
