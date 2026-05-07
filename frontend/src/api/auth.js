import http from './http'
export const login = (data) => http.post('/auth/login', data)
export const register = (data) => http.post('/auth/register', data)
export const me = () => http.get('/auth/me')
export const logout = () => http.post('/auth/logout')
export const changePassword = (data) => http.post('/auth/change-password', data)
export const pointRules = () => http.get('/auth/point-rules')
export const myPointLogs = (limit = 50) => http.get('/auth/point-logs', { params: { limit } })

// 管理员
export const userList = () => http.get('/user/list')
export const userSave = (data) => http.post('/user/save', data)
export const userToggleStatus = (id) => http.post(`/user/toggle-status/${id}`)
export const userResetPassword = (id) => http.post(`/user/reset-password/${id}`)
export const userDelete = (id) => http.delete(`/user/${id}`)
