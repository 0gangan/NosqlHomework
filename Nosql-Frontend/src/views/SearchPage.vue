<template>
  <div class="search-page">
    <div class="page-header">
      <h2>智能检索</h2>
      <p>支持自然语言检索，例如 "找 Java 语言的高星标 Web 框架项目"</p>
    </div>

    <el-card shadow="never" class="search-card">
      <div class="search-input-area">
        <el-input
          v-model="query"
          size="large"
          placeholder="输入自然语言描述您想找的项目..."
          clearable
          @keyup.enter="doSearch"
        >
          <template #prepend>
            <el-select v-model="topK" style="width: 120px">
              <el-option :value="5" label="Top 5" />
              <el-option :value="10" label="Top 10" />
              <el-option :value="20" label="Top 20" />
            </el-select>
          </template>
          <template #append>
            <el-button
              type="primary"
              :loading="searching"
              @click="doSearch"
              :icon="Search"
            >
              检索
            </el-button>
          </template>
        </el-input>
      </div>

      <div class="quick-tags">
        <span class="tag-label">试试:</span>
        <el-tag
          v-for="tag in quickQueries"
          :key="tag"
          class="quick-tag"
          @click="query = tag; doSearch()"
        >
          {{ tag }}
        </el-tag>
      </div>
    </el-card>

    <!-- 检索结果 -->
    <el-card shadow="never" style="margin-top: 16px" v-if="result">
      <template #header>
        <div class="result-header">
          <span>检索结果</span>
          <el-tag v-if="result.parsedIntent" type="info" size="small">
            意图: {{ result.parsedIntent }}
          </el-tag>
          <div class="result-filters" v-if="result.language || result.category">
            <el-tag v-if="result.language" type="success" size="small">
              语言: {{ result.language }}
            </el-tag>
            <el-tag v-if="result.category" type="warning" size="small">
              分类: {{ result.category }}
            </el-tag>
          </div>
        </div>
      </template>

      <div v-if="result.items && result.items.length" class="search-results">
        <div
          v-for="(item, index) in result.items"
          :key="item.id"
          class="search-result-item"
          @click="$router.push(`/projects/${item.id}`)"
        >
          <div class="result-rank">
            <span :class="{ 'top3': index < 3 }">{{ index + 1 }}</span>
          </div>
          <div class="result-content">
            <div class="result-title">
              <span class="result-name">{{ item.fullName }}</span>
              <el-progress
                :percentage="Math.round(item.matchScore * 100)"
                :stroke-width="6"
                :color="matchScoreColor(item.matchScore)"
                style="width: 120px"
              />
            </div>
            <p class="result-desc">{{ item.description || '暂无描述' }}</p>
            <div class="result-meta">
              <el-tag size="small">{{ item.language }}</el-tag>
              <span class="meta-star">⭐ {{ formatNumber(item.starsCount) }}</span>
              <span class="match-label">
                匹配度: {{ (item.matchScore * 100).toFixed(1) }}%
              </span>
            </div>
          </div>
          <div class="result-action">
            <el-button type="primary" size="small" @click.stop="$router.push(`/projects/${item.id}`)">
              查看详情
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="未找到匹配的项目，请尝试其他关键词" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { naturalLanguageSearch } from '../api/search'
import { ElMessage } from 'element-plus'

const query = ref('')
const topK = ref(10)
const searching = ref(false)
const result = ref(null)

const quickQueries = [
  '找 Java 语言的高星标 Web 项目',
  'Python 机器学习框架',
  '前端 UI 组件库',
  'Go 语言微服务项目',
  'Rust 系统工具'
]

function formatNumber(n) {
  if (!n) return '0'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return n.toString()
}

function matchScoreColor(score) {
  if (score >= 0.8) return '#67c23a'
  if (score >= 0.6) return '#e6a23c'
  return '#f56c6c'
}

async function doSearch() {
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searching.value = true
  try {
    const res = await naturalLanguageSearch({
      query: query.value.trim(),
      topK: topK.value
    })
    result.value = res
  } catch (e) {
    console.error('检索失败:', e)
    result.value = null
  } finally {
    searching.value = false
  }
}
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.page-header h2 {
  font-size: 24px;
  color: #303133;
}
.page-header p {
  color: #909399;
  margin-top: 4px;
}
.search-input-area {
  max-width: 800px;
}
.quick-tags {
  margin-top: 16px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.tag-label {
  font-size: 13px;
  color: #909399;
}
.quick-tag {
  cursor: pointer;
}
.quick-tag:hover {
  opacity: 0.8;
}
.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
  flex-wrap: wrap;
}
.result-filters {
  display: flex;
  gap: 8px;
}
.search-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.search-result-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.search-result-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}
.result-rank {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  color: #909399;
  flex-shrink: 0;
}
.result-rank .top3 {
  color: #409eff;
}
.result-content {
  flex: 1;
  min-width: 0;
}
.result-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.result-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.result-desc {
  margin-top: 6px;
  color: #606266;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.meta-star {
  font-size: 13px;
  color: #e6a23c;
}
.match-label {
  font-size: 13px;
  color: #67c23a;
}
.result-action {
  flex-shrink: 0;
}
</style>
