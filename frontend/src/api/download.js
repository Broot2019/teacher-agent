import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

/**
 * 通过 fetch + blob 下载文件，自动携带 token
 */
export async function downloadFile(url, fallbackName = 'download') {
  try {
    const userStore = useUserStore()
    const resp = await fetch(url, {
      headers: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {}
    })
    if (!resp.ok) {
      const text = await resp.text()
      ElMessage.error('下载失败: ' + text.substring(0, 100))
      return
    }
    // 从 Content-Disposition 解析文件名
    const cd = resp.headers.get('Content-Disposition') || ''
    let name = fallbackName
    const m = cd.match(/filename\*=UTF-8''([^;]+)/)
    if (m) name = decodeURIComponent(m[1])
    else {
      const m2 = cd.match(/filename="?([^";]+)"?/)
      if (m2) name = m2[1]
    }
    const blob = await resp.blob()
    const a = document.createElement('a')
    const objectUrl = URL.createObjectURL(blob)
    a.href = objectUrl
    a.download = name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(objectUrl)
  } catch (e) {
    ElMessage.error('下载失败: ' + e.message)
  }
}
