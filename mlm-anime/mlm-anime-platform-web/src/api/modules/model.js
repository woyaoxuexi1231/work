/**
 * MLM 模型配置相关 API
 */
import request from '../request'

/** 获取模型列表 */
export function listModels() {
  return request.post('/mlm-api/api/models/list')
}

/** 创建模型配置 */
export function createModel(data) {
  return request.post('/mlm-api/api/models/create', data)
}
