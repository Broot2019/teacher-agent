import http from './http'

// 邀请码管理
export const invitationCodeCreate = (data) => http.post('/invitation-code/create', data)
export const invitationCodeBatchCreate = (data) => http.post('/invitation-code/batch-create', data)
export const invitationCodeList = (limit = 200) => http.get('/invitation-code/list', { params: { limit } })
export const invitationCodeDisable = (id) => http.post(`/invitation-code/disable/${id}`)

// 系统配置
export const systemConfigList = () => http.get('/system/config')
export const systemConfigSave = (data) => http.post('/system/config', data)

// 积分管理
export const grantPoints = (data) => http.post('/system/point/grant', data)
export const pointLogs = (params) => http.get('/system/point/logs', { params })
