<template>
  <div class="page-card">
    <h2 class="page-title">📝 教案生成</h2>
    <p class="page-subtitle">支持单章节、按周次范围批量、按文件配置三种模式；后台异步执行，可切换页面等待</p>

    <el-alert type="info" :closable="false" style="margin-bottom:16px">
      📦 文件上传上限：单文件 ≤ 200MB，单次提交总大小 ≤ 500MB。建议每个章节用 1 个 PPT/PDF。
    </el-alert>

    <el-alert v-if="activeCourse" type="success" :closable="false" show-icon style="margin-bottom:16px">
      <template #title>
        <span style="font-weight:600">已应用课程配置：</span>
        {{ activeCourse.courseName }}
        <el-tag size="small" type="info" style="margin:0 6px">{{ activeCourse.educationLevel }}</el-tag>
        <el-tag v-if="activeCourse.teachingMode" size="small" style="margin-right:6px">{{ activeCourse.teachingMode }}</el-tag>
        <el-tag v-if="activeCourse.programmingLanguage" size="small" type="warning">{{ activeCourse.programmingLanguage }}</el-tag>
        <router-link to="/course-config" style="margin-left:8px; font-size:12px">修改</router-link>
      </template>
    </el-alert>
    <el-alert v-else type="warning" :closable="false" show-icon style="margin-bottom:16px">
      <template #title>
        当前未激活任何课程配置，将使用系统默认参数（高职院校 + Java 程序设计）。
        <router-link to="/course-config" style="margin-left:8px; font-size:12px">前往配置</router-link>
      </template>
    </el-alert>

    <el-form :model="form" label-width="110px" size="default">
      <h3 class="section-title">1. 选择生成模式</h3>
      <el-form-item label="模式">
        <el-radio-group v-model="form.mode">
          <el-radio value="single">单章节生成</el-radio>
          <el-radio value="range">按周次范围批量</el-radio>
          <el-radio value="per_file">按文件配置（推荐）</el-radio>
        </el-radio-group>
      </el-form-item>

      <h3 class="section-title">2. 上传素材</h3>
      <el-form-item label="章节 PPT/PDF">
        <el-upload drag multiple :auto-upload="false" :on-change="onPptChange" :on-remove="onPptRemove" :file-list="form.pptFileList" accept=".ppt,.pptx,.pdf">
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">拖拽或点击上传，支持批量 .ppt/.pptx/.pdf（单文件 ≤ 200MB）</div>
          <template #tip><div style="font-size:12px; color:#8c8c8c; margin-top:6px">按文件配置 模式下，每个文件可单独设置周次与教案数</div></template>
        </el-upload>
      </el-form-item>
      <el-form-item v-if="form.mode === 'range'" label="教学计划文档">
        <el-upload :auto-upload="false" :on-change="(f) => form.teachingPlanFile = f.raw" :on-remove="() => form.teachingPlanFile = null" accept=".pdf,.docx,.doc" :limit="1">
          <el-button>选择 .pdf/.docx</el-button>
          <template #tip><span style="font-size:12px; color:#8c8c8c; margin-left:8px">周次范围模式必传</span></template>
        </el-upload>
      </el-form-item>
      <el-form-item label="自定义教案模板">
        <el-upload :auto-upload="false" :on-change="(f) => form.customTemplate = f.raw" :on-remove="() => form.customTemplate = null" accept=".docx" :limit="1">
          <el-button>选择 .docx（可选）</el-button>
        </el-upload>
      </el-form-item>

      <!-- 按文件配置 -->
      <template v-if="form.mode === 'per_file'">
        <h3 class="section-title">3. 每个文件的生成配置</h3>
        <el-empty v-if="!form.pptFiles.length" description="请先在上方上传 PPT/PDF 文件" />
        <div v-else>
          <el-card v-for="(f, i) in form.pptFiles" :key="i" shadow="hover" style="margin-bottom:12px" class="file-config-card">
            <div style="display:flex; align-items:center; gap:8px; margin-bottom:10px">
              <el-tag>文件 {{ i + 1 }}</el-tag>
              <strong>{{ f.name }}</strong>
              <span style="color:#8c8c8c; font-size:12px">{{ humanSize(f.size) }}</span>
            </div>
            <el-row :gutter="16">
              <el-col :span="10">
                <el-form-item label="章节标题"><el-input v-model="getCfg(i).chapter" placeholder="例: 第一章 Java 开发入门" size="small" /></el-form-item>
              </el-col>
              <el-col :span="5">
                <el-form-item label="起始周"><el-input-number v-model="getCfg(i).weekStart" :min="1" :max="20" size="small" controls-position="right" style="width:100%" /></el-form-item>
              </el-col>
              <el-col :span="5">
                <el-form-item label="结束周"><el-input-number v-model="getCfg(i).weekEnd" :min="1" :max="20" size="small" controls-position="right" style="width:100%" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="教案数"><el-input-number v-model="getCfg(i).sessionCount" :min="1" :max="20" size="small" controls-position="right" style="width:100%" /></el-form-item>
              </el-col>
            </el-row>
            <div style="font-size:12px; color:#8c8c8c; margin-top:-8px; padding-left:8px">
              将生成 {{ getCfg(i).sessionCount }} 份教案（每份 80 分钟），均分到第 {{ getCfg(i).weekStart }}-{{ getCfg(i).weekEnd }} 周
            </div>
          </el-card>
          <el-tag type="warning" effect="light" size="large" style="margin-top:6px">📊 全部文件合计：{{ form.pptFiles.length }} 个文件 / {{ totalSessions }} 份教案</el-tag>
        </div>
      </template>

      <!-- 单章节 / 按周次 -->
      <h3 v-if="form.mode !== 'per_file'" class="section-title">3. 教案参数</h3>
      <el-row v-if="form.mode !== 'per_file'" :gutter="16">
        <el-col :span="12" v-if="form.mode === 'single'">
          <el-form-item label="章节标题" required><el-input v-model="form.chapter" placeholder="例: 第一章 Java 开发入门" /></el-form-item>
        </el-col>
        <el-col :span="12" v-if="form.mode === 'single'">
          <el-form-item label="周次" required><el-input v-model="form.weekNo" placeholder="例: 2" /></el-form-item>
        </el-col>
        <el-col :span="12" v-if="form.mode === 'range'">
          <el-form-item label="起始周" required><el-input-number v-model="form.weekStart" :min="1" :max="20" controls-position="right" style="width:100%" /></el-form-item>
        </el-col>
        <el-col :span="12" v-if="form.mode === 'range'">
          <el-form-item label="结束周" required><el-input-number v-model="form.weekEnd" :min="1" :max="20" controls-position="right" style="width:100%" /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="每周学时">
            <el-radio-group v-model="form.hoursPerWeek">
              <el-radio :value="4">4学时</el-radio>
              <el-radio :value="2">2学时</el-radio>
            </el-radio-group>
            <div style="font-size:12px; color:#8c8c8c; line-height:1.4; margin-top:2px">{{ hoursHint }}</div>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="打包方式">
            <el-radio-group v-model="form.packageMode">
              <el-radio value="single">单次</el-radio>
              <el-radio value="weekly">按周</el-radio>
              <el-radio value="full">满载</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <h3 class="section-title">{{ form.mode === 'per_file' ? '4. 全局参数' : '4. 其他参数' }}</h3>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="内容等级">
            <el-select v-model="form.contentLevel" style="width:100%">
              <el-option label="基础版（4段·快速备课）" value="basic" />
              <el-option label="标准版（6段·日常教学）" value="standard" />
              <el-option label="详尽版（8段·分层任务）" value="detailed" />
              <el-option label="特详版（10段·检查提交）" value="comprehensive" />
            </el-select>
            <div style="font-size:12px; color:#8c8c8c; line-height:1.4; margin-top:2px">{{ levelHint }}</div>
          </el-form-item>
        </el-col>
        <el-col :span="8"><el-form-item label="班级"><el-input v-model="form.className" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="任课老师"><el-input v-model="form.teacher" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="使用模型">
          <el-select v-model="form.provider" placeholder="留空则用激活模型" clearable style="width:100%">
            <el-option label="智谱 GLM" value="zhipu" />
            <el-option label="Kimi" value="kimi" />
            <el-option label="通义千问" value="qwen" />
            <el-option label="MiniMax" value="minimax" />
            <el-option label="DeepSeek" value="deepseek" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="8"><el-form-item label="学年"><el-input v-model="form.academicYear" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="学期"><el-input v-model="form.semester" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="编号"><el-input v-model="form.planNo" placeholder="可空" /></el-form-item></el-col>
        <el-col :span="8" v-if="form.mode === 'per_file'">
          <el-form-item label="输出形式">
            <el-radio-group v-model="form.mergeIntoOne">
              <el-radio :value="true">合并为一个 Word</el-radio>
              <el-radio :value="false">单独多个 Word（ZIP）</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <h3 v-if="form.mode === 'range'" class="section-title">5. 手动周-章节映射（可选）</h3>
      <el-form-item v-if="form.mode === 'range'" label="">
        <div style="font-size:12px; color:#8c8c8c; margin-bottom:8px">系统优先从教学计划自动识别。可在此手动覆盖。</div>
        <el-button size="small" @click="addMapping">添加映射</el-button>
        <el-table v-if="form.manualMapping.length" :data="form.manualMapping" stripe style="margin-top:10px" size="small">
          <el-table-column label="周次" width="110"><template #default="{ row }"><el-input-number v-model="row.week" :min="1" :max="20" size="small" /></template></el-table-column>
          <el-table-column label="章节"><template #default="{ row }"><el-input v-model="row.chapter" size="small" /></template></el-table-column>
          <el-table-column label="内容要点"><template #default="{ row }"><el-input v-model="row.topics" size="small" /></template></el-table-column>
          <el-table-column label="" width="60"><template #default="{ $index }"><el-button size="small" type="danger" link @click="removeMapping($index)">删</el-button></template></el-table-column>
        </el-table>
      </el-form-item>

      <el-form-item style="margin-top:20px">
        <div style="display:flex; align-items:center; gap:16px">
          <el-button type="primary" size="large" @click="onSubmit" :loading="submitting" style="width:200px">提交生成任务</el-button>
          <span style="font-size:13px; color:#8c8c8c">后台执行，可切换页面</span>
          <el-tag type="warning" effect="dark" size="large" style="margin-left:auto">
            💰 预计消耗 {{ estimatedCost }} 积分
          </el-tag>
        </div>
      </el-form-item>
    </el-form>

    <el-card v-if="currentTask" shadow="hover" :class="['task-card', currentTask.status]">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px">
        <strong>教案生成 {{ currentTask.taskId.substring(0, 8) }}</strong>
        <el-tag :type="statusType(currentTask.status)">{{ statusName(currentTask.status) }}</el-tag>
      </div>
      <div style="margin-bottom:8px; font-size:13px; color:#606266">{{ currentTask.stageText }}</div>
      <el-progress :percentage="currentTask.progress || 0" :status="currentTask.status === 'failed' ? 'exception' : null" :stroke-width="14" />
      <div v-if="currentTask.errorMsg" style="margin-top:10px; padding:8px; background:#ffebee; color:#c62828; border-radius:6px; font-size:13px">{{ currentTask.errorMsg }}</div>
      <div v-if="currentTask.status === 'success' && currentTask.resultHistoryId" style="margin-top:14px">
        <el-button type="success" @click="onDownloadResult">下载文件</el-button>
        <el-button @click="currentTask = null">关闭</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { generateLessonPlan } from '@/api/lessonPlan'
