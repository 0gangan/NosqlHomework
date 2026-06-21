import api from './index'

// 分页查询项目
export function listProjects(params) {
  return api.get('/projects', { params })
}

// 获取项目详情
export function getProject(id) {
  return api.get(`/projects/${id}`)
}

// 获取项目提交历史
export function getProjectCommits(id, params) {
  return api.get(`/projects/${id}/commits`, { params })
}

// 按需拉取提交历史 (点击"查看Commit"按钮时调用)
export function fetchProjectCommits(id) {
  return api.post(`/projects/${id}/fetch-commits`)
}

// 获取项目贡献者
export function getProjectContributors(id) {
  return api.get(`/projects/${id}/contributors`)
}

// 创建项目
export function createProject(data) {
  return api.post('/projects', data)
}

// 语言分布统计
export function getLanguageStats() {
  return api.get('/projects/language-stats')
}

// 删除项目
export function deleteProject(id) {
  return api.delete(`/projects/${id}`)
}
