<template>
  <div class="page-card">
    <h2 class="page-title">⏱️ 任务中心</h2>
    <p class="page-subtitle">查看所有生成任务进度，任务后台执行不受页面切换影响</p>

    <el-tabs v-model="active">
      <el-tab-pane label="🔄 进行中" name="running">
        <el-empty v-if="!running.length" description="暂无运行中的任务" />
        <el-card v-for="t in running" :key="t.id" shadow="hover" style="margin-bottom:12px">
          <div style="display:flex; align-items:center; gap:12px">
            <el-tag :type="t.type === 'lesson_plan' ? 'success' : 'warning'" size="small">
              {{ t.type === 'lesson_plan' ? '📝 教案' : '❓ 题库' }}
            </el-tag>
            <strong>{{ t.taskId.substring(0,8) }}</strong>
            <span style="color:#909399; font-size:13px">{{ t.stageText }}</span>
            <span style="margin-left:auto; font-size:13px">{{ t.createTime }}</span>
          </div>
          <el-progress :percentage="t.progress || 0" :status="t.status === 'failed' ? 'exception' : null" style="margin-top:10px" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="📋 全部任务" name="all">
        <el-table :data="all" stripe v-loading="loading">
          <el-table-column prop="taskId" label="任务ID" width="120">
            <template #default="{ row }">
              <code style="font-size:11px">{{ row.taskId.substring(0,8) }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="row.type === 'lesson_plan' ? 'success' : 'warning'" size="small">
                {{ row.type === 'lesson_plan' ? '教案' : '题库' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="progress" label="进度" width="220">
            <template #default="{ row }">
              <el-progress :percentage="row.progress || 0" :status="row.status === 'failed' ? 'exception' : null" />
            </template>
          </el-table-column>
          <el-table-column prop="stageText" label="阶段" min-width="180" show-overflow-tooltip />
          <el-table-column prop="errorMsg" label="错误" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createTime" label="创建时间" width="160" />
          <el-table-column prop="finishTime" label="完成时间" width="160" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="row.status === 'success' && row.resultHistoryId"
                         size="small" type="primary" plain @click="onDownload(row)">
                ⬇️ 下载
              </el-button>
              <el-button v-if="row.status === 'failed'"
                         size="small" type="warning" plain @click="onRetry(row)">
                🔄 重跑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { taskList, runningTasks } from '@/api/task'
import { retryLesson, retryQuestion } from '@/api/extra'
import { downloadFile } from '@/api/download'

const active = ref('running')
const running = ref([])
const all = ref([])
const loading = ref(false)
let timer = null

const statusName = (s) => ({ pending: '待开始', running: '进行中', success: '成功', failed: '失败', cancelled: '已取消' }[s] || s)
const statusType = (s) => ({ pending: 'info', running: '', success: 'success', failed: 'danger', cancelled: 'warning' }[s] || '')

const refresh = async () => {
  try {
    const [r1, r2] = await Promise.all([runningTasks(), taskList(100)])
    running.value = r1.data || []
    all.value = r2.data || []
  } catch (e) {}
}

const refreshLoading = async () => {
  loading.value = true
  try { await refresh() } finally { loading.value = false }
}

const onDownload = (row) => {
  const path = row.type === 'lesson_plan' ? '/api/lesson-plan/download/' : '/api/question-bank/download/'
  downloadFile(path + row.resultHistoryId)
}

const onRetry = async (row) => {
  try {
    if (row.type === 'lesson_plan') await retryLesson(row.taskId)
    else await retryQuestion(row.taskId)
    ElMessage.success('已重新提交，请稍等查看新任务')
    refresh()
  } catch (e) {}
}

onMounted(() => {
  refreshLoading()
  timer = setInterval(refresh, 2500)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
