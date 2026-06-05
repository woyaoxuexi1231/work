/**
 * 后端 API 接口汇总
 *
 * 后端所有接口使用 POST 方式，参数通过 RequestBody 传递。
 * 路径使用扁平风格（x-x-x），无层级。
 *
 * 每个函数返回 Promise，结果格式：{ code, message, status, data }
 * 组件中通过 res.data 获取实际数据。
 */
import request from './request'

// ==================== 系统总览 ====================

/** 获取系统总览：项目信息、拓扑、表统计、Leaf 状态 */
export function getOverview() {
  return request.post('/api-hub-overview')
}

// ==================== 数据源管理 ====================

/** 列出所有已注册的数据源 */
export function listDatasources() {
  return request.post('/api-datasource-list')
}

/** 查看单个数据源详情 */
export function getDatasource(key) {
  return request.post('/api-datasource-get', { key })
}

/** 注册新数据源 */
export function registerDatasource(config) {
  return request.post('/api-datasource-register', config)
}

/** 删除数据源 */
export function removeDatasource(key) {
  return request.post('/api-datasource-remove', { key })
}

// ==================== 同步任务 ====================

/** 提交同步任务 */
export function startSync(dataSourceKey, pageSize = 100) {
  return request.post('/api-hub-sync', { dataSourceKey, pageSize })
}

/** 查询当前同步任务状态 */
export function getSyncTask() {
  return request.post('/api-hub-sync-task')
}

/** 查询最近 30 条清洗交易记录 */
export function getCleanedTrades() {
  return request.post('/api-hub-cleaned-trades')
}
