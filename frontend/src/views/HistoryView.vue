<template>
  <div class="page-card">
    <h2 class="page-title">📂 历史记录</h2>
    <p class="page-subtitle">查看历次生成的教案与题库，支持下载与删除</p>

    <el-tabs v-model="active" type="border-card">
      <el-tab-pane label="📝 教案历史" name="lesson">
        <el-table :data="lessonList" stripe v-loading="loading.lesson" empty-text="暂无记录" size="default">
          <el-table-column prop="id" label="ID" width="55" />
          <el-table-column prop="chapter" label="章节" min-width="180" show-overflow-tooltip />
          <el-table-column prop="weekNo" label="周次" width="70" />
          <el-table-column prop="packageMode" label="模式" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="modeTagType(row.packageMode)">{{ modeName(row.packageMode) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="llmProvider" label="模型" width="110">
            <template #default="{ row }">{{ row.llmProvider }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70">
            <template #default="{ row }">
              <span :class="'status-badge status-' + row.status">{{ row.status === 'success' ? '成功' : '失败' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="150" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain :disabled="row.status !== 'success'" @click="onPreview(row)">预览</el-button>
              <el-button size="small" type="success" plain :disabled="row.status !== 'success'" @click="downloadLesson(row.id)">下载</el-button>
              <el-popconfirm title="确认删除该记录？" @confirm="onDeleteLesson(row.id)">
                <template #reference>
                  <el-button size="small" type="danger" plain>删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="❓ 题库历史" name="question">
        <el-table :data="questionList" stripe v-loading="loading.question" empty-text="暂无记录" size="default">
          <el-table-column prop="id" label="ID" width="55" />
          <el-table-column prop="chapter" label="章节" min-width="180" show-overflow-tooltip />
          <el-table-column prop="totalCount" label="题数" width="65" />
          <el-table-column prop="difficultyDist" label="难度" width="70" />
          <el-table-column prop="llmProvider" label="模型" width="110" />
          <el-table-column prop="status" label="状态" width="70">
            <template #default="{ row }">
              <span :class="'status-badge status-' + row.status">{{ row.status === 'success' ? '成功' : '失败' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="150" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="success" plain :disabled="row.status !== 'success'" @click="downloadQuestion(row.id)">下载</el-button>
              <el-popconfirm title="确认删除该记录？" @confirm="onDeleteQuestion(row.id)">
                <template #reference>
                  <el-button size="small" type="danger" plain>删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 预览模态框 -->
    <el-dialog v-model="previewVisible" title="教案预览" width="920px" top="4vh">
      <div style="padding-bottom:12px; border-bottom:1px solid #eee; margin-bottom:16px; display:flex; align-items:center; gap:12px">
        <el-button type="primary" plain size="small" @click="onPrint">打印 / 另存为 PDF</el-button>
        <span style="font-size:12px; color:#8c8c8c">打印对话框中可选择"另存为 PDF"</span>
      </div>
      <div id="lesson-preview-content" v-loading="previewLoading">
        <el-empty v-if="previewData.length === 0 && !previewLoading" description="暂无预览数据" />
        <div v-for="(d, idx) in previewData" :key="idx">
          <h3 style="color:#1a1a2e; border-bottom:2px solid #4361ee; padding-bottom:8px">第 {{ idx + 1 }} 周教案</h3>
          <div v-for="(s, si) in d.sessions || []" :key="si" class="session-block">
            <h4 style="color:#4361ee; margin:0 0 10px">第 {{ si + 1 }} 次教案</h4>
            <table class="preview-table">
              <tr><th>班别</th><td>{{ s.className }}</td></tr>
              <tr><th>周次</th><td>第 {{ s.week }} 周</td></tr>
              <tr><th>课题</th><td><strong>{{ s.title }}</strong></td></tr>
              <tr><th>知识目标</th><td>{{ s.knowledgeGoal }}</td></tr>
              <tr><th>能力目标</th><td>{{ s.abilityGoal }}</td></tr>
              <tr><th>素养目标</th><td>{{ s.literacyGoal }}</td></tr>
              <tr><th>重点</th><td>{{ s.keyPoints }}</td></tr>
              <tr><th>难点</th><td>{{ s.difficultPoints }}</td></tr>
              <tr><th>学情</th><td>{{ s.studentSituation }}</td></tr>
            </table>
            <h5 style="margin:12px 0 6px">教学过程</h5>
            <table class="preview-table">
              <tr><th style="width:90px">时间</th><th>教师活动</th><th>学生活动</th></tr>
              <tr v-for="(t, ti) in s.timeline || []" :key="ti">
                <td>{{ t.time }}</td><td>{{ t.teacherAction }}</td><td>{{ t.studentAction }}</td>
              </tr>
            </table>
            <p style="margin-top:8px"><strong>反思：</strong>{{ s.reflection }}</p>
            <p><strong>诊改：</strong>{{ s.improvement }}</p>
          </div>
          <p style="margin-top:8px"><strong>教学资源：</strong>{{ d.teachingResource }}</p>
          <p><strong>课外作业：</strong>{{ d.homework }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { lessonPlanHistory } from '@/api/lessonPlan'
import { questionBankHistory } from '@/api/questionBank'
import { previewLesson, deleteLessonHistory, deleteQuestionHistory } from '@/api/extra'
import { downloadFile } from '@/api/download'

const active = ref('lesson')
const lessonList = ref([])
const questionList = ref([])
const loading = reactive({ lesson: false, question: false })
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewData = ref([])

const modeName = (m) => ({ single: '单次', weekly: '周', full: '满载' }[m] || m)
const modeTagType = (m) => ({ single: '', weekly: 'success', full: 'warning' }[m] || '')

const refresh = async () => {
  loading.lesson = true; loading.question = true
  try {
    const [r1, r2] = await Promise.all([lessonPlanHistory(), questionBankHistory()])
    lessonList.value = r1.data
    questionList.value = r2.data
  } finally {
    loading.lesson = false; loading.question = false
  }
}

const downloadLesson = (id) => downloadFile(`/api/lesson-plan/download/${id}`)
const downloadQuestion = (id) => downloadFile(`/api/question-bank/download/${id}`)

const onDeleteLesson = async (id) => {
  await deleteLessonHistory(id)
  ElMessage.success('已删除')
  refresh()
}

const onDeleteQuestion = async (id) => {
  await deleteQuestionHistory(id)
  ElMessage.success('已删除')
  refresh()
}

const onPreview = async (row) => {
  previewVisible.value = true
  previewLoading.value = true
  previewData.value = []
  try {
    const r = await previewLesson(row.id)
    previewData.value = r.data || []
  } finally { previewLoading.value = false }
}

const onPrint = () => {
  const content = document.getElementById('lesson-preview-content')
  if (!content) return
  const w = window.open('', '_blank')
  w.document.write(`<html><head><title>教案打印</title>
    <style>body{font-family:"Microsoft YaHei",sans-serif;padding:20px}h3{color:#1a1a2e;border-bottom:2px solid #4361ee;padding-bottom:8px}table{border-collapse:collapse;width:100%;margin:10px 0}th,td{border:1px solid #dcdfe6;padding:6px 10px;text-align:left;vertical-align:top}th{background:#f5f7fa;font-weight:600;width:90px}.session-block{margin:16px 0;padding:14px;border:1px solid #ebeef5;border-radius:4px}</style>
    </head><body>${content.innerHTML}</body></html>`)
  w.document.close()
  setTimeout(() => w.print(), 500)
}

onMounted(refresh)
</script>
