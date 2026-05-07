<template>
  <div class="page-card">
    <h2 class="page-title">✏️ 题库管理</h2>
    <p class="page-subtitle">在已生成的题库基础上手动增删改题目，可重新导出 Excel</p>

    <el-form inline>
      <el-form-item label="选择题库">
        <el-select v-model="selectedBankId" filterable style="width:480px" @change="onBankChange">
          <el-option v-for="b in banks" :key="b.id" :label="bankLabel(b)" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="selectedBankId">
        <el-button type="primary" @click="onAdd">➕ 新增题目</el-button>
        <el-button type="success" @click="onRegenerate" :loading="regenerating">🔄 重新导出 Excel</el-button>
      </el-form-item>
    </el-form>

    <el-table v-if="selectedBankId" :data="items" stripe v-loading="loading">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="type" label="题型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="typeColor(row.type)">{{ typeName(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="difficulty" label="难度" width="80" />
      <el-table-column prop="knowledge" label="知识点" width="120" show-overflow-tooltip />
      <el-table-column prop="stem" label="题干" min-width="280" show-overflow-tooltip />
      <el-table-column prop="answer" label="答案" width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除？" @confirm="onDelete(row)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑题目' : '新增题目'" width="700px">
      <el-form :model="editing" label-width="100px">
        <el-form-item label="题型" required>
          <el-select v-model="editing.type" style="width:100%">
            <el-option label="单选题" value="single" />
            <el-option label="多选题" value="multi" />
            <el-option label="判断题" value="judge" />
            <el-option label="问答题/编程题" value="program" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="editing.difficulty">
            <el-radio value="简单">简单</el-radio>
            <el-radio value="一般">一般</el-radio>
            <el-radio value="困难">困难</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="知识点"><el-input v-model="editing.knowledge" /></el-form-item>
        <el-form-item label="题干" required>
          <el-input v-model="editing.stem" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input v-model="editing.answer" :placeholder="answerHint(editing.type)" />
        </el-form-item>
        <el-form-item label="解析"><el-input v-model="editing.explanation" type="textarea" :rows="2" /></el-form-item>
        <el-form-item v-if="editing.type === 'single' || editing.type === 'multi'" label="选项">
          <div v-for="(opt, i) in editingOptions" :key="i" style="display:flex; gap:6px; margin-bottom:4px">
            <el-input v-model="editingOptions[i]" :placeholder="`选项${'ABCDEFG'[i]}`" />
            <el-button size="small" type="danger" @click="editingOptions.splice(i, 1)">×</el-button>
          </div>
          <el-button size="small" @click="editingOptions.push('')">➕ 添加选项</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { questionBankHistory } from '@/api/questionBank'
import { listQuestions, saveQuestion, deleteQuestion, regenerateBank } from '@/api/extra'

const banks = ref([])
const selectedBankId = ref(null)
const items = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const regenerating = ref(false)
const editing = reactive({ id: null, bankId: null, type: 'single', difficulty: '一般', knowledge: '', stem: '', answer: '', explanation: '', optionsJson: '' })
const editingOptions = ref([])

const bankLabel = (b) => `${b.id} - ${b.chapter} (${b.totalCount}题, ${b.createTime})`
const typeName = (t) => ({ single: '单选', multi: '多选', judge: '判断', program: '编程' }[t] || t)
const typeColor = (t) => ({ single: '', multi: 'success', judge: 'warning', program: 'danger' }[t] || '')
const answerHint = (t) => ({
  single: '如 A',
  multi: '如 A,B,D',
  judge: '正确 或 错误',
  program: '完整答案文本'
}[t] || '')

const refreshBanks = async () => {
  const r = await questionBankHistory(100)
  banks.value = (r.data || []).filter(b => b.status === 'success')
}

const onBankChange = async () => {
  if (!selectedBankId.value) return
  loading.value = true
  try {
    const r = await listQuestions(selectedBankId.value)
    items.value = r.data
  } finally { loading.value = false }
}

const onAdd = () => {
  Object.assign(editing, { id: null, bankId: selectedBankId.value, type: 'single', difficulty: '一般', knowledge: '', stem: '', answer: '', explanation: '' })
  editingOptions.value = ['', '', '', '']
  dialogVisible.value = true
}

const onEdit = (row) => {
  Object.assign(editing, row)
  try { editingOptions.value = row.optionsJson ? JSON.parse(row.optionsJson) : [] } catch (e) { editingOptions.value = [] }
  dialogVisible.value = true
}

const onSave = async () => {
  if (!editing.stem || !editing.answer) return ElMessage.warning('请填写题干和答案')
  saving.value = true
  try {
    const data = { ...editing }
    if (editing.type === 'single' || editing.type === 'multi') {
      data.optionsJson = JSON.stringify(editingOptions.value.filter(s => s))
    } else {
      data.optionsJson = '[]'
    }
    await saveQuestion(data)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    onBankChange()
  } finally { saving.value = false }
}

const onDelete = async (row) => {
  await deleteQuestion(row.id)
  ElMessage.success('已删除')
  onBankChange()
}

const onRegenerate = async () => {
  regenerating.value = true
  try {
    const r = await regenerateBank(selectedBankId.value)
    ElMessage.success('已重新导出: ' + r.data.outputFileName)
  } finally { regenerating.value = false }
}

onMounted(refreshBanks)
</script>
