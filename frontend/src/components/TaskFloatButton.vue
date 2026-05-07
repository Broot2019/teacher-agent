<template>
  <div class="task-float">
    <el-badge :value="runningCount" :hidden="!runningCount" type="primary">
      <el-button circle size="large" type="primary" @click="drawerVisible = true" :icon="Loading" />
    </el-badge>
    <el-drawer v-model="drawerVisible" title="🔄 进行中的任务" size="420px">
      <el-empty v-if="!running.length" description="暂无运行中的任务" />
      <div v-for="t in running" :key="t.id" class="task-item">
        <div style="display:flex; align-items:center; gap:8px">
          <el-tag :type="t.type === 'lesson_plan' ? 'success' : 'warning'" size="small">
            {{ t.type === 'lesson_plan' ? '教案' : '题库' }}
          </el-tag>
          <span style="font-size:12px; color:#909399">{{ t.taskId.substring(0,8) }}</span>
        </div>
        <div style="margin:6px 0; font-size:13px">{{ t.stageText }}</div>
        <el-progress :percentage="t.progress || 0" :status="t.status === 'failed' ? 'exception' : null" />
      </div>
      <div style="margin-top:16px; text-align:center">
        <router-link to="/tasks">
          <el-button type="primary" plain size="small" @click="drawerVisible = false">查看全部任务</el-button>
        </router-link>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { runningTasks } from '@/api/task'

const running = ref([])
const drawerVisible = ref(false)

// 三档轮询策略（节省 SQL 查询）：
//   visible + 有运行任务: 3000ms
//   visible + 无任务:     8000ms（之前 15s 太慢，新提交任务可能要等到下一次刷新才看到）
//   document hidden:      停止
// 同时监听全局 task-submitted 事件，提交任务后立即刷新一次（无需等下次 tick）
const INTERVAL_BUSY = 3000
const INTERVAL_IDLE = 8000

let timer = null

const runningCount = computed(() => running.value.filter(t => t.status === 'running' || t.status === 'pending').length)

const refresh = async () => {
  try {
    const r = await runningTasks()
    running.value = r.data || []
  } catch (e) { /* 静默失败，避免顶栏弹错误 */ }
}

const computeInterval = () => {
  if (typeof document !== 'undefined' && document.hidden) return null
  return runningCount.value > 0 ? INTERVAL_BUSY : INTERVAL_IDLE
}

const reschedule = () => {
  if (timer) { clearTimeout(timer); timer = null }
  const next = computeInterval()
  if (next == null) return
  timer = setTimeout(async () => {
    await refresh()
    reschedule()
  }, next)
}

const onVisibilityChange = () => {
  if (document.hidden) {
    if (timer) { clearTimeout(timer); timer = null }
  } else {
    refresh().then(reschedule)
  }
}

const onTaskSubmitted = () => {
  // 立即刷新一次拿到新任务，并重置定时器（此时 runningCount > 0 会切到 BUSY 档）
  refresh().then(reschedule)
}

onMounted(() => {
  refresh().then(reschedule)
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('task-submitted', onTaskSubmitted)
})

onUnmounted(() => {
  if (timer) { clearTimeout(timer); timer = null }
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('task-submitted', onTaskSubmitted)
})
</script>

<style scoped>
.task-float {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 999;
}
.task-item {
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 10px;
}
</style>
