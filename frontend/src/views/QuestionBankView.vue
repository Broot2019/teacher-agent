<template>
  <div class="page-card">
    <h2 class="page-title">❓ 题库生成</h2>
    <p class="page-subtitle">支持单章节合并出题或按文件单独配置；后台异步执行，可切换页面等待</p>

    <el-alert type="info" :closable="false" style="margin-bottom:16px">
      📦 文件上传上限：单文件 ≤ 200MB，单次提交总大小 ≤ 500MB
    </el-alert>

    <el-alert v-if="activeCourse" type="success" :closable="false" show-icon style="margin-bottom:16px">
      <template #title>
        <span style="font-weight:600">已应用课程配置：</span>
        {{ activeCourse.courseName }}
        <el-tag size="small" type="info" style="margin:0 6px">{{ activeCourse.educationLevel }}</el-tag>
        <el-tag v-if="activeCourse.programmingLanguage" size="small" type="warning">编程题语言：{{ activeCourse.programmingLanguage }}</el-tag>
        <span v-else style="color:#e6a23c; font-size:12px; margin-left:6px">⚠ 课程未指定编程语言，编程题将按下方"编程题语言"或自动检测</span>
        <router-link to="/course-config" style="margin-left:8px; font-size:12px">修改</router-link>
      </template>
    </el-alert>
    <el-alert v-else type="warning" :closable="false" show-icon style="margin-bottom:16px">
      <template #title>
        当前未激活任何课程配置，编程题将按下方"编程题语言"或从素材自动检测，回退默认 Java。
        <router-link to="/course-config" style="margin-left:8px; font-size:12px">前往配置</router-link>
      </template>
    </el-alert>

    <el-form :model="form" label-width="110px" size="default">
      <h3 class="section-title">1. 选择生成模式</h3>
      <el-form-item label="模式">
        <el-radio-group v-model="form.mode">
          <el-radio value="single">单章节出题（合并所有素材）</el-radio>
          <el-radio value="per_file">按文件配置（推荐）</el-radio>
        </el-radio-group>
      </el-form-item>

      <h3 class="section-title">2. 上传章节素材</h3>
      <el-form-item label="章节 PPT/PDF" required>
        <el-upload drag multiple :auto-upload="false" :on-change="onPptChange" :on-remove="onPptRemove" :file-list="form.pptFileList" accept=".ppt,.pptx,.pdf">
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">拖拽或点击上传，支持批量多文件（单文件 ≤ 200MB）</div>
        </el-upload>
      </el-form-item>
      <el-form-item label="自定义题库模板">
        <el-upload :auto-upload="false" :on-change="(f) => form.customTemplate = f.raw" :on-remove="() => form.customTemplate = null" accept=".xlsx" :limit="1">
          <el-button>选择 .xlsx（可选）</el-button>
        </el-upload>
      </el-form-item>

      <!-- per_file 配置 -->
      <template v-if="form.mode === 'per_file'">
        <h3 class="section-title">3. 每个文件的题型配置</h3>
        <el-empty v-if="!form.pptFiles.length" description="请先在上方上传 PPT/PDF 文件" />
        <div v-else>
          <el-card v-for="(f, i) in form.pptFiles" :key="i" shadow="hover" style="margin-bottom:12px" class="file-config-card">
            <div style="display:flex; align-items:center; gap:8px; margin-bottom:10px">
              <el-tag>文件 {{ i + 1 }}</el-tag>
              <strong>{{ f.name }}</strong>
              <span style="color:#8c8c8c; font-size:12px">{{ humanSize(f.size) }}</span>
            </div>
            <el-form-item label="章节标题"><el-input v-model="getCfg(i).chapter" placeholder="例: 第一章 Java 开发入门" size="small" /></el-form-item>
            <el-row :gutter="12">
              <el-col :span="6">
                <el-form-item label="单选" label-width="60px">
                  <el-input-number v-model="getCfg(i).typeCount.single" :min="0" :max="50" size="small" controls-position="right" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="多选" label-width="60px">
                  <el-input-number v-model="getCfg(i).typeCount.multi" :min="0" :max="50" size="small" controls-position="right" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="判断" label-width="60px">
                  <el-input-number v-model="getCfg(i).typeCount.judge" :min="0" :max="50" size="small" controls-position="right" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="编程" label-width="60px">
                  <el-input-number v-model="getCfg(i).typeCount.program" :min="0" :max="20" size="small" controls-position="right" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <div style="font-size:12px; color:#8c8c8c; padding-left:8px">
              小计：{{ fileSubtotal(i) }} 道
            </div>
          </el-card>
          <el-tag type="warning" effect="light" size="large">📊 合计：{{ form.pptFiles.length }} 个文件 / {{ totalQuestions }} 道题目</el-tag>
        </div>
      </template>

      <!-- single 配置 -->
      <template v-if="form.mode === 'single'">
        <h3 class="section-title">3. 题目参数</h3>
        <el-form-item label="章节标题" required><el-input v-model="form.chapter" placeholder="例: 第一章 Java 开发入门" /></el-form-item>
        <el-form-item label="题型与数量">
          <el-row :gutter="16" style="width:100%">
            <el-col :span="6">
              <el-input-number v-model="form.singleCount" :min="0" :max="50" controls-position="right" style="width:100%" />
              <div style="text-align:center; font-size:13px; color:#8c8c8c; margin-top:4px">单选题</div>
            </el-col>
            <el-col :span="6">
              <el-input-number v-model="form.multiCount" :min="0" :max="50" controls-position="right" style="width:100%" />
              <div style="text-align:center; font-size:13px; color:#8c8c8c; margin-top:4px">多选题</div>
            </el-col>
            <el-col :span="6">
              <el-input-number v-model="form.judgeCount" :min="0" :max="50" controls-position="right" style="width:100%" />
              <div style="text-align:center; font-size:13px; color:#8c8c8c; margin-top:4px">判断题</div>
            </el-col>
            <el-col :span="6">
              <el-input-number v-model="form.programCount" :min="0" :max="20" controls-position="right" style="width:100%" />
              <div style="text-align:center; font-size:13px; color:#8c8c8c; margin-top:4px">编程题</div>
            </el-col>
          </el-row>
        </el-form-item>
      </template>

      <h3 class="section-title">{{ form.mode === 'per_file' ? '4. 全局参数' : '4. 其他参数' }}</h3>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="难度倾向">
            <el-radio-group v-model="form.difficulty">
              <el-radio value="简单">简单</el-radio>
              <el-radio value="一般">一般</el-radio>
              <el-radio value="困难">困难</el-radio>
              <el-radio value="混合">混合</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="内容等级">
            <el-select v-model="form.contentLevel" style="width:100%">
              <el-option label="基础版（短题短解析）" value="basic" />
              <el-option label="标准版（日常作业）" value="standard" />
              <el-option label="详尽版（含易错点）" value="detailed" />
              <el-option label="特详版（综合训练）" value="comprehensive" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="使用模型">
            <el-select v-model="form.provider" placeholder="留空则用激活模型" clearable style="width:100%">
              <el-option label="智谱 GLM" value="zhipu" />
              <el-option label="Kimi" value="kimi" />
              <el-option label="通义千问" value="qwen" />
              <el-option label="MiniMax" value="minimax" />
              <el-option label="DeepSeek" value="deepseek" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16" v-if="hasProgramQuestions">
        <el-col :span="24">
          <el-form-item label="编程题语言">
            <el-select v-model="form.programmingLanguage" placeholder="留空时自动按课程配置/素材检测/默认 Java" clearable style="max-width:280px">
              <el-option label="Java" value="java" />
              <el-option label="Python" value="python" />
              <el-option label="C" value="c" />
              <el-option label="C++" value="cpp" />
              <el-option label="C#" value="csharp" />
              <el-option label="Go" value="go" />
              <el-option label="JavaScript" value="javascript" />
              <el-option label="TypeScript" value="typescript" />
              <el-option label="PHP" value="php" />
              <el-option label="Kotlin" value="kotlin" />
              <el-option label="Swift" value="swift" />
              <el-option label="Rust" value="rust" />
              <el-option label="Ruby" value="ruby" />
              <el-option label="SQL" value="sql" />
            </el-select>
            <span style="margin-left:10px; font-size:12px; color:#909399">
              留空：优先用激活课程的 programmingLanguage，否则从素材自动检测，最终回退 Java
            </span>
          </el-form-item>
        </el-col>
      </el-row>

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
        <strong>题库生成 {{ currentTask.taskId.substring(0, 8) }}</strong>
        <el-tag :type="statusType(currentTask.status)">{{ statusName(currentTask.status) }}</el-tag>
      </div>
      <div style="margin-bottom:8px; font-size:13px; color:#606266">{{ currentTask.stageText }}</div>
      <el-progress :percentage="currentTask.progress || 0" :status="currentTask.status === 'failed' ? 'exception' : null" :stroke-width="14" />
      <div v-if="currentTask.errorMsg" style="margin-top:10px; padding:8px; background:#ffebee; color:#c62828; border-radius:6px; font-size:13px">{{ currentTask.errorMsg }}</div>
      <div v-if="currentTask.status === 'success' && currentTask.resultHistoryId" style="margin-top:14px">
        <el-button type="success" @click="onDownloadResult">下载题库 Excel</el-button>
        <el-button @click="currentTask = null">关闭</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { generateQuestionBank } from '@/api/questionBank'
