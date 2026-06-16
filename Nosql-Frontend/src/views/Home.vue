<template>
  <div class="home-page">
    <div class="hero-section">
      <h1>GitHub 开源项目分析平台</h1>
      <p>面向高校科研场景，提供多维数据分析、自然语言检索与趋势预测能力</p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="$router.push('/projects')">
          <el-icon><FolderOpened /></el-icon>浏览项目
        </el-button>
        <el-button size="large" @click="$router.push('/search')">
          <el-icon><Search /></el-icon>智能检索
        </el-button>
      </div>
    </div>

    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" :style="{ background: stat.color }">
            <el-icon size="28"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>语言分布</span>
              <el-button type="primary" link @click="$router.push('/projects')">查看更多</el-button>
            </div>
          </template>
          <div ref="langChartRef" style="height: 350px"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>热门项目 TOP 10</span>
              <el-button type="primary" link @click="$router.push('/projects')">查看更多</el-button>
            </div>
          </template>
          <div class="top-projects">
            <div
              v-for="(project, index) in topProjects"
              :key="project.id"
              class="top-project-item"
              @click="$router.push(`/projects/${project.id}`)"
            >
              <span class="rank" :class="{ 'top3': index < 3 }">{{ index + 1 }}</span>
              <div class="project-info">
                <div class="project-name">{{ project.fullName }}</div>
                <div class="project-meta">
                  <el-tag size="small">{{ project.language }}</el-tag>
                  <span class="stars">⭐ {{ formatNumber(project.starsCount) }}</span>
                </div>
              </div>
            </div>
            <el-empty v-if="topProjects.length === 0" description="暂无数据" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { listProjects } from '../api/project'
import { listOwnersByType } from '../api/owner'
import { getSearchHistory } from '../api/search'
import * as echarts from 'echarts'

const topProjects = ref([])
const langChartRef = ref(null)

const stats = ref([
  { label: '开源项目', value: '-', icon: 'FolderOpened', color: 'rgba(247,147,30,0.12)' },
  { label: '编程语言', value: '-', icon: 'Monitor', color: 'rgba(247,147,30,0.12)' },
  { label: '组织/用户', value: '-', icon: 'User', color: 'rgba(247,147,30,0.12)' },
  { label: '检索记录', value: '-', icon: 'Search', color: 'rgba(247,147,30,0.12)' }
])

// 图表使用的语言列表 — 仅作为查询参数传后端，实际数量来自 API 返回值
const chartLanguages = ['Java', 'Python', 'JavaScript', 'Go', 'TypeScript', 'Rust', 'C++', 'Ruby']

function formatNumber(n) {
  if (n == null) return '-'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

async function loadData() {
  try {
    // 并行请求所有统计数据，各自失败不影响其他
    const [projRes] = await Promise.allSettled([
      listProjects({ page: 1, size: 1 }),
    ])
    if (projRes.status === 'fulfilled') {
      stats.value[0].value = formatNumber(projRes.value.total)
    }

    // 组织和用户数量
    const [userRes, orgRes] = await Promise.allSettled([
      listOwnersByType('User'),
      listOwnersByType('Organization')
    ])
    let ownerTotal = 0
    if (userRes.status === 'fulfilled' && Array.isArray(userRes.value)) ownerTotal += userRes.value.length
    if (orgRes.status === 'fulfilled' && Array.isArray(orgRes.value)) ownerTotal += orgRes.value.length
    if (ownerTotal > 0) stats.value[2].value = formatNumber(ownerTotal)

    // 检索记录总数
    const historyRes = await Promise.allSettled([
      getSearchHistory({ page: 1, size: 1 })
    ]).then(r => r[0])
    if (historyRes.status === 'fulfilled') {
      stats.value[3].value = formatNumber(historyRes.value.total)
    }

    // 热门项目
    const topRes = await listProjects({ page: 1, size: 10, sortBy: 'starsCount' })
    topProjects.value = topRes.records || []

    // 语言分布 (一次查询，同时用于图表和"编程语言"统计)
    await nextTick()
    renderLangChart()
  } catch (e) {
    console.error('加载首页数据失败:', e)
  }
}

function renderLangChart() {
  if (!langChartRef.value) return
  const chart = echarts.init(langChartRef.value)

  const langQueries = chartLanguages.map(lang =>
    listProjects({ page: 1, size: 1, language: lang }).catch(() => ({ total: 0 }))
  )

  Promise.all(langQueries).then((results) => {
    const data = chartLanguages
      .map((lang, i) => ({ name: lang, value: results[i].total || 0 }))
      .filter(d => d.value > 0)

    // 更新"编程语言"统计卡片
    stats.value[1].value = formatNumber(data.length)

    if (data.length === 0) {
      chart.setOption({
        title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#909399', fontSize: 14 } }
      })
      return
    }

    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie',
        radius: ['45%', '75%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#1e1e36', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{d}%' },
        data: data
      }]
    })
  })

  window.addEventListener('resize', () => chart.resize())
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.hero-section {
  text-align: center;
  padding: 48px 0;
  background: linear-gradient(135deg, #1a1a2e 0%, #252542 40%, #2d1f0e 100%);
  border-radius: 12px;
  border: 1px solid #3a3020;
  color: #e0e0e0;
  margin-bottom: 24px;
}
.hero-section h1 {
  font-size: 32px;
  margin-bottom: 12px;
  color: #f7931e;
}
.hero-section p {
  font-size: 16px;
  opacity: 0.8;
  margin-bottom: 24px;
  color: #b0b0c0;
}
.hero-actions :deep(.el-button--primary) {
  background: #f7931e;
  border-color: #f7931e;
}
.hero-actions :deep(.el-button--primary:hover) {
  background: #e07d0e;
  border-color: #e07d0e;
}
.hero-actions :deep(.el-button:not(.el-button--primary)) {
  background: rgba(255,255,255,0.1);
  border-color: rgba(255,255,255,0.2);
  color: #e0e0e0;
}
.hero-actions :deep(.el-button:not(.el-button--primary):hover) {
  background: rgba(255,255,255,0.2);
  border-color: #f7931e;
  color: #f7931e;
}
.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}
.stats-row {
  margin-top: 0;
}
.stat-card {
  background: #1e1e36;
  border: 1px solid #2a2a4a;
}
.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #1e1e36;
}
.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #f7931e;
  background: rgba(247,147,30,0.12) !important;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #f7931e;
}
.stat-label {
  font-size: 14px;
  color: #c0c0d0;
  margin-top: 4px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.card-header :deep(.el-button--primary.is-link) {
  color: #f7931e;
}
.top-projects {
  max-height: 350px;
  overflow-y: auto;
}
.top-project-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #2a2a4a;
  cursor: pointer;
  transition: background 0.2s;
}
.top-project-item:hover {
  background: rgba(247,147,30,0.06);
  border-radius: 6px;
}
.rank {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: #2a2a4a;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #8a8aaa;
  flex-shrink: 0;
}
.rank.top3 {
  background: rgba(247,147,30,0.2);
  color: #f7931e;
}
.project-info {
  flex: 1;
  min-width: 0;
}
.project-name {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #e0e0e0;
}
.project-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.stars {
  font-size: 12px;
  color: #e6a23c;
}
</style>
