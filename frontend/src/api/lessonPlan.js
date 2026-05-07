import http from './http'

export const generateLessonPlan = (formData) => http.post('/lesson-plan/generate', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const lessonPlanHistory = (limit = 50) => http.get('/lesson-plan/history', { params: { limit } })

// 下载链接需要带 token，使用 fetch 主动下载或后端开放跳过鉴权
export const lessonPlanDownloadUrl = (id) => `/api/lesson-plan/download/${id}`
