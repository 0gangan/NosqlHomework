<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="logo" @click="$router.push('/')">
        <el-icon size="24"><DataAnalysis /></el-icon>
        <span class="logo-text">GitHub 开源项目分析平台</span>
      </div>
      <el-menu
        mode="horizontal"
        :default-active="activeMenu"
        router
        class="header-menu"
      >
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/projects">项目浏览</el-menu-item>
        <el-menu-item index="/search">智能检索</el-menu-item>
        <el-menu-item index="/agent">AI 问答</el-menu-item>
        <el-menu-item index="/history">检索历史</el-menu-item>
      </el-menu>
    </el-header>
    <el-main class="app-main">
      <router-view />
    </el-main>
    <el-footer class="app-footer">
      GitHub 开源项目分析平台 © 2025 — 面向高校科研场景
    </el-footer>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/projects')) return '/projects'
  if (path.startsWith('/search')) return '/search'
  if (path.startsWith('/history')) return '/history'
  if (path.startsWith('/agent')) return '/agent'
  return '/'
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
:root {
  --el-color-primary: #f7931e;
  --el-color-primary-light-3: #f9b45c;
  --el-color-primary-light-5: #fbc98a;
  --el-color-primary-light-7: #fddeb8;
  --el-color-primary-light-8: #fee8cd;
  --el-color-primary-light-9: #fef2e2;
  --el-color-primary-dark-2: #e07d0e;
}
body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: #1a1a2e;
  color: #e0e0e0;
}
.app-container {
  min-height: 100vh;
}
.app-header {
  display: flex;
  align-items: center;
  background: #16162a;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  padding: 0 24px;
  height: 60px;
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid #2a2a4a;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #f7931e;
  margin-right: 40px;
  white-space: nowrap;
}
.logo-text {
  font-size: 18px;
  font-weight: 700;
}
.header-menu {
  flex: 1;
  border-bottom: none !important;
  background: transparent !important;
  --el-menu-text-color: #ffffff !important;
  --el-menu-hover-text-color: #f7931e !important;
  --el-menu-active-color: #f7931e !important;
}
.header-menu :deep(.el-menu-item),
.header-menu :deep(.el-sub-menu__title) {
  color: var(--el-menu-text-color) !important;
}
.header-menu :deep(.el-menu-item.is-active) {
  color: var(--el-menu-active-color) !important;
  border-bottom-color: #f7931e !important;
}
.header-menu :deep(.el-menu-item:hover),
.header-menu :deep(.el-sub-menu__title:hover) {
  color: var(--el-menu-hover-text-color) !important;
  background: rgba(247,147,30,0.08) !important;
}
.app-main {
  min-height: calc(100vh - 100px);
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
}
.app-footer {
  text-align: center;
  color: #6a6a8a;
  font-size: 13px;
  padding: 16px;
  height: 40px;
  border-top: 1px solid #2a2a4a;
}

/* ====== 全局覆盖 Element Plus 白色组件底色 ====== */
/* 卡片 */
.el-card {
  background: #252542 !important;
  border-color: #2a2a4a !important;
  color: #e0e0e0 !important;
}
.el-card__header {
  background: #1e1e36 !important;
  border-bottom-color: #2a2a4a !important;
  color: #e0e0e0 !important;
}
.el-card__body {
  background: #252542 !important;
  color: #e0e0e0 !important;
}

/* 表格 */
.el-table {
  --el-table-bg-color: #252542 !important;
  --el-table-tr-bg-color: #252542 !important;
  --el-table-header-bg-color: #1e1e36 !important;
  --el-table-row-hover-bg-color: rgba(247,147,30,0.06) !important;
  --el-table-border-color: #2a2a4a !important;
  --el-table-text-color: #e0e0e0 !important;
  --el-table-header-text-color: #c0c0d0 !important;
  background: #252542 !important;
  color: #e0e0e0 !important;
}
.el-table th.el-table__cell {
  background: #1e1e36 !important;
  color: #c0c0d0 !important;
}
.el-table tr {
  background: #252542 !important;
}
.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background: #22223e !important;
}

/* 分页 */
.el-pagination {
  --el-pagination-bg-color: transparent !important;
  --el-pagination-text-color: #c0c0d0 !important;
}
.el-pagination button,
.el-pager li {
  background: #252542 !important;
  color: #c0c0d0 !important;
}

/* 输入框 / 下拉框 */
.el-input__wrapper,
.el-textarea__inner {
  background: #1e1e36 !important;
  border-color: #2a2a4a !important;
  box-shadow: none !important;
}
.el-input__inner {
  color: #e0e0e0 !important;
}
.el-select .el-input__wrapper {
  background: #1e1e36 !important;
}
.el-input-number .el-input__wrapper {
  background: #1e1e36 !important;
}

/* 描述列表 */
.el-descriptions {
  --el-descriptions-table-bg: #252542 !important;
  --el-descriptions-item-bordered-label-background: #1e1e36 !important;
}
.el-descriptions__body .el-descriptions__table {
  background: #252542 !important;
}
.el-descriptions__body .el-descriptions__table tbody tr td,
.el-descriptions__body .el-descriptions__table tbody tr th {
  background: #252542 !important;
  border-color: #2a2a4a !important;
}
.el-descriptions__label {
  color: #c0c0d0 !important;
  background: #1e1e36 !important;
}
.el-descriptions__content {
  color: #e0e0e0 !important;
  background: #252542 !important;
}

/* 时间线 */
.el-timeline-item__timestamp {
  color: #8a8aaa !important;
}

/* 表单标签 */
.el-form-item__label {
  color: #c0c0d0 !important;
}

/* 空状态 */
.el-empty__description p {
  color: #6a6a8a !important;
}

/* Page Header */
.el-page-header {
  --el-page-header-text-color: #e0e0e0 !important;
}
.el-page-header__left {
  color: #c0c0d0 !important;
}
.el-page-header__title {
  color: #e0e0e0 !important;
}
.el-page-header .el-icon {
  color: #f7931e !important;
}

/* 时间线 */
.el-timeline-item__timestamp {
  color: #c0c0d0 !important;
}
.el-timeline-item__node {
  background: #f7931e !important;
}

/* 卡片内的菜单（如快速导航） */
.el-card .el-menu {
  background: transparent !important;
  border-right: none !important;
}
.el-card .el-menu-item {
  color: #c0c0d0 !important;
  background: transparent !important;
}
.el-card .el-menu-item:hover {
  color: #f7931e !important;
  background: rgba(247,147,30,0.08) !important;
}

/* 评分组件 */
.el-rate {
  --el-rate-fill-color: #f7931e !important;
}

/* 进度条底色 */
.el-progress-bar__outer {
  background: #2a2a4a !important;
}

/* 下拉菜单面板 */
.el-select-dropdown,
.el-dropdown-menu {
  background: #252542 !important;
  border-color: #2a2a4a !important;
}
.el-select-dropdown__item {
  color: #e0e0e0 !important;
}
.el-select-dropdown__item.hover,
.el-select-dropdown__item:hover {
  background: rgba(247,147,30,0.1) !important;
}

/* Tooltip / Popover */
.el-tooltip__trigger,
.el-popover {
  background: #252542 !important;
  border-color: #2a2a4a !important;
  color: #e0e0e0 !important;
}
</style>
