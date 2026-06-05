/**
 * MLM 项目相关 API
 */
import request from '../request'

/** 获取项目列表 */
export function listProjects() {
  return request.post('/mlm-api/api/projects/list')
}

/** 获取项目详情 */
export function getProject(id) {
  return request.post('/mlm-api/api/projects/get', { id })
}

/** 创建项目 */
export function createProject(data) {
  return request.post('/mlm-api/api/projects/create', data)
}

/** 切换项目可见性 */
export function toggleVisibility(id) {
  return request.post('/mlm-api/api/projects/toggle-visibility', { id })
}

/** 添加剧集 */
export function addEpisode(data) {
  return request.post('/mlm-api/api/projects/episode/add', data)
}

/** 提交剧本 */
export function submitScript(data) {
  return request.post('/mlm-api/api/projects/episode/submit-script', data)
}

/** 审核通过剧本 */
export function approveScript(data) {
  return request.post('/mlm-api/api/projects/episode/approve-script', data)
}

/** 驳回剧本 */
export function rejectScript(data) {
  return request.post('/mlm-api/api/projects/episode/reject-script', data)
}

/** 生成图片 */
export function generateImage(data) {
  return request.post('/mlm-api/api/projects/episode/generate-image', data)
}

/** 生成视频 */
export function generateVideo(data) {
  return request.post('/mlm-api/api/projects/episode/generate-video', data)
}

/** 完成生成 */
export function completeGeneration(data) {
  return request.post('/mlm-api/api/projects/episode/complete-generation', data)
}

/** 终审通过 */
export function finalApprove(data) {
  return request.post('/mlm-api/api/projects/episode/approve', data)
}

/** 终审驳回 */
export function finalReject(data) {
  return request.post('/mlm-api/api/projects/episode/reject', data)
}

/** 重试 */
export function retryEpisode(data) {
  return request.post('/mlm-api/api/projects/episode/retry', data)
}
