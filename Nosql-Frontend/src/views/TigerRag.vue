<template>
  <div class="tiger-rag">
    <!-- 左侧：会话列表 + 顶部操作 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <div class="brand" @click="newChat">
          <el-icon :size="22"><MagicStick /></el-icon>
          <span class="brand-text">Tiger-RAG</span>
        </div>
        <el-button type="primary" plain @click="newChat" :icon="Plus" size="small">
          新建对话
        </el-button>
      </div>

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
        </div>
      </div>

      <div class="sidebar-footer">
        <el-tag size="small" type="success" effect="plain" round>
          MongoDB Atlas · 向量检索
        </el-tag>
        <el-tag size="small" type="info" effect="plain" round>
          豆包大模型
        </el-tag>
      </div>
    </div>

    <!-- 右侧：聊天主区域 -->
    <div class="chat-main">
      <!-- 欢迎屏 -->
      <div class="chat-header">
        <span>
        <el-icon :size="18" color="#409eff"><MagicStick /></el-icon>
        <span class="chat-title">{{ currentSessionTitle }}</span>
        </span>
        <el-button link type="danger" size="small" @click="clearCurrentSession" v-if="messages.length > 0">
          <el-icon><Delete /></el-icon>清空对话
        </el-button>
      </div>

      <!-- 消息列表 -->
      <div class="messages-wrap" ref="messagesRef">
        <div v-if="messages.length === 0" class="welcome-screen">
          <div class="welcome-hero">
            <div class="welcome-icon">
              <el-icon :size="56" color="#409eff"><MagicStick /></el-icon>
            </div>
            <h2>欢迎使用 Tiger-RAG 智能助手</h2>
            <p class="welcome-sub">
              基于 MongoDB Atlas 向量数据库 (projects 集合) + 豆包大模型，<br/>
              可回答关于 GitHub 项目、编程语言、技术栈的各种问题。
            </p>
            <div class="welcome-suggestions">
              <el-card
                v-for="q in suggestions"
                :key="q"
                shadow="hover"
                class="suggestion-card"
                @click="handleSuggestion(q)"
              >
                <el-icon><Promotion /></el-icon>
                <span>{{ q }}</span>
              </el-card>
            </div>
          </div>
        </div>

        <div v-else class="messages">
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
                <!-- 回答中 -->
                <div v-if="msg.content" class="bubble-content">
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
                    effect="plain"
                    :type="scoreToTagType(r.score)"
                    size="small"
                  >
                    <span class="ref-title">{{ r.title || '(无标题)' }}</span>
                    <span class="ref-score" v-if="r.score">{{ (r.score * 100).toFixed(0) }}%</span>
                  </el-tag>
                </div>
              </div>

              <div v-if="msg.role === 'assistant' && msg.durationMs" class="msg-meta">
                耗时 {{ msg.durationMs }} ms · {{ msg.usedKnowledge === false ? '未命中知识库' : '已检索知识库' }}
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
            <span class="tip">支持 Markdown</span>
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
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  MagicStick, ChatDotRound, Document, Plus, Delete, Promotion, InfoFilled
} from '@element-plus/icons-vue'
import { tigerRagChat, tigerRagClearSession } from '../api/tigerRag'

// ===== 会话状态 =====
// 会话列表：存 localStorage，可切换
const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const messagesRef = ref(null)
const inputText = ref('')
const sending = ref(false)

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

