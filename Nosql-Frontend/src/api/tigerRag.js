import api from './index'

// ========== Tiger-RAG 对话 ==========

// 发起一次问答 (传统阻塞方式，等待后端返回完整结果)
export function tigerRagChat(data) {
  return api.post('/tiger-rag/chat', data)
}

// 异步模式 (推荐)：立即返回一个 taskId，前端轮询查询结果
// 适合 LLM 调用需要十几秒甚至几十秒的场景
export function tigerRagStartChat(data) {
  return api.post('/tiger-rag/chat/start', data)
}

// 查询异步任务的状态与结果
export function tigerRagGetTask(taskId) {
  return api.get('/tiger-rag/chat/' + taskId)
}

// 获取某会话的历史
export function tigerRagHistory(sessionId) {
  return api.get('/tiger-rag/history', { params: { sessionId } })
}

// 清空会话
export function tigerRagClearSession(sessionId) {
  return api.delete('/tiger-rag/session', { params: { sessionId } })
}

// 获取 Tiger-RAG 服务信息
export function tigerRagInfo() {
  return api.get('/tiger-rag/info')
}

// 批量给项目生成向量 (仅管理员使用，用于初始化/批量补全)
export function tigerRagBatchEmbedProjects(params) {
  return api.post('/tiger-rag/projects/batch-embed', null, { params })
}
