<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">课程配置</span>
          <el-button type="primary" size="small" @click="onAdd">新增课程</el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        配置您教授的课程信息，系统将根据课程配置动态调整教案与题库的生成策略。激活的课程配置将在生成教案/题库时自动应用。
      </el-alert>

      <el-table :data="list" stripe style="width:100%" v-loading="loading">
        <el-table-column prop="courseName" label="课程名称" min-width="150" />
        <el-table-column prop="major" label="专业" min-width="120" />
        <el-table-column prop="educationLevel" label="学段" width="100" />
        <el-table-column prop="teachingMode" label="教学模式" width="100" />
        <el-table-column prop="className" label="默认班级" min-width="150" show-overflow-tooltip />
        <el-table-column prop="programmingLanguage" label="编程语言" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.programmingLanguage" size="small" type="warning">{{ langDisplay(row.programmingLanguage) }}</el-tag>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive === 1 ? 'success' : 'info'" size="small" effect="dark">
              {{ row.isActive === 1 ? '使用中' : '未激活' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button v-if="row.isActive !== 1" type="success" size="small" text @click="onActivate(row)">激活</el-button>
            <el-button type="primary" size="small" text @click="onEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="onDelete(row)">
              <template #reference>
                <el-button type="danger" size="small" text>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑课程配置' : '新增课程配置'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="课程名称" required>
          <el-input v-model="form.courseName" placeholder="如：Java程序设计基础" />
        </el-form-item>
        <el-form-item label="专业" required>
          <el-input v-model="form.major" placeholder="如：大数据技术" />
        </el-form-item>
        <el-form-item label="学段" required>
          <el-select v-model="form.educationLevel" style="width:100%">
            <el-option label="高职院校" value="高职院校" />
            <el-option label="中职学校" value="中职学校" />
            <el-option label="本科院校" value="本科院校" />
            <el-option label="高中" value="高中" />
            <el-option label="初中" value="初中" />
            <el-option label="小学" value="小学" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学模式">
          <el-select v-model="form.teachingMode" style="width:100%">
            <el-option label="理实一体" value="理实一体" />
            <el-option label="理论教学" value="理论教学" />
            <el-option label="实验教学" value="实验教学" />
            <el-option label="项目驱动" value="项目驱动" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生情况" required>
          <el-input v-model="form.studentDescription" type="textarea" :rows="2" placeholder="如：大一学生，编程基础参差不齐，动手能力较弱" />
        </el-form-item>
        <el-form-item label="默认班级">
          <el-input v-model="form.className" placeholder="如：2024级大数据技术01-05班" />
        </el-form-item>
        <el-form-item label="编程语言">
          <el-select v-model="form.programmingLanguage" style="width:100%" clearable placeholder="非程序设计课程可留空">
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
          <div style="font-size:12px; color:#909399; margin-top:4px">仅影响【编程题】生成与校验：Java 走完整编译运行校验，其他语言走代码格式校验。非程序设计课程留空即可。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listCourseConfigs, saveCourseConfig, activateCourseConfig, deleteCourseConfig } from '@/api/courseConfig'

const loading = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const form = ref({})

const LANG_NAME_MAP = {
  java: 'Java', python: 'Python', c: 'C', cpp: 'C++', csharp: 'C#',
  go: 'Go', javascript: 'JavaScript', typescript: 'TypeScript',
  php: 'PHP', kotlin: 'Kotlin', swift: 'Swift', rust: 'Rust',
  ruby: 'Ruby', sql: 'SQL'
}
const langDisplay = (k) => LANG_NAME_MAP[(k || '').toLowerCase()] || k

const refresh = async () => {
  loading.value = true
  try {
    const r = await listCourseConfigs()
    list.value = r.data || []
  } finally {
    loading.value = false
  }
}

const onAdd = () => {
  form.value = { courseName: '', major: '', educationLevel: '高职院校', teachingMode: '理实一体', studentDescription: '', className: '', programmingLanguage: '' }
  dialogVisible.value = true
}

const onEdit = (row) => {
  form.value = { id: row.id, courseName: row.courseName, major: row.major, educationLevel: row.educationLevel, teachingMode: row.teachingMode, studentDescription: row.studentDescription, className: row.className, programmingLanguage: row.programmingLanguage || '' }
  dialogVisible.value = true
}

const onSave = async () => {
  if (!form.value.courseName) return ElMessage.warning('请填写课程名称')
  await saveCourseConfig(form.value)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  refresh()
}

const onActivate = async (row) => {
  await activateCourseConfig(row.id)
  ElMessage.success('已激活')
  refresh()
}

const onDelete = async (row) => {
  await deleteCourseConfig(row.id)
  ElMessage.success('已删除')
  refresh()
}

onMounted(refresh)
</script>
