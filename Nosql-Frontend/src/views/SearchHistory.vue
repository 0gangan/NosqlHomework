<template>
  <div class="history-page">
    <div class="page-header">
      <h2>检索历史</h2>
      <p>查看历史检索记录与分析结果</p>
    </div>

    <el-card shadow="never">
      <el-table :data="records" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="query" label="查询语句" min-width="250" show-overflow-tooltip />
        <el-table-column prop="queryType" label="查询类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.queryType === 'natural_language'" type="success" size="small">
              自然语言
            </el-tag>
            <el-tag v-else type="info" size="small">{{ row.queryType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="intent" label="意图" width="140" align="center">
          <template #default="{ row }">
            {{ row.intent || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="匹配度" width="100" align="center">
          <template #default="{ row }">
            <el-progress
              v-if="row.matchScore != null"
              :percentage="Math.round((row.matchScore || 0) * 100)"
              :stroke-width="10"
              :color="matchColor(row.matchScore)"
              style="width: 70px; display: inline-block"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="校验状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.validated" type="success" size="small">已校验</el-tag>
            <el-tag v-else type="warning" size="small">待校验</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
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
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSearchHistory } from '../api/search'

const loading = ref(false)
const records = ref([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

function formatDateTime(d) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN')
}

function matchColor(score) {
  if (score >= 0.8) return '#67c23a'
  if (score >= 0.6) return '#e6a23c'
  return '#f56c6c'
}

async function loadHistory() {
  loading.value = true
  try {
    const res = await getSearchHistory({
      page: pagination.page,
      size: pagination.size
    })
    records.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    console.error('加载检索历史失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadHistory()
})
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
</style>
