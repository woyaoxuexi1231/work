/**
 * 数据总览 + 同步 + 初始化 API — 直连后端，无 Gateway 前缀
 */
import request from '../request'

// ——— 总览 ———
export function getOverview() {
  return request.post('/api/hub/overview')
}

// ——— 数据源 ———
export function listDatasources() {
  return request.post('/api/datasource/list')
}

export function registerDatasource(data) {
  return request.post('/api/datasource/register', data)
}

export function removeDatasource(key) {
  return request.post('/api/datasource/remove', { key })
}

// ——— 同步 ———
export function getSyncTask() {
  return request.post('/api/hub/sync-task')
}

export function startSync(data) {
  return request.post('/api/hub/sync', data)
}

// ——— 初始化 ———
export function getInitTask() {
  return request.post('/api/hub/init-task')
}

export function startInitData() {
  return request.post('/api/hub/init-data')
}
