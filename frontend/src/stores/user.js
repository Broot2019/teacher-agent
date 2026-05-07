import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// 容错地解析 localStorage 内容；损坏时清掉避免整个 store 创建失败
const safeReadUser = () => {
  const raw = localStorage.getItem('user')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    console.warn('[user store] localStorage user 损坏，已清理', e)
    localStorage.removeItem('user')
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref(safeReadUser())

  const isLogged = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'admin')

  function setLogin(payload) {
    token.value = payload.token
    user.value = {
      id: payload.userId,
      username: payload.username,
      role: payload.role,
      realName: payload.realName
    }
    localStorage.setItem('token', token.value)
    localStorage.setItem('user', JSON.stringify(user.value))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, isLogged, isAdmin, setLogin, logout }
})
