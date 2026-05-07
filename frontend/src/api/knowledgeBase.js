import http from './http'

export function listKnowledgeBases() {
  return http.get('/knowledge-base/list')
}

export function uploadKnowledgeBase(title, file) {
  const form = new FormData()
  form.append('title', title)
  form.append('file', file)
  return http.post('/knowledge-base/upload', form)
}

export function deleteKnowledgeBase(id) {
  return http.delete('/knowledge-base/' + id)
}
