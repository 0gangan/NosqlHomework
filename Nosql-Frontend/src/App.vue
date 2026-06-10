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
  return '/'
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: #f5f7fa;
}
.app-container {
  min-height: 100vh;
}
.app-header {
  display: flex;
  align-items: center;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  padding: 0 24px;
  height: 60px;
  position: sticky;
  top: 0;
  z-index: 100;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #409eff;
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
  color: #909399;
  font-size: 13px;
  padding: 16px;
  height: 40px;
}
</style>
