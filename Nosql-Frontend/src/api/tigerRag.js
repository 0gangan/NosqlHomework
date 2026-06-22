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

/**
 * SSE 流式问答 (推荐)：通过 Server-Sent Events 实时推送 LLM token。
 * 无需轮询，体验最佳。
 *
 * @param {Object}   params
 * @param {string}   params.query       - 用户问题
 * @param {string}   [params.sessionId] - 会话 ID
 * @param {number}   [params.topK]      - 检索返回数量
 * @param {number}   [params.minScore]  - 相似度阈值
 * @param {Function} params.onToken     - 收到新 token 时的回调 (token: string)
 * @param {Function} params.onDone      - 回答完成时的回调 (answer: RagAnswer)
 * @param {Function} params.onError     - 出错时的回调 (error: string)
 * @returns {EventSource} 可用来手动关闭连接
 */
export function tigerRagStreamChat({ query, sessionId, topK, minScore, onToken, onDone, onError }) {
  const params = new URLSearchParams({ query })
  if (sessionId) params.append('sessionId', sessionId)
  if (topK != null) params.append('topK', topK)
  if (minScore != null) params.append('minScore', minScore)

  const url = `/api/tiger-rag/chat/stream?${params.toString()}`
  const es = new EventSource(url)

  es.addEventListener('token', (e) => {
    if (onToken) onToken(e.data)
  })

  es.addEventListener('done', (e) => {
    es.close()
    if (onDone) {
      try {
        onDone(JSON.parse(e.data))
      } catch {
        onDone(e.data)
      }
    }
  })

  es.addEventListener('error', (e) => {
    es.close()
    if (onError) {
      // 如果 error 事件有 data，使用它；否则给默认消息
      const msg = (e.data && e.data !== '') ? e.data : '连接中断，请重试'
      onError(msg)
    }
  })

  // EventSource 自身的 onerror (网络错误等)
  es.onerror = () => {
    // 如果连接已关闭 (readyState === 2)，说明已经被 done/error 事件处理了
    if (es.readyState === EventSource.CLOSED) return
    es.close()
    if (onError) onError('连接中断，请重试')
  }

  return es
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