import { getTask, subscribeTaskSse } from '@/api/task'
import { downloadFile } from '@/api/download'
import { pointRules } from '@/api/auth'
import { getActiveCourseConfig } from '@/api/courseConfig'
import { inject } from 'vue'

const activeCourse = ref(null)
const loadActiveCourse = async () => {
  try {
    const r = await getActiveCourseConfig()
    activeCourse.value = r.data || null
  } catch (e) { activeCourse.value = null }
}

const form = reactive({
  mode: 'per_file', pptFileList: [], pptFiles: [], teachingPlanFile: null, customTemplate: null,
  chapter: '', weekNo: '', weekStart: 2, weekEnd: 3, hoursPerWeek: 4, packageMode: 'weekly',
  className: '2024级大数据技术01-05班', teacher: '', academicYear: '2025-2026', semester: '1',
  planNo: '', contentLevel: 'standard', provider: '', manualMapping: [],
  fileConfigs: [], mergeIntoOne: true
})

const submitting = ref(false)
const currentTask = ref(null)
let pollTimer = null
const rules = ref({ lessonPlanBaseCost: 10, lessonPlanRangeCost: 5 })
const refreshUserInfo = inject('refreshUserInfo', null)

const loadRules = async () => {
  try {
    const r = await pointRules()
    if (r.data) rules.value = r.data
  } catch (e) {}
}
onMounted(() => { loadRules(); loadActiveCourse() })

