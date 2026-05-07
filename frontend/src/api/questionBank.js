import http from './http'

export const generateQuestionBank = (formData) => http.post('/question-bank/generate', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const questionBankHistory = (limit = 50) => http.get('/question-bank/history', { params: { limit } })

export const questionBankDownloadUrl = (id) => `/api/question-bank/download/${id}`