// ===== 发送消息 =====
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
  const assistantMsg = { role: 'assistant', content: '', refs: [], usedKnowledge: null, durationMs: null }
  messages.value.push(assistantMsg)

  await nextTick()
  scrollToBottom()

  try {
    const result = await tigerRagChat({ sessionId: currentSessionId.value, query: q })

    assistantMsg.content = result.answer
    assistantMsg.refs = result.refs || []
    assistantMsg.usedKnowledge = result.usedKnowledge
    assistantMsg.durationMs = result.durationMs
    // 更新 session title（用首条用户消息的前 20 字当标题）
    const cur = sessions.value.find(s => s.id === currentSessionId.value)
    if (cur && cur.title && cur.title.startsWith('新对话')) {
      cur.title = q.slice(0, 20) + (q.length > 20 ? '...' : '')
    }
  } catch (e) {
    assistantMsg.content = '抱歉，回答失败: ' + (e.message || '未知错误')
    assistantMsg.usedKnowledge = false
  } finally {
    sending.value = false
    saveSessionsToLocal()
    await nextTick()
    scrollToBottom()
  }
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
  html = html.replace(/`([^`\n]+)`/g, '<code style="background:#f5f5f5;padding:1px 6px;border-radius:3px;font-size:13px">$1</code>')
  // 换行转 <br/>
  html = html.replace(/\n/g, '<br/>')
  return html
}
</script>

<style scoped>
.tiger-rag {
  display: flex;
  height: calc(100vh - 140px);
  min-height: 600px;
  background: #f5f7fa;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

/* ============ 侧栏 ============ */
.sidebar {
  width: 240px;
  background: #fff;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #ebeef5;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
  cursor: pointer;
  margin-bottom: 12px;
}
.brand-text {
  letter-spacing: 1px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  color: #606266;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.15s;
  margin-bottom: 4px;
}
.session-item:hover { background: #f0f7ff; }
.session-item.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 500;
}
.sidebar-footer {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid #ebeef5;
}
.sidebar-footer .el-tag {
  text-align: center;
}

/* ============ 聊天主区 ============ */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.chat-header {
  padding: 14px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  font-weight: 600;
  color: #303133;
}
.chat-header span {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-title { font-size: 15px; }

.messages-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 24px 24px 0;
}

/* 欢迎屏 */
.welcome-screen {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}
.welcome-hero { text-align: center; max-width: 720px; }
.welcome-icon {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  color: #fff;
}
.welcome-icon :deep(.el-icon) { color: #fff; }
.welcome-hero h2 {
  font-size: 22px;
  color: #303133;
  margin-bottom: 8px;
}
.welcome-sub {
  color: #909399;
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 32px;
}
.welcome-suggestions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}
.suggestion-card {
  cursor: pointer;
  transition: transform 0.15s;
  text-align: left;
}
.suggestion-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}
.suggestion-card:hover {
  transform: translateY(-2px);
  border-color: #409eff;
}

/* 消息气泡 */
.messages {
  max-width: 900px;
  margin: 0 auto;
  padding-bottom: 20px;
}
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}
.message-row.user {
  flex-direction: row-reverse;
}
.avatar {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
}
.avatar-inner {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  color: #fff;
}
.avatar-inner.user {
  background: linear-gradient(135deg, #36d1dc, #5b86e5);
}
.avatar-inner.assistant {
  background: linear-gradient(135deg, #667eea, #764ba2);
}
.bubble-wrap {
  max-width: 75%;
  min-width: 40%;
}
.bubble {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.7;
  font-size: 14px;
  word-wrap: break-word;
}
.message-row.assistant .bubble {
  background: #f7f9fc;
  border: 1px solid #ebeef5;
  color: #303133;
  border-top-left-radius: 4px;
}
.message-row.user .bubble {
  background: linear-gradient(135deg, #409eff, #337ecc);
  color: #fff;
  border-top-right-radius: 4px;
}
.bubble-content :deep(strong) {
  font-weight: 700;
}
.bubble-content :deep(p) {
  margin: 0;
}

/* 正在输入... */
.loading-dots {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
}
.loading-dots span {
  width: 6px;
  height: 6px;
  background: #c0c4cc;
  border-radius: 50%;
  animation: bounce 1.2s infinite ease-in-out both;
}
.loading-dots span:nth-child(2) { animation-delay: 0.15s; }
.loading-dots span:nth-child(3) { animation-delay: 0.3s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* 引用 */
.refs {
  margin-top: 10px;
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px dashed #ebeef5;
}
.refs-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.ref-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ref-chip {
  max-width: 100%;
  display: flex !important;
  align-items: center;
  gap: 6px;
}
.ref-chip :deep(.el-tag__content) {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ref-title {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ref-score {
  color: #909399;
  font-size: 11px;
}

.msg-meta {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

/* 输入区 */
.input-area {
  border-top: 1px solid #ebeef5;
  background: #fafbfc;
  padding: 16px 24px 12px;
}
.input-inner {
  max-width: 900px;
  margin: 0 auto;
}
.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.input-footer {
  max-width: 900px;
  margin: 6px auto 0;
  font-size: 12px;
  color: #c0c4cc;
  display: flex;
  align-items: center;
  gap: 6px;
}

.tip {
  font-size: 12px;
  color: #909399;
}

/* ===== 滚动条美化 ===== */
.messages-wrap::-webkit-scrollbar,
.session-list::-webkit-scrollbar {
  width: 6px;
}
.messages-wrap::-webkit-scrollbar-thumb,
.session-list::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}
.messages-wrap::-webkit-scrollbar-track,
.session-list::-webkit-scrollbar-track {
  background: transparent;
}
</style>