import { getTask, subscribeTaskSse } from '@/api/task'
import { downloadFile } from '@/api/download'
import { pointRules } from '@/api/auth'
import { getActiveCourseConfig } from '@/api/courseConfig'
import { inject } from 'vue'

const form = reactive({
  mode: 'per_file',
  pptFileList: [], pptFiles: [], customTemplate: null, chapter: '',
  singleCount: 5, multiCount: 3, judgeCount: 5, programCount: 1,
  difficulty: '混合', contentLevel: 'standard', provider: '',
  programmingLanguage: '',
  fileConfigs: []
})

// 仅当请求中含编程题时显示语言下拉
const hasProgramQuestions = computed(() => {
  if (form.mode === 'per_file') {
    return form.fileConfigs.some(c => Number(c?.typeCount?.program) > 0)
  }
  return Number(form.programCount) > 0
})

const submitting = ref(false)
const currentTask = ref(null)
let pollTimer = null
const rules = ref({ questionBankCost: 5, questionBankPerQuestionCost: 1 })
const refreshUserInfo = inject('refreshUserInfo', null)

const loadRules = async () => {
  try {
    const r = await pointRules()
    if (r.data) rules.value = r.data
  } catch (e) {}
}
const activeCourse = ref(null)
const loadActiveCourse = async () => {
  try {
    const r = await getActiveCourseConfig()
    activeCourse.value = r.data || null
  } catch (e) { activeCourse.value = null }
}

