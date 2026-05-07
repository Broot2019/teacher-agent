import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const http = axios.create({
  baseURL: '/api',
  timeout: 600000
})

// toast 节流：1 秒内同一文案只弹一次，避免并发请求失败时叠加
const recentToasts = new Map()
const TOAST_THROTTLE_MS = 1000

const toastError = (msg) => {
  if (!msg) return
  const now = Date.now()
  const last = recentToasts.get(msg) || 0
  if (now - last < TOAST_THROTTLE_MS) return
  recentToasts.set(msg, now)
  ElMessage.error(msg)
  // 简单清理：避免 Map 无限增长
  if (recentToasts.size > 50) {
    const oldest = [...recentToasts.entries()].sort((a, b) => a[1] - b[1])[0][0]
    recentToasts.delete(oldest)
  }
}

const goLogin = async () => {
  try {
    const userStore = useUserStore()
    userStore.logout()
  } catch (e) { /* pinia 未初始化时忽略 */ }
  // 通过动态 import 拿 router 实例，避免循环依赖
  try {
    const router = (await import('@/router')).default
    if (router && router.currentRoute.value.path !== '/login') {
      router.push('/login')
    }
  } catch (e) {
    // 极端兜底：路由实例不可用时回退到 hash
    if (location.hash !== '#/login') location.hash = '#/login'
  }
}

// 请求拦截器：自动加 Authorization
http.interceptors.request.use((config) => {
  try {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
  } catch (e) { /* pinia 未初始化时忽略 */ }
  return config
})

// 响应拦截器：401 自动登出跳登录；业务码 >= 400 弹 toast
http.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data && data.code !== undefined) {
      if (data.code === 401) {
        goLogin()
        ElMessage.warning('登录已过期，请重新登录')
        return Promise.reject(new Error(data.message || '未登录'))
      }
      // 仅 4xx/5xx 视为错误（兼容自定义 200/201/202/302 等）
      if (data.code >= 400) {
        toastError(data.message || '请求失败')
        return Promise.reject(new Error(data.message || '请求失败'))
      }
    }
    return data
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message || '网络错误'
    if (status === 401) {
      goLogin()
      ElMessage.warning('登录已过期，请重新登录')
    } else {
      toastError(msg)
    }
    return Promise.reject(err)
  }
)

export default http
