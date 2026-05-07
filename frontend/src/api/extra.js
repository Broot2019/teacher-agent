import http from './http'

export const dashboardStats = () => http.get('/dashboard/stats')

// 预览
export const previewLesson = (id) => http.get(`/lesson-plan/preview/${id}`)
// 重跑
export const retryLesson = (taskId) => http.post(`/lesson-plan/retry/${taskId}`)
export const retryQuestion = (taskId) => http.post(`/question-bank/retry/${taskId}`)

// 历史删除
export const deleteLessonHistory = (id) => http.delete(`/lesson-plan/${id}`)
export const deleteQuestionHistory = (id) => http.delete(`/question-bank/${id}`)

// 题目管理
export const listQuestions = (bankId) => http.get('/question-item/list', { params: { bankId } })
export const saveQuestion = (data) => http.post('/question-item/save', data)
export const deleteQuestion = (id) => http.delete(`/question-item/${id}`)
export const regenerateBank = (bankId) => http.post(`/question-item/regenerate/${bankId}`)

// 章节资料库
export const materialList = (chapter, course) => http.get('/material/list', { params: { chapter, course } })
export const materialUpload = (formData) => http.post('/material/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const materialDelete = (id) => http.delete(`/material/${id}`)
export const materialDownloadUrl = (id) => `/api/material/download/${id}`

// 审计日志
export const auditList = (params) => http.get('/audit/list', { params })
