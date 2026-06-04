/**
 * 数据总览 + 同步 + 初始化 API
 */
import request from '../request'

// ——— 总览 ———
export function getOverview() {
  return request.post('/risk-api/api/hub/overview')
}

// ——— 数据源 ———
export function listDatasources() {
  return request.post('/risk-api/api/datasource/list')
}

export function registerDatasource(data) {
  return request.post('/risk-api/api/datasource/register', data)
}

export function removeDatasource(key) {
  return request.post('/risk-api/api/datasource/remove', { key })
}

// ——— 同步 ———
export function getSyncTask() {
  return request.post('/risk-api/api/hub/sync-task')
}

export function startSync(data) {
  return request.post('/risk-api/api/hub/sync', data)
}

// ——— 初始化 ———
export function getInitTask() {
  return request.post('/risk-api/api/hub/init-task')
}

export function startInitData() {
  return request.post('/risk-api/api/hub/init-data')
}