onMounted(() => { loadRules(); loadActiveCourse() })

const totalQuestions = computed(() => {
  return form.fileConfigs.reduce((s, c) => {
    const t = c.typeCount || {}
    return s + (Number(t.single) || 0) + (Number(t.multi) || 0) + (Number(t.judge) || 0) + (Number(t.program) || 0)
  }, 0)
})

const fileSubtotal = (i) => {
  const t = form.fileConfigs[i]?.typeCount || {}
  return (Number(t.single) || 0) + (Number(t.multi) || 0) + (Number(t.judge) || 0) + (Number(t.program) || 0)
}

// 两段式计费：base + N × perQuestion
const estimatedCost = computed(() => {
  const base = Number(rules.value.questionBankCost) || 5
  const perQ = Number(rules.value.questionBankPerQuestionCost) || 1
  let n = 0
  if (form.mode === 'per_file') {
    n = totalQuestions.value
  } else {
    n = (Number(form.singleCount) || 0) + (Number(form.multiCount) || 0)
        + (Number(form.judgeCount) || 0) + (Number(form.programCount) || 0)
  }
  return base + n * perQ
})

const statusName = (s) => ({ pending: '已提交', running: '生成中', success: '已完成', failed: '失败' }[s] || s)
const statusType = (s) => ({ pending: 'info', running: '', success: 'success', failed: 'danger' }[s] || '')

