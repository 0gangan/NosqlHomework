<template>
  <div class="project-list-page">
    <div class="page-header">
      <h2>项目浏览</h2>
      <p>多维度筛选与排序，快速定位目标开源项目</p>
    </div>

    <!-- 搜索与筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="语言">
          <el-select v-model="filters.language" placeholder="全部语言" clearable style="width: 140px">
            <el-option
              v-for="lang in languages"
              :key="lang"
              :label="lang"
              :value="lang"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.category" placeholder="全部分类" clearable style="width: 140px">
            <el-option
              v-for="cat in categories"
              :key="cat.value"
              :label="cat.label"
              :value="cat.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="最低星标">
          <el-input-number v-model="filters.minStars" :min="0" :step="100" style="width: 130px" />
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="filters.sortBy" style="width: 130px">
            <el-option label="更新时间" value="updatedAt" />
            <el-option label="星标数" value="starsCount" />
            <el-option label="Fork 数" value="forksCount" />
            <el-option label="贡献者数" value="contributorsCount" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 项目表格 -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-table
        :data="projects"
        v-loading="loading"
        stripe
        style="width: 100%"
        @row-click="(row) => $router.push(`/projects/${row.id}`)"
        row-class-name="clickable-row"
      >
        <el-table-column prop="fullName" label="项目名" min-width="200">
          <template #default="{ row }">
            <div class="project-cell">
              <el-avatar :size="28" :src="`https://github.com/${row.fullName?.split('/')[0]}.png`" />
              <span class="project-name">{{ row.fullName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column prop="language" label="语言" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.language" size="small">{{ row.language }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="starsCount" label="⭐ Star" width="100" sortable align="center">
          <template #default="{ row }">
            {{ formatNumber(row.starsCount) }}
          </template>
        </el-table-column>
        <el-table-column prop="forksCount" label="Fork" width="80" align="center">
          <template #default="{ row }">
            {{ formatNumber(row.forksCount) }}
          </template>
        </el-table-column>
        <el-table-column prop="openIssuesCount" label="Issues" width="80" align="center" />
        <el-table-column prop="license" label="协议" width="100">
          <template #default="{ row }">
            <span v-if="row.license" class="text-muted">{{ row.license }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastPushAt" label="最近推送" width="130">
          <template #default="{ row }">
            {{ formatDate(row.lastPushAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="$router.push(`/projects/${row.id}`)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; text-align: right">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="search"
          @current-change="search"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listProjects } from '../api/project'

const loading = ref(false)
const projects = ref([])

const filters = reactive({
  language: '',
  category: '',
  minStars: null,
  sortBy: 'updatedAt'
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const languages = [
  'Java', 'Python', 'JavaScript', 'TypeScript', 'Go', 'Rust',
  'C++', 'C', 'C#', 'Ruby', 'PHP', 'Swift', 'Kotlin', 'Scala'
]

const categories = [
  { label: 'Web 开发', value: 'Web' },
  { label: '移动开发', value: 'Mobile' },
  { label: '工具', value: 'Tool' },
  { label: '基础设施', value: 'Infrastructure' },
  { label: 'AI/ML', value: 'AI_ML' },
  { label: '数据库', value: 'Database' }
]

function formatNumber(n) {
  if (!n) return '0'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return n.toString()
}

function formatDate(d) {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('zh-CN')
}

async function search() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      sortBy: filters.sortBy
    }
    if (filters.language) params.language = filters.language
    if (filters.category) params.category = filters.category
    if (filters.minStars) params.minStars = filters.minStars

    const res = await listProjects(params)
    projects.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    console.error('加载项目列表失败:', e)
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.language = ''
  filters.category = ''
  filters.minStars = null
  filters.sortBy = 'updatedAt'
  pagination.page = 1
  search()
}

onMounted(() => {
  search()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.page-header h2 {
  font-size: 24px;
  color: #e0e0e0;
}
.page-header p {
  color: #c0c0d0;
  margin-top: 4px;
}
.filter-card :deep(.el-card__body) {
  padding: 16px 20px 0;
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
}
.project-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.project-name {
  font-weight: 500;
  color: #e0e0e0;
}
.text-muted {
  color: #a0a0b8;
  font-size: 13px;
}
:deep(.clickable-row) {
  cursor: pointer;
}
:deep(.clickable-row:hover) {
  background: rgba(247,147,30,0.08) !important;
}
</style>
