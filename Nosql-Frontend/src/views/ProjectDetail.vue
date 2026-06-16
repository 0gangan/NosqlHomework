<template>
  <div class="project-detail-page" v-loading="loading">
    <el-page-header @back="$router.push('/projects')" :title="project?.fullName || '项目详情'" />

    <el-row :gutter="20" style="margin-top: 20px" v-if="project">
      <!-- 左侧：基础信息 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <div class="project-title">
                <el-avatar :size="40" :src="`https://github.com/${project.fullName?.split('/')[0]}.png`" />
                <div>
                  <h3>{{ project.fullName }}</h3>
                  <a :href="`https://github.com/${project.fullName}`" target="_blank" class="github-link">
                    <el-icon><Link /></el-icon>GitHub 主页
                  </a>
                </div>
              </div>
            </div>
          </template>

          <p class="description">{{ project.description || '暂无描述' }}</p>

          <el-row :gutter="16" class="stats-grid">
            <el-col :span="6" v-for="item in statItems" :key="item.label">
              <div class="stat-item">
                <div class="stat-num">{{ formatNumber(project[item.key]) }}</div>
                <div class="stat-lbl">{{ item.label }}</div>
              </div>
            </el-col>
          </el-row>

          <el-descriptions :column="2" border style="margin-top: 20px">
            <el-descriptions-item label="语言">
              <el-tag v-if="project.language">{{ project.language }}</el-tag>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="分类">
              {{ project.category || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="许可证">
              {{ project.license || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="默认分支">
              {{ project.defaultBranch || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="质量评分">
              <el-rate
                v-model="qualityRate"
                disabled
                show-score
                text-color="#ff9900"
              />
            </el-descriptions-item>
            <el-descriptions-item label="长期价值">
              <el-progress
                :percentage="ltvPercent"
                :stroke-width="16"
                :color="ltvColor"
              />
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDate(project.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="最近推送">
              {{ formatDate(project.lastPushAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="project.topics && project.topics.length" class="topics-section">
            <h4>技术标签</h4>
            <el-tag
              v-for="topic in project.topics"
              :key="topic"
              style="margin: 4px"
            >{{ topic }}</el-tag>
          </div>
        </el-card>

        <!-- 提交历史 -->
        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span class="section-title">提交历史</span></template>
          <el-timeline v-if="commits.length">
            <el-timeline-item
              v-for="commit in commits"
              :key="commit.sha"
              :timestamp="formatDateTime(commit.commitDate)"
              placement="top"
            >
              <el-card shadow="hover" size="small">
                <div class="commit-header">
                  <strong>{{ commit.authorLogin }}</strong>
                  <span class="commit-sha">{{ commit.sha?.substring(0, 7) }}</span>
                </div>
                <p class="commit-message">{{ commit.message }}</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无提交记录" />
          <div v-if="commitTotal > 0" style="margin-top: 16px; text-align: right">
            <el-pagination
              v-model:current-page="commitPage.page"
              :page-size="commitPage.size"
              :total="commitTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              size="small"
              @size-change="loadCommits"
              @current-change="loadCommits"
            />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：贡献者 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="section-title">贡献者 ({{ contributors.length }})</span></template>
          <div class="contributors-list" v-if="contributors.length">
            <div
              v-for="c in contributors"
              :key="c.githubId"
              class="contributor-item"
            >
              <el-avatar :size="36" :src="c.avatarUrl" />
              <div class="contributor-info">
                <div class="contributor-name">{{ c.login }}</div>
                <div class="contributor-count">{{ c.contributions }} 次贡献</div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无贡献者数据" />
        </el-card>

        <!-- 快速跳转 -->
        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span class="section-title">快速导航</span></template>
          <el-menu :default-active="''" class="quick-nav">
            <el-menu-item @click="$router.push('/search')">
              <el-icon><Search /></el-icon>智能检索相似项目
            </el-menu-item>
            <el-menu-item @click="$router.push('/projects')">
              <el-icon><List /></el-icon>返回项目列表
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && !project" description="项目不存在" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getProject, getProjectCommits, getProjectContributors } from '../api/project'

const route = useRoute()
const loading = ref(true)
const project = ref(null)
const commits = ref([])
const contributors = ref([])
const commitTotal = ref(0)
const commitPage = reactive({ page: 1, size: 20 })

const statItems = [
  { key: 'starsCount', label: 'Star' },
  { key: 'forksCount', label: 'Fork' },
  { key: 'watchersCount', label: 'Watch' },
  { key: 'openIssuesCount', label: 'Open Issues' }
]

const qualityRate = computed(() => {
  const score = project.value?.qualityScore || 0
  return Math.min(5, Math.round(score / 20))
})

const ltvPercent = computed(() => {
  const score = project.value?.longTermValue || 0
  return Math.min(100, Math.round(score))
})

const ltvColor = computed(() => {
  const p = ltvPercent.value
  if (p >= 80) return '#67c23a'
  if (p >= 60) return '#e6a23c'
  return '#f56c6c'
})

function formatNumber(n) {
  if (!n) return '0'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return n.toString()
}

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('zh-CN')
}

function formatDateTime(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN')
}

async function loadCommits() {
  if (!route.params.id) return
  try {
    const data = await getProjectCommits(route.params.id, {
      page: commitPage.page,
      size: commitPage.size
    })
    commits.value = data.records || []
    commitTotal.value = data.total || 0
  } catch (e) {
    console.error('加载提交历史失败:', e)
  }
}

onMounted(async () => {
  try {
    const id = route.params.id
    const [proj, commitData, contribData] = await Promise.all([
      getProject(id),
      getProjectCommits(id, { page: 1, size: commitPage.size }),
      getProjectContributors(id)
    ])
    project.value = proj
    commits.value = commitData.records || []
    commitTotal.value = commitData.total || 0
    contributors.value = Array.isArray(contribData) ? contribData : (contribData.records || [])
  } catch (e) {
    console.error('加载项目详情失败:', e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.project-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.project-title h3 {
  font-size: 20px;
  margin: 0;
}
.github-link {
  font-size: 13px;
  color: #f7931e;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.description {
  color: #d0d0e0;
  line-height: 1.6;
  margin-bottom: 16px;
}
.stats-grid {
  text-align: center;
}
.stat-item {
  padding: 12px 0;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: #f7931e;
}
.stat-lbl {
  font-size: 13px;
  color: #c0c0d0;
  margin-top: 4px;
}
.topics-section {
  margin-top: 20px;
}
.topics-section h4 {
  margin-bottom: 8px;
  color: #e0e0e0;
}
.section-title {
  font-weight: 600;
  font-size: 15px;
}
.commit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.commit-sha {
  font-family: monospace;
  font-size: 12px;
  color: #a0a0b8;
}
.commit-message {
  margin-top: 4px;
  color: #d0d0e0;
  font-size: 14px;
}
.commit-stats {
  margin-top: 6px;
  font-size: 13px;
}
.additions {
  color: #67c23a;
  margin-right: 8px;
}
.deletions {
  color: #f56c6c;
}
.contributors-list {
  max-height: 500px;
  overflow-y: auto;
}
.contributor-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #2a2a4a;
}
.contributor-name {
  font-weight: 500;
  font-size: 14px;
  color: #e0e0e0;
}
.contributor-count {
  font-size: 12px;
  color: #a0a0b8;
  margin-top: 2px;
}
.quick-nav {
  border-right: none;
}
</style>
