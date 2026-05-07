import http from './http'

export function listCourseConfigs() {
  return http.get('/course-config/list')
}

export function getActiveCourseConfig() {
  return http.get('/course-config/active')
}

export function saveCourseConfig(data) {
  return http.post('/course-config/save', data)
}

export function activateCourseConfig(id) {
  return http.post('/course-config/activate/' + id)
}

export function deleteCourseConfig(id) {
  return http.delete('/course-config/' + id)
}
