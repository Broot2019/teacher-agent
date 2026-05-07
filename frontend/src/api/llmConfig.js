import http from './http'

export const listLlm = () => http.get('/llm-config/list')
export const getActiveLlm = () => http.get('/llm-config/active')
export const saveLlm = (data) => http.post('/llm-config/save', data)
export const testLlm = (provider) => http.post(`/llm-config/test/${provider}`)
export const activateLlm = (provider) => http.post(`/llm-config/activate/${provider}`)
