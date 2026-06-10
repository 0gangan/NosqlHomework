import api from './index'

// 获取 Owner 详情
export function getOwner(id) {
  return api.get(`/owners/${id}`)
}

// 按类型获取 Owner 列表
export function listOwnersByType(type) {
  return api.get(`/owners/type/${type}`)
}

// 获取最活跃 Owner
export function getTopActiveOwners() {
  return api.get('/owners/top-active')
}