const totalSessions = computed(() => {
  return form.fileConfigs.reduce((s, c) => s + (Number(c.sessionCount) || 0), 0)
})

const estimatedCost = computed(() => {
  if (form.mode === 'per_file') {
    const t = totalSessions.value
    return rules.value.lessonPlanBaseCost + Math.max(0, t - 1) * rules.value.lessonPlanRangeCost
  }
  if (form.mode === 'range' && form.weekStart && form.weekEnd && form.weekEnd >= form.weekStart) {
    const weeks = form.weekEnd - form.weekStart + 1
    return rules.value.lessonPlanBaseCost + Math.max(0, weeks - 1) * rules.value.lessonPlanRangeCost
  }
  return rules.value.lessonPlanBaseCost
})

const hoursHint = computed(() => {
  if (form.packageMode === 'single') return '仅生成 1 次教案，每周学时不影响输出'
  if (form.packageMode === 'full') return '满载 = 每周 3 次教案，每周学时不影响输出'
  return form.hoursPerWeek === 4 ? '4学时 / 周 → 每周生成 2 次教案' : '2学时 / 周 → 每周生成 1 次教案'
})

const levelHint = computed(() => ({
  basic: '简明备课，4 段教学过程，源材料 4000 字',
  standard: '日常教学，6 段教学过程，源材料 8000 字',
  detailed: '分层指导 + 分层任务，8 段教学过程，源材料 12000 字',
  comprehensive: '检查提交标准，含分层任务+评价量规+评价细则，10 段教学过程，源材料 16000 字'
}[form.contentLevel] || ''))

