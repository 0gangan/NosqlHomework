import api from './index'

// 手动触发采集
export function triggerCrawler(params) {
  return api.post('/crawler/trigger', null, { params })
}
