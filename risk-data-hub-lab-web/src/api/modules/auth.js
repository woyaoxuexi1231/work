/**
 * 认证 API
 */
import request, { setToken, removeToken } from '../request'

export function login(username, password) {
  return request.post('/login', { username, password }, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    transformRequest: [(data) => {
      const p = new URLSearchParams()
      for (const k in data) p.append(k, data[k])
      return p.toString()
    }]
  }).then(res => {
    const token = res.data?.token || res.token || res.data
    if (token) setToken(token)
    return res
  })
}

export function logout() {
  return request.post('/logout').finally(() => removeToken())
}

export function getCurrentUser() {
  return request.post('/api/auth/me')
}