const stripExt = (n) => n && n.includes('.') ? n.substring(0, n.lastIndexOf('.')) : n
const humanSize = (b) => {
  if (!b) return ''
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(1) + ' MB'
}

const ensureFileConfigs = () => {
  // 确保 fileConfigs 与 pptFiles 长度对齐
  while (form.fileConfigs.length < form.pptFiles.length) {
    const idx = form.fileConfigs.length
    const f = form.pptFiles[idx]
    form.fileConfigs.push({
      fileIndex: idx,
      chapter: f ? stripExt(f.name) : '',
      weekStart: 1 + idx,
      weekEnd: 1 + idx,
      sessionCount: 2
    })
  }
  while (form.fileConfigs.length > form.pptFiles.length) {
    form.fileConfigs.pop()
  }
  // 同步 fileIndex
  form.fileConfigs.forEach((c, i) => { c.fileIndex = i })
}

const getCfg = (i) => form.fileConfigs[i]

watch(() => form.pptFiles.length, ensureFileConfigs)

const onPptChange = (file, fileList) => {
  form.pptFiles = fileList.map(f => f.raw).filter(Boolean)
  form.pptFileList = fileList
  ensureFileConfigs()
}
const onPptRemove = (file, fileList) => {
  form.pptFiles = fileList.map(f => f.raw).filter(Boolean)
  form.pptFileList = fileList
  ensureFileConfigs()
}
const addMapping = () => form.manualMapping.push({ week: form.weekStart, chapter: '', topics: '' })
const removeMapping = (i) => form.manualMapping.splice(i, 1)

const statusName = (s) => ({ pending: '已提交', running: '生成中', success: '已完成', failed: '失败' }[s] || s)
const statusType = (s) => ({ pending: 'info', running: '', success: 'success', failed: 'danger' }[s] || '')

const validateBeforeSubmit = async () => {
  if (form.mode === 'single') {
    if (!form.chapter) return (ElMessage.warning('请填写章节标题'), false)
    if (!form.weekNo) return (ElMessage.warning('请填写周次'), false)
  } else if (form.mode === 'range') {
    if (!form.weekStart || !form.weekEnd) return (ElMessage.warning('请填写起止周次'), false)
    if (form.weekEnd < form.weekStart) return (ElMessage.warning('结束周不能小于起始周'), false)
    if (!form.teachingPlanFile && !form.manualMapping.length) {
      const c = await import('element-plus').then(m => m.ElMessageBox.confirm('未上传教学计划且未填写映射，将以默认章节名生成。是否继续？', '提示', { type: 'warning' })).catch(() => false)
      if (!c) return false
    }
  } else if (form.mode === 'per_file') {
    if (!form.pptFiles.length) return (ElMessage.warning('请上传至少 1 个 PPT/PDF 文件'), false)
    for (let i = 0; i < form.fileConfigs.length; i++) {
      const c = form.fileConfigs[i]
      if (!c.chapter) return (ElMessage.warning(`请填写文件 ${i + 1} 的章节标题`), false)
      if (!c.weekStart || !c.weekEnd) return (ElMessage.warning(`请填写文件 ${i + 1} 的起止周次`), false)
      if (c.weekEnd < c.weekStart) return (ElMessage.warning(`文件 ${i + 1} 的结束周不能小于起始周`), false)
      if (!c.sessionCount || c.sessionCount < 1) return (ElMessage.warning(`文件 ${i + 1} 的教案数至少 1 份`), false)
    }
    if (totalSessions.value > 60) return (ElMessage.warning('总教案数不能超过 60 份'), false)
  }
  return true
}

