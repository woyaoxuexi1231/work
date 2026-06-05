/**
 * MLM 资源相关 API
 */
import request from '../request'

/** 获取资源列表 */
export function listResources() {
  return request.post('/mlm-api/api/resources/list')
}