const stripExt = (n) => n && n.includes('.') ? n.substring(0, n.lastIndexOf('.')) : n
const humanSize = (b) => {
  if (!b) return ''
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(1) + ' MB'
}

const ensureFileConfigs = () => {
  while (form.fileConfigs.length < form.pptFiles.length) {
    const idx = form.fileConfigs.length
    const f = form.pptFiles[idx]
    form.fileConfigs.push({
      fileIndex: idx,
      chapter: f ? stripExt(f.name) : '',
      typeCount: { single: 5, multi: 2, judge: 3, program: 0 }
    })
  }
  while (form.fileConfigs.length > form.pptFiles.length) form.fileConfigs.pop()
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

const onSubmit = async () => {
  if (!form.pptFiles.length) return ElMessage.warning('请上传至少一个素材')
  if (form.mode === 'per_file') {
    for (let i = 0; i < form.fileConfigs.length; i++) {
      const c = form.fileConfigs[i]
      if (!c.chapter) return ElMessage.warning(`请填写文件 ${i + 1} 的章节标题`)
      const sum = fileSubtotal(i)
      if (sum <= 0) return ElMessage.warning(`文件 ${i + 1} 至少选择一种题型并设置数量`)
    }
    if (totalQuestions.value > 200) return ElMessage.warning('总题数不能超过 200 道')
  } else {
    if (!form.chapter) return ElMessage.warning('请填写章节标题')
    const total = form.singleCount + form.multiCount + form.judgeCount + form.programCount
    if (total <= 0) return ElMessage.warning('至少选择一种题型')
  }

  const fd = new FormData()
  for (const f of form.pptFiles) fd.append('pptFiles', f)
  if (form.customTemplate) fd.append('customTemplate', form.customTemplate)

  const payload = {
    mode: form.mode,
    chapter: form.chapter,
    typeCount: form.mode === 'single' ? { single: form.singleCount, multi: form.multiCount, judge: form.judgeCount, program: form.programCount } : {},
    fileConfigs: form.mode === 'per_file' ? form.fileConfigs : [],
    difficulty: form.difficulty,
    contentLevel: form.contentLevel,
    provider: form.provider,
    programmingLanguage: form.programmingLanguage || ''
  }
  fd.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))

  submitting.value = true
  try {
    const r = await generateQuestionBank(fd)
    currentTask.value = r.data
    // 通知 TaskFloatButton 立即刷新
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
        if (currentTask.value) {
          if (p.progress !== undefined) currentTask.value.progress = p.progress
          if (p.stage !== undefined) currentTask.value.stageText = p.stage
        }
      },
      onDone: (p) => {
        if (p && currentTask.value) {
          if (p.progress !== undefined) currentTask.value.progress = p.progress
          if (p.stage !== undefined) currentTask.value.stageText = p.stage
          if (p.status) currentTask.value.status = p.status
          if (p.historyId) currentTask.value.resultHistoryId = p.historyId
        }
        if (p && p.status) {
          getTask(taskId).then(r => { currentTask.value = r.data }).catch(() => {})
        }
        if (refreshUserInfo) refreshUserInfo()
      },
      onError: () => {
        if (sseHandle) sseHandle.close()
        startPollingFallback()
      }
    })
    pollTimer = { __sse: true, close: () => sseHandle?.close() }
  } catch (e) {
    startPollingFallback()
  }
}

const onDownloadResult = () => { if (currentTask.value?.resultHistoryId) downloadFile(`/api/question-bank/download/${currentTask.value.resultHistoryId}`) }
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
