import api from './index'

/**
 * Agent 智能问答 API
 */
export function askAgent(question, topK = 3) {
  return api.post('/agent/ask', { question, topK })
}

/**
 * 触发离线分析
 */
export function triggerAnalysis(period) {
  return api.post('/agent/analyze', null, { params: { period } })
}

/**
 * 查看知识库状态
 */
export function getKnowledgeBaseStatus() {
  return api.get('/agent/knowledge-base')
}
