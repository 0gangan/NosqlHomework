<template>
  <div class="tiger-rag-page">
    <div class="page-header">
      <h2>Tiger-RAG 智能问答</h2>
      <p>基于 MongoDB Atlas 向量检索 + 大模型的 GitHub 项目智能问答系统</p>
    </div>

    <div class="chat-row">
      <!-- 左侧：会话列表 -->
      <div class="chat-col-left">
        <el-card shadow="never" class="session-card">
          <template #header>
            <div class="card-header-row">
              <span class="section-title">会话列表</span>
              <el-button type="primary" size="small" :icon="Plus" @click="newChat">新建</el-button>
            </div>
          </template>
          <div class="session-list">
            <div
              v-for="session in sessions"
              :key="session.id"
              class="session-item"
              :class="{ active: currentSessionId === session.id }"
              @click="switchSession(session.id)"
            >
              <el-icon><ChatDotRound /></el-icon>
              <span class="session-title">{{ session.title }}</span>
              <el-button
                v-if="currentSessionId === session.id && messages.length > 0"
                link
                type="danger"
                size="small"
                class="clear-btn"
                @click.stop="clearCurrentSession"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="60" />
          </div>
        </el-card>
      </div>

      <!-- 右侧：聊天主区域 -->
      <div class="chat-col-right">
        <div class="chat-panel">
          <!-- 欢迎屏 -->
          <div v-if="messages.length === 0" class="welcome-screen">
            <div class="welcome-hero">
              <div class="welcome-icon">
                <el-icon :size="56" color="#f7931e"><MagicStick /></el-icon>
              </div>
              <h2>欢迎使用 Tiger-RAG 智能助手</h2>
              <p class="welcome-sub">
                基于 MongoDB Atlas 向量数据库 (projects 集合) + 大模型，<br/>
                可回答关于 GitHub 项目、编程语言、技术栈的各种问题。
              </p>
              <div class="welcome-suggestions">
                <div
                  v-for="q in suggestions"
                  :key="q"
                  class="suggestion-card"
                  @click="handleSuggestion(q)"
                >
                  <el-icon><Promotion /></el-icon>
                  <span>{{ q }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 消息列表 -->
          <div v-else class="messages-wrap" ref="messagesRef">
            <div class="messages">
              <div
                v-for="(msg, idx) in messages"
                :key="idx"
                class="message-row"
                :class="msg.role"
              >
                <div class="avatar">
                  <span v-if="msg.role === 'user'" class="avatar-inner user">U</span>
                  <span v-else class="avatar-inner assistant">
                    <el-icon :size="16"><MagicStick /></el-icon>
                  </span>
                </div>

                <div class="bubble-wrap">
                  <div class="bubble">
                    <div v-if="msg.role === 'assistant' && msg === lastMsg && streamingContent !== null" class="bubble-content">
                      <p v-html="renderMarkdown(streamingContent)" />
                    </div>
                    <div v-else-if="msg.content" class="bubble-content">
                      <p v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)" />
                      <p v-else>{{ msg.content }}</p>
                    </div>
                    <div v-else class="loading-dots">
                      <span /><span /><span />
                    </div>
                  </div>

                  <!-- 引用项目 -->
                  <div v-if="msg.role === 'assistant' && msg.refs && msg.refs.length" class="refs">
                    <div class="refs-title">
                      <el-icon :size="12"><Document /></el-icon>
                      <span>参考项目 ({{ msg.refs.length }})</span>
                    </div>
                    <div class="ref-chips">
                      <el-tag
                        v-for="r in msg.refs"
                        :key="r.docId"
                        class="ref-chip"
                        :type="scoreToTagType(r.score)"
                        size="small"
                      >
                        <span class="ref-title">{{ r.title || '(无标题)' }}</span>
                        <span class="ref-score" v-if="r.score">{{ (r.score * 100).toFixed(0) }}%</span>
                      </el-tag>
                    </div>
                  </div>

                  <div v-if="msg.role === 'assistant' && msg.durationMs" class="msg-meta">
                    耗时 {{ msg.durationMs }} ms ·
                    <span v-if="msg.knowledgeSource === 'structured'">精确查询 (数据库)</span>
                    <span v-else-if="msg.knowledgeSource === 'rag'">项目知识库 (最高相似度 {{ msg.highestScore && msg.highestScore.toFixed(2) }})</span>
                    <span v-else-if="msg.knowledgeSource === 'hybrid'">混合模式 (最高相似度 {{ msg.highestScore && msg.highestScore.toFixed(2) }}, 已结合通用知识)</span>
                    <span v-else-if="msg.knowledgeSource === 'llm'">通用知识 (LLM)</span>
                    <span v-else>{{ msg.usedKnowledge === false ? '未命中知识库' : '已检索知识库' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部输入区 -->
          <div class="input-area">
            <div class="input-inner">
              <el-input
                v-model="inputText"
                type="textarea"
                :rows="2"
                :disabled="sending"
                placeholder="向 Tiger-RAG 提问... (Enter 发送, Shift+Enter 换行)"
                resize="none"
                @keydown="handleKey"
              />
              <div class="input-actions">
                <el-button
                  type="primary"
                  :loading="sending"
                  :disabled="!inputText.trim()"
                  :icon="Promotion"
                  @click="sendMessage"
                >
                  发送
                </el-button>
              </div>
            </div>
            <div class="input-footer">
              <el-icon><InfoFilled /></el-icon>
              <span>Tiger-RAG 的回答由大语言模型生成，不能完全准确，请自行甄别关键信息。</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  MagicStick, ChatDotRound, Document, Plus, Delete, Promotion, InfoFilled
} from '@element-plus/icons-vue'
import { tigerRagStreamChat, tigerRagClearSession } from '../api/tigerRag'

// ===== 会话状态 =====
// 会话列表：存 localStorage，可切换
const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const messagesRef = ref(null)
const inputText = ref('')
const sending = ref(false)
const streamingContent = ref(null)  // 流式输出的独立 ref，突破 Vue 批量合并
let activeStream = null  // 当前活跃的 SSE 连接，用于取消

// 计算属性：最后一条消息
const lastMsg = computed(() => {
  const msgs = messages.value
  return msgs.length > 0 ? msgs[msgs.length - 1] : null
})

const SESSION_STORAGE_KEY = 'tiger-rag-sessions-v1'
const CURRENT_KEY = 'tiger-rag-current-v1'

const currentSessionTitle = computed(() => {
  const s = sessions.value.find(s => s.id === currentSessionId.value)
  return s ? s.title : '新对话'
})

// 常见问题提示
const suggestions = [
  '帮我介绍一下 GitHub 上 Java 语言的热门项目',
  '什么是向量数据库？MongoDB Atlas 如何支持向量检索？',
  'RAG (Retrieval-Augmented Generation) 是什么？',
  '请推荐一些学习 Spring Boot 的学习资源',
  '给我介绍一下开源项目 NewPipe'
]

// ===== 初始化
onMounted(() => {
  loadSessionsFromLocal()
  if (!currentSessionId.value || sessions.value.length === 0) {
    newChat()
  } else {
    // 恢复当前会话的消息（从当前进程内的内存存储）
    const cur = sessions.value.find(s => s.id === currentSessionId.value)
    if (cur && cur.messages) messages.value = cur.messages
  }
})

onUnmounted(() => {
  if (activeStream) {
    activeStream.close()
    activeStream = null
  }
})

function loadSessionsFromLocal() {
  try {
    const s = localStorage.getItem(SESSION_STORAGE_KEY)
    if (s) sessions.value = JSON.parse(s)
    const c = localStorage.getItem(CURRENT_KEY)
    if (c) currentSessionId.value = c
  } catch (e) {}
}
function saveSessionsToLocal() {
  try {
    // 同步消息到当前会话
    const cur = sessions.value.find(s => s.id === currentSessionId.value)
    if (cur) cur.messages = messages.value
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(sessions.value))
    localStorage.setItem(CURRENT_KEY, currentSessionId.value)
  } catch (e) {}
}

