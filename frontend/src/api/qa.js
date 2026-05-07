import http from './http'

export function askQuestion(question) {
  return http.post('/qa/ask', { question })
}
