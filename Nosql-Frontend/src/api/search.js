import api from './index'

// 自然语言检索
export function naturalLanguageSearch(data) {
  return api.post('/search/nl', data)
}

// 检索历史
export function getSearchHistory(params) {
  return api.get('/search/history', { params })
}
