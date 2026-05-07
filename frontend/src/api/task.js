import http from './http'
import { useUserStore } from '@/stores/user'

export const getTask = (taskId) => http.get(`/task/${taskId}`)
export const taskList = (limit = 50) => http.get('/task/list', { params: { limit } })
export const runningTasks = () => http.get('/task/running')

/**
 * SSE 订阅任务进度（替代轮询）。
 *
 * 用法：
 *   const handle = subscribeTaskSse(taskId, {
 *     onProgress: (payload) => { ... },
 *     onDone:     (payload) => { ... },  // status: 'success' | 'failed'
 *     onError:    (err) => { ... }
 *   })
 *   // 取消订阅
 *   handle.close()
 *
 * 与原生 EventSource 不同：
 * - 自动从 user store 取 token 拼到查询参数（EventSource 不能设自定义 header）
 * - 自动处理 init / progress / done 三种事件
 * - 'done' 事件触发后自动 close（避免悬挂连接）
 *
 * @param {string} taskId
 * @param {{onProgress?: Function, onDone?: Function, onError?: Function, onInit?: Function}} callbacks
 * @returns {{close: Function, source: EventSource}}
 */
export function subscribeTaskSse(taskId, callbacks = {}) {
  const userStore = useUserStore()
  const token = userStore?.token || ''
  // baseURL 在 http.js 是 '/api'；SSE 路径直接拼绝对路径避免 EventSource 被 axios 拦截
  const url = `/api/task/sse/${taskId}` + (token ? `?token=${encodeURIComponent(token)}` : '')
  const es = new EventSource(url)
  let closed = false

  const safeClose = () => {
    if (closed) return
    closed = true
    try { es.close() } catch (e) { /* ignore */ }
  }

  es.addEventListener('init', (e) => {
    if (callbacks.onInit) callbacks.onInit(e.data)
  })
  es.addEventListener('progress', (e) => {
    if (closed) return
    let payload = null
    try { payload = JSON.parse(e.data) } catch { /* ignore */ }
    if (callbacks.onProgress) callbacks.onProgress(payload)
  })
  es.addEventListener('done', (e) => {
    let payload = null
    try { payload = JSON.parse(e.data) } catch { /* ignore */ }
    if (callbacks.onDone) callbacks.onDone(payload)
    safeClose()
  })
  es.onerror = (err) => {
    if (callbacks.onError) callbacks.onError(err)
    // EventSource 默认会自动重连；如果是 401/404 等持续错误，5 秒后强制关闭避免风暴
    setTimeout(() => {
      if (es.readyState === EventSource.CLOSED) safeClose()
    }, 5000)
  }

  return {
    close: safeClose,
    source: es,
  }
}
