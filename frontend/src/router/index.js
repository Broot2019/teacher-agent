import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
  { path: '/', component: () => import('@/views/HomeView.vue') },
  { path: '/dashboard', component: () => import('@/views/DashboardView.vue'), meta: { adminOnly: true } },
  { path: '/llm-config', component: () => import('@/views/LlmConfigView.vue'), meta: { adminOnly: true } },
  { path: '/course-config', component: () => import('@/views/CourseConfigView.vue') },
  { path: '/knowledge-base', component: () => import('@/views/KnowledgeBaseView.vue') },
  { path: '/qa', component: () => import('@/views/QaAssistantView.vue') },
  { path: '/lesson-plan', component: () => import('@/views/LessonPlanView.vue') },
  { path: '/question-bank', component: () => import('@/views/QuestionBankView.vue') },
  { path: '/question-bank-mgmt', component: () => import('@/views/QuestionBankManagementView.vue') },
  { path: '/materials', component: () => import('@/views/MaterialLibraryView.vue') },
  { path: '/history', component: () => import('@/views/HistoryView.vue') },
  { path: '/tasks', component: () => import('@/views/TaskCenterView.vue') },
  { path: '/point-logs', component: () => import('@/views/PointLogView.vue') },
  { path: '/users', component: () => import('@/views/UserManagementView.vue'), meta: { adminOnly: true } },
  { path: '/invitation', component: () => import('@/views/InvitationCodeView.vue'), meta: { adminOnly: true } },
  { path: '/system-config', component: () => import('@/views/SystemConfigView.vue'), meta: { adminOnly: true } },
  { path: '/audit', component: () => import('@/views/AuditLogView.vue'), meta: { adminOnly: true } },
  { path: '/403', component: () => import('@/views/ForbiddenView.vue'), meta: { public: true } },
  // 兜底：未匹配的任意路径回到首页，避免空白页
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta?.public) return next()
  if (!userStore.isLogged) return next('/login')
  if (to.meta?.adminOnly && !userStore.isAdmin) return next('/403')
  next()
})

export default router
