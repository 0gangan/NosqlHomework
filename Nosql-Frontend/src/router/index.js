import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/projects',
    name: 'ProjectList',
    component: () => import('../views/ProjectList.vue')
  },
  {
    path: '/projects/:id',
    name: 'ProjectDetail',
    component: () => import('../views/ProjectDetail.vue')
  },
  {
    path: '/search',
    name: 'SearchPage',
    component: () => import('../views/SearchPage.vue')
  },
  {
    path: '/history',
    name: 'SearchHistory',
    component: () => import('../views/SearchHistory.vue')
  },
  {
    path: '/agent',
    name: 'AgentPage',
    component: () => import('../views/AgentPage.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