const onSubmit = async () => {
  if (!(await validateBeforeSubmit())) return

  const fd = new FormData()
  for (const f of form.pptFiles) fd.append('pptFiles', f)
  if (form.teachingPlanFile) fd.append('teachingPlanFile', form.teachingPlanFile)
  if (form.customTemplate) fd.append('customTemplate', form.customTemplate)

  const payload = {
    mode: form.mode, chapter: form.chapter, weekNo: form.weekNo, weekStart: form.weekStart, weekEnd: form.weekEnd,
    hoursPerWeek: form.hoursPerWeek, packageMode: form.packageMode, className: form.className, teacher: form.teacher,
    academicYear: form.academicYear, semester: form.semester, planNo: form.planNo, contentLevel: form.contentLevel,
    provider: form.provider, manualMapping: form.manualMapping.filter(m => m.week && m.chapter),
    fileConfigs: form.mode === 'per_file' ? form.fileConfigs : [],
    mergeIntoOne: form.mergeIntoOne
  }
  fd.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))

  submitting.value = true
  try {
    const r = await generateLessonPlan(fd)
    currentTask.value = r.data
    // 通知 TaskFloatButton 立即刷新，避免无任务档 8s 间隔导致用户看到任务列表空窗
    window.dispatchEvent(new CustomEvent('task-submitted', { detail: { taskId: r.data?.taskId } }))
    ElMessage({
      type: 'success',
      message: '任务已提交，可在「任务中心」查看进度（页面切换不影响任务执行）',
      duration: 4000,
      showClose: true
    })
    startPolling(currentTask.value.taskId)
    if (refreshUserInfo) refreshUserInfo()
  } finally { submitting.value = false }
}

const startPolling = (taskId) => {
  if (pollTimer) clearInterval(pollTimer)
  // 优先使用 SSE 实时推送；失败时自动回退到轮询
  let sseHandle = null
  let fellBackToPolling = false
  const startPollingFallback = () => {
    if (fellBackToPolling) return
    fellBackToPolling = true
    pollTimer = setInterval(async () => {
      try {
        const r = await getTask(taskId)
        currentTask.value = r.data
        if (r.data.status === 'success' || r.data.status === 'failed') {
          clearInterval(pollTimer); pollTimer = null
          if (refreshUserInfo) refreshUserInfo()
        }
      } catch (e) { clearInterval(pollTimer); pollTimer = null }
    }, 1500)
  }
  try {
    sseHandle = subscribeTaskSse(taskId, {
      onProgress: (p) => {
        if (!p) return
        // 增量更新 progress / stage
        if (currentTask.value) {
          if (p.progress !== undefined) currentTask.value.progress = p.progress
          if (p.stage !== undefined) currentTask.value.stageText = p.stage
        }
      },
      onDone: (p) => {
        // 立即更新 status / progress 避免 100% running 残留
        if (p && currentTask.value) {
          if (p.progress !== undefined) currentTask.value.progress = p.progress
          if (p.stage !== undefined) currentTask.value.stageText = p.stage
          if (p.status) currentTask.value.status = p.status
          if (p.historyId) currentTask.value.resultHistoryId = p.historyId
        }
        // 异步拉完整 task 兜底（取错误信息等）
        if (p && p.status) {
          getTask(taskId).then(r => { currentTask.value = r.data }).catch(() => {})
        }
        if (refreshUserInfo) refreshUserInfo()
      },
      onError: () => {
        // SSE 异常：回退到轮询保证不丢任务状态
        if (sseHandle) sseHandle.close()
        startPollingFallback()
      }
    })
    // 把 sseHandle 挂到 pollTimer 占位，便于 onUnmounted 清理
    pollTimer = { __sse: true, close: () => sseHandle?.close() }
  } catch (e) {
    startPollingFallback()
  }
}

const onDownloadResult = () => { if (currentTask.value?.resultHistoryId) downloadFile(`/api/lesson-plan/download/${currentTask.value.resultHistoryId}`) }
onUnmounted(() => {
  if (pollTimer) {
    if (pollTimer.__sse) pollTimer.close()
    else clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.file-config-card :deep(.el-form-item) { margin-bottom: 8px; }
.file-config-card :deep(.el-form-item__label) { font-size: 12px; padding-right: 6px; }
</style>
