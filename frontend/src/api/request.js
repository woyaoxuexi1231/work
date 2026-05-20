/**
 * 通用请求封装
 * 所有 API 请求自动带 Authorization 头 + 统一错误处理
 */

const TOKEN_KEY = 'auth_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

// 统一响应解析，处理 HTTP 错误和非 JSON 响应
async function parseResponse(res) {
  const status = res.status
  
  // 处理 2xx 成功状态
  if (status >= 200 && status < 300) {
    const contentType = res.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      return res.json()
    }
    // 非 JSON 响应
    return { code: status, message: '服务器返回了非 JSON 响应' }
  }
  
  // 处理 4xx/5xx 错误状态
  let errorMsg = `HTTP ${status}`
  try {
    const data = await res.json()
    if (data.message) errorMsg = data.message
    return { code: status, message: errorMsg, _httpError: true }
  } catch {
    return { code: status, message: errorMsg, _httpError: true }
  }
}

function authHeaders() {
  const token = getToken()
  return token ? { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
                : { 'Content-Type': 'application/json' }
}

export async function post(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: body ? JSON.stringify(body) : undefined
  })
  return parseResponse(res)
}

export async function postForm(url, params) {
  const token = getToken()
  const headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: new URLSearchParams(params)
  })
  return parseResponse(res)
}

export async function get(url) {
  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders()
  })
  return parseResponse(res)
}
