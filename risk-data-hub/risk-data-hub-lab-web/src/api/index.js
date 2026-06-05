/**
 * 后端 API 接口汇总
 *
 * 这个文件集中管理所有后端接口调用，每个函数对应一个后端接口。
 * 组件里只需要 import 对应的函数并调用，不需要关心请求细节。
 *
 * 后端接口约定：
 * - 全部使用 POST 方法（统一风格）
 * - 参数通过请求体（RequestBody）以 JSON 格式传递
 * - 路径使用扁平风格（x-x-x），没有层级嵌套
 * - 返回值格式：{ code, message, status, data }
 *   组件中通过 res.data 获取实际业务数据
 *
 * 所有函数返回 Promise，配合 async/await 使用：
 *   const res = await getOverview()
 *   console.log(res.data)  // 获取真正的数据
 */
import request from './request'

// ==================== 1. 系统总览 ====================

/**
 * 获取系统总览数据
 * 返回：项目信息、系统拓扑、业务表统计、中台表统计、Leaf 发号器状态
 * 后端路径：POST /api-hub-overview
 * 响应 data 结构：{ projectName, datasourceCount, topology, businessTableStats, hubTableStats, ... }
 */
export function getOverview() {
  return request.post('/api-hub-overview')
}

// ==================== 2. 数据源管理 ====================

/**
 * 列出所有已注册的数据源
 * 后端路径：POST /api-datasource-list
 * 响应 data：数据源对象数组 [{ key, name, datasourceType, url, online, ... }]
 */
export function listDatasources() {
  return request.post('/api-datasource-list')
}

/**
 * 查看单个数据源详情
 * @param {string} key 数据源唯一标识（如 trade_oms）
 * 后端路径：POST /api-datasource-get
 */
export function getDatasource(key) {
  return request.post('/api-datasource-get', { key })
}

/**
 * 注册新数据源
 * @param {object} config 数据源配置，包含 key/name/url/username/password 等
 * 后端路径：POST /api-datasource-register
 */
export function registerDatasource(config) {
  return request.post('/api-datasource-register', config)
}

/**
 * 删除数据源
 * @param {string} key 数据源唯一标识
 * 后端路径：POST /api-datasource-remove
 */
export function removeDatasource(key) {
  return request.post('/api-datasource-remove', { key })
}

// ==================== 3. 同步任务 ====================

/**
 * 提交异步同步任务
 * @param {string} dataSourceKey 要同步的数据源标识
 * @param {number} pageSize 每页拉取条数，默认 100
 * 后端路径：POST /api-hub-sync
 * 响应 data：刚创建的同步任务对象 { id, status, progress, message, ... }
 */
export function startSync(dataSourceKey, pageSize = 100) {
  return request.post('/api-hub-sync', { dataSourceKey, pageSize })
}

/**
 * 查询当前同步任务状态
 * 返回最近一条同步任务的实时状态，包括进度、拉取/落库数量、运行信息等
 * 后端路径：POST /api-hub-sync-task
 * 响应 data：同步任务对象 { id, status, progress, message, totalPulledCount, totalSavedCount, ... }
 *   无任务时返回 status=IDLE 的空任务
 */
export function getSyncTask() {
  return request.post('/api-hub-sync-task')
}

/**
 * 查询最近 30 条清洗后的交易记录
 * 后端路径：POST /api-hub-cleaned-trades
 * 响应 data：交易记录数组 [{ globalId, vendorTradeNo, direction, amount, statusName, ... }]
 */
export function getCleanedTrades() {
  return request.post('/api-hub-cleaned-trades')
}
