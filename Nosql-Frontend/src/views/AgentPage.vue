<template>
  <div class="agent-page">
    <div class="page-header">
      <h2>🤖 AI 智能问答</h2>
      <p>基于知识库的 RAG 问答，可分析项目趋势、语言排名、品类分布等</p>
    </div>

    <!-- 提问区 -->
    <el-card shadow="never" class="ask-card">
      <div class="search-row">
        <el-input
          v-model="question"
          size="large"
          placeholder="问点什么？例如：今年六月最火的项目是什么？Python 和 Java 哪个增长更快？"
          clearable
          @keyup.enter="doAsk"
          class="ask-input"
        >
          <template #append>
            <el-button
              type="primary"
              :loading="asking"
              @click="doAsk"
              :icon="ChatDotSquare"
            >
              提问
            </el-button>
          </template>
        </el-input>
      </div>

      <div class="quick-tags">
        <span class="tag-label">试试:</span>
        <el-tag
          v-for="tag in quickQuestions"
          :key="tag"
          class="quick-tag"
          @click="question = tag; doAsk()"
        >
          {{ tag }}
        </el-tag>
      </div>
    </el-card>

    <!-- 回答区 -->
    <el-card shadow="never" class="answer-card" v-if="answer">
      <template #header>
        <div class="answer-header">
          <span>📋 回答</span>
          <el-space>
            <el-tag v-if="strategy" type="info" size="small">
              策略: {{ strategy === 'precise' ? '精确检索' : strategy === 'semantic' ? '语义检索' : '混合检索' }}
            </el-tag>
            <el-tag v-if="confidence" :type="confidence > 0.6 ? 'success' : 'warning'" size="small">
              置信度: {{ (confidence * 100).toFixed(0) }}%
            </el-tag>
          </el-space>
        </div>
      </template>

      <div class="answer-content" v-html="renderedAnswer"></div>

      <div class="answer-sources" v-if="sources && sources.length">
        <el-divider />
        <div class="sources-title">📚 引用来源:</div>
        <el-tag
          v-for="src in sources"
          :key="src"
          type="success"
          size="small"
          class="source-tag"
        >
          {{ src }}
        </el-tag>
      </div>
    </el-card>

    <!-- 知识库状态 -->
    <el-card shadow="never" class="kb-card" v-if="kbStatus">
      <template #header>
        <span>📦 知识库状态</span>
      </template>
      <div class="kb-info">
        <el-tag type="info">共 {{ kbStatus.totalDocs }} 条文档</el-tag>
        <el-tag
          v-for="doc in kbStatus.documents"
          :key="doc.id"
          type=""
          class="kb-doc-tag"
        >
          {{ doc.type }} / {{ doc.title }}
        </el-tag>
      </div>
    </el-card>

    <!-- 对话历史 -->
    <el-card shadow="never" class="history-card" v-if="chatHistory.length > 0">
      <template #header>
        <span>💬 对话历史 ({{ chatHistory.length / 2 }} 轮)</span>
      </template>
      <div
        v-for="(msg, index) in chatHistory"
        :key="index"
        :class="['chat-message', msg.role === 'user' ? 'user-msg' : 'assistant-msg']"
      >
        <div class="msg-role">{{ msg.role === 'user' ? '🧑 你' : '🤖 AI' }}</div>
        <div class="msg-content" v-html="msg.role === 'assistant' ? formatMarkdown(msg.content) : msg.content"></div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotSquare } from '@element-plus/icons-vue'
import { askAgent, getKnowledgeBaseStatus } from '../api/agent'

const question = ref('')
const asking = ref(false)
const answer = ref('')
const sources = ref([])
const confidence = ref(0)
const strategy = ref('')
const chatHistory = ref([])
const kbStatus = ref(null)

const quickQuestions = [
  '今年六月最火的项目是什么？',
  'Python 和 Java 哪个增长更快？',
  'AI 品类最近趋势如何？',
  '编程语言活跃度排行',
  '品类分布分析'
]

const renderedAnswer = computed(() => {
  return formatMarkdown(answer.value)
})

function formatMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
    .replace(/^- (.+)$/gm, '• $1')
}

async function doAsk() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  asking.value = true
  answer.value = ''
  sources.value = []
  try {
    const q = question.value.trim()
    const res = await askAgent(q, 3)

    chatHistory.value.push({ role: 'user', content: q })
    answer.value = res.answer
    sources.value = res.sources || []
    confidence.value = res.confidence || 0
    strategy.value = res.strategy || ''
    chatHistory.value.push({ role: 'assistant', content: res.answer })

    question.value = ''
  } catch (e) {
    ElMessage.error('问答失败: ' + (e.message || '未知错误'))
  } finally {
    asking.value = false
  }
}

onMounted(async () => {
  try {
    kbStatus.value = await getKnowledgeBaseStatus()
  } catch (e) {
    // 知识库状态获取失败不影响主流程
  }
})
</script>

<style scoped>
.agent-page {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 2rem;
  font-weight: 700;
  background: linear-gradient(135deg, #f7931e, #ff6b6b);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 8px;
}

.page-header p {
  color: #909399;
  font-size: 0.95rem;
}

.ask-card {
  margin-bottom: 16px;
}

.search-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.ask-input {
  flex: 1;
}

.quick-tags {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.tag-label {
  color: #909399;
  font-size: 0.9rem;
  margin-right: 4px;
}

.quick-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.quick-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(247, 147, 30, 0.3);
}

.answer-card {
  margin-bottom: 16px;
}

.answer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.answer-content {
  font-size: 1rem;
  line-height: 1.8;
  color: #303133;
  white-space: pre-line;
}

.answer-sources {
  margin-top: 12px;
}

.sources-title {
  font-size: 0.9rem;
  color: #606266;
  margin-bottom: 8px;
}

.source-tag {
  margin-right: 8px;
  margin-bottom: 4px;
}

.kb-card {
  margin-bottom: 16px;
}

.kb-info {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.kb-doc-tag {
  font-size: 0.85rem;
}

.history-card {
  margin-bottom: 16px;
}

.chat-message {
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 8px;
}

.user-msg {
  background: #f0f2f5;
}

.assistant-msg {
  background: #ecf5ff;
}

.msg-role {
  font-weight: 600;
  font-size: 0.85rem;
  color: #606266;
  margin-bottom: 4px;
}

.msg-content {
  font-size: 0.95rem;
  line-height: 1.7;
  color: #303133;
  white-space: pre-line;
}
</style>
