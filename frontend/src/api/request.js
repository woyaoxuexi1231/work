/**
 * 通用请求封装
 * 所有 API 请求自动带 Authorization 头 + 路径前缀
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
  return res.json()
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
  return res.json()
}

export async function get(url) {
  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders()
  })
  return res.json()
}