function newChat() {
  // 保存当前会话的消息
  saveSessionsToLocal()
  const id = 'sess_' + Date.now()
  const title = '新对话 ' + new Date().toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
  sessions.value.unshift({ id, title, messages: [] })
  currentSessionId.value = id
  messages.value = []
  saveSessionsToLocal()
}

function switchSession(id) {
  // 先把当前消息写回
  const cur = sessions.value.find(s => s.id === currentSessionId.value)
  if (cur) cur.messages = messages.value

  currentSessionId.value = id
  const target = sessions.value.find(s => s.id === id)
  messages.value = target ? (target.messages || []) : []
  saveSessionsToLocal()
}

async function clearCurrentSession() {
  try {
    if (currentSessionId.value) {
      await tigerRagClearSession(currentSessionId.value)
    }
  } catch (e) {}
  messages.value = []
  saveSessionsToLocal()
  ElMessage.success('已清空当前对话')
}

function handleSuggestion(q) {
  inputText.value = q
  sendMessage()
}

function handleKey(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

// ===== 发送消息 (SSE 流式) =====
async function sendMessage() {
  const q = inputText.value.trim()
  if (!q) return
  if (sending.value) return

  // 确保有一个有效 session
  if (!currentSessionId.value) newChat()

  const userMsg = { role: 'user', content: q }
  messages.value.push(userMsg)
  inputText.value = ''
  sending.value = true

  // 空的"正在输入"气泡
  const assistantMsg = { role: 'assistant', content: '', refs: [], usedKnowledge: null, durationMs: null, knowledgeSource: null }
  messages.value.push(assistantMsg)

  await nextTick()
  scrollToBottom()

  // 断开之前的 SSE 连接（如果有）
  if (activeStream) {
    activeStream.close()
    activeStream = null
  }

  let firstToken = true

  activeStream = tigerRagStreamChat({
    query: q,
    sessionId: currentSessionId.value,
    onToken: (token) => {
      if (firstToken) {
        streamingContent.value = ''
        firstToken = false
      }
      streamingContent.value += token
    },
    onDone: (answer) => {
      activeStream = null
      // 将流式内容迁移到消息对象
      assistantMsg.content = streamingContent.value || ''
      streamingContent.value = null
      if (answer && answer.refs) {
        assistantMsg.refs = answer.refs
      }
      if (answer) {
        assistantMsg.usedKnowledge = answer.usedKnowledge
        assistantMsg.knowledgeSource = answer.knowledgeSource
        assistantMsg.durationMs = answer.durationMs
        assistantMsg.highestScore = answer.highestScore
      }
      sending.value = false

      // 更新 session title
      const cur = sessions.value.find(s => s.id === currentSessionId.value)
      if (cur && cur.title && cur.title.startsWith('新对话')) {
        cur.title = q.slice(0, 20) + (q.length > 20 ? '...' : '')
      }
      saveSessionsToLocal()
    },
    onError: (error) => {
      activeStream = null
      const text = streamingContent.value || ''
      streamingContent.value = null
      assistantMsg.content = text
      if (!assistantMsg.content) {
        assistantMsg.content = '抱歉，回答失败: ' + error
      } else {
        assistantMsg.content += '\n\n---\n⚠️ 回答中断: ' + error
      }
      assistantMsg.usedKnowledge = false
      sending.value = false
      saveSessionsToLocal()
    }
  })
}

function scrollToBottom() {
  if (!messagesRef.value) return
  const el = messagesRef.value
  el.scrollTop = el.scrollHeight
}

// ===== 小工具 =====
function scoreToTagType(score) {
  if (score == null) return 'info'
  if (score >= 0.8) return 'success'
  if (score >= 0.6) return 'warning'
  return 'info'
}

// 非常简易 markdown：支持 **粗体** + 列表 + 换行；不引入库避免额外依赖
function renderMarkdown(text) {
  if (!text) return ''
  // 先转义 HTML 原文 < >
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // **bold**
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  // *italic*
  html = html.replace(/(^|\W)\*([^*\n]+)\*/g, '$1<em>$2</em>')
  // `code`
  html = html.replace(/`([^`\n]+)`/g, '<code style="background:#2a2a4a;color:#f7931e;padding:1px 6px;border-radius:3px;font-size:13px">$1</code>')
  // 换行转 <br/>
  html = html.replace(/\n/g, '<br/>')
  return html
}
</script>

<style scoped>
.tiger-rag-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
}
.chat-row {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 20px;
}
.chat-col-left {
  width: 20%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.chat-col-right {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.page-header {
  margin-bottom: 16px;
}
.page-header h2 {
  font-size: 22px;
  color: #e0e0e0;
  margin: 0;
}
.page-header p {
  color: #a0a0b8;
  margin-top: 4px;
  font-size: 13px;
}

/* ============ 会话列表 ============ */
.session-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.session-card :deep(.el-card__body) {
  flex: 1 0 0;
  overflow: hidden;
  padding: 0;
  min-height: 0;
}
.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.section-title {
  font-weight: 600;
  font-size: 15px;
}
.session-list {
  height: 100%;
  overflow-y: auto;
  padding: 8px;
}
.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  color: #c0c0d0;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
  margin-bottom: 4px;
}
.session-item:hover { background: #2a2a4a; }
.session-item.active {
  background: rgba(247, 147, 30, 0.12);
  color: #f7931e;
  font-weight: 500;
}
.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.clear-btn {
  opacity: 0.5;
  flex-shrink: 0;
}
.clear-btn:hover { opacity: 1; }

/* ============ 聊天面板 ============ */
.chat-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: #1e1e36;
  border: 1px solid #2a2a4a;
  border-radius: 8px;
  overflow: hidden;
}

/* 欢迎屏 */
.welcome-screen {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px;
}
.welcome-hero { text-align: center; }
.welcome-icon {
  margin-bottom: 20px;
}
.welcome-screen h2 {
  font-size: 22px;
  color: #e0e0e0;
  margin: 0 0 12px;
}
.welcome-sub {
  color: #a0a0b8;
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 28px;
}
.welcome-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 640px;
  margin: 0 auto;
}
.suggestion-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: 1px solid #2a2a4a;
  border-radius: 8px;
  background: #1e1e36;
  cursor: pointer;
  color: #c0c0d0;
  font-size: 13px;
  transition: all 0.2s;
}
.suggestion-card:hover {
  border-color: #f7931e;
  color: #f7931e;
  box-shadow: 0 2px 8px rgba(247,147,30,0.15);
}

/* 消息列表 */
.messages-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}
.messages {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.message-row {
  display: flex;
  gap: 12px;
}
.message-row.user { flex-direction: row-reverse; }
.avatar { flex-shrink: 0; }
.avatar-inner {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
}
.avatar-inner.user {
  background: #f7931e;
  color: #fff;
}
.avatar-inner.assistant {
  background: rgba(247, 147, 30, 0.15);
  color: #f7931e;
}
.bubble-wrap {
  max-width: 75%;
  min-width: 0;
}
.bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
}
.message-row.user .bubble {
  background: #f7931e;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.message-row.assistant .bubble {
  background: #1e1e36;
  color: #e0e0e0;
  border: 1px solid #2a2a4a;
  border-bottom-left-radius: 4px;
}
.bubble-content :deep(strong) { color: #f7931e; }
.bubble-content :deep(code) {
  background: #2a2a4a;
  color: #f7931e;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 13px;
}
.loading-dots {
  display: flex;
  gap: 6px;
  padding: 8px 0;
}
.loading-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #a0a0b8;
  animation: dot-bounce 1.2s infinite;
}
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

/* 引用项目 */
.refs {
  margin-top: 10px;
  padding: 10px 12px;
  background: rgba(42, 42, 74, 0.4);
  border-radius: 8px;
  border: 1px dashed #2a2a4a;
}
.refs-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #a0a0b8;
  margin-bottom: 6px;
}
.ref-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ref-chip { cursor: default; }
.ref-score {
  margin-left: 6px;
  font-weight: 600;
}
.msg-meta {
  margin-top: 6px;
  font-size: 11px;
  color: #a0a0b8;
}

/* 输入区 */
.input-area {
  flex-shrink: 0;
  padding: 12px 20px 16px;
  border-top: 1px solid #2a2a4a;
  background: #1a1a2e;
}
.input-inner {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.input-inner :deep(.el-textarea__inner) {
  background: #1e1e36;
  border-color: #2a2a4a;
  color: #e0e0e0;
}
.input-inner :deep(.el-textarea__inner:focus) {
  border-color: #f7931e;
}
.input-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}
.tip {
  font-size: 11px;
  color: #606070;
}
.input-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 11px;
  color: #606070;
}
</style>
