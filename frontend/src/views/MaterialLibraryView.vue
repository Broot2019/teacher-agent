<template>
  <div class="page-card">
    <h2 class="page-title">📚 章节资料库</h2>
    <p class="page-subtitle">上传过的 PPT/PDF 入库，可重复使用</p>

    <el-form inline>
      <el-form-item label="搜索章节">
        <el-input v-model="filter.chapter" placeholder="例: 第一章" clearable @clear="refresh" @change="refresh" />
      </el-form-item>
      <el-form-item label="课程">
        <el-input v-model="filter.course" placeholder="Java" clearable @clear="refresh" @change="refresh" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="uploadVisible = true">📤 上传资料</el-button>
        <el-button @click="refresh">🔄 刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="materials" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="chapter" label="章节" width="180" />
      <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column prop="fileType" label="类型" width="80" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ humanSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="course" label="课程" width="100" />
      <el-table-column prop="useCount" label="使用次数" width="100" />
      <el-table-column prop="isPublic" label="共享" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.isPublic ? 'success' : 'info'">
            {{ row.isPublic ? '公开' : '私有' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="上传时间" width="160" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="onDownload(row)">下载</el-button>
          <el-popconfirm title="确认删除？" @confirm="onDelete(row)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="uploadVisible" title="上传章节资料" width="500px">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="文件" required>
          <el-upload :auto-upload="false" :on-change="(f) => uploadForm.file = f.raw"
                     :on-remove="() => uploadForm.file = null" accept=".ppt,.pptx,.pdf,.docx" :limit="1">
            <el-button>选择文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="章节" required>
          <el-input v-model="uploadForm.chapter" placeholder="例: 第一章 Java 开发入门" />
        </el-form-item>
        <el-form-item label="课程">
          <el-input v-model="uploadForm.course" placeholder="Java" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="共享">
          <el-radio-group v-model="uploadForm.isPublic">
            <el-radio value="1">所有教师可用</el-radio>
            <el-radio value="0">仅自己可用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" @click="onUpload" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { materialList, materialUpload, materialDelete, materialDownloadUrl } from '@/api/extra'
import { downloadFile } from '@/api/download'

const materials = ref([])
const loading = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const filter = reactive({ chapter: '', course: '' })
const uploadForm = reactive({ file: null, chapter: '', course: 'Java', description: '', isPublic: '1' })

const humanSize = (b) => {
  if (!b) return '0 B'
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(1) + ' MB'
}

const refresh = async () => {
  loading.value = true
  try {
    const r = await materialList(filter.chapter, filter.course)
    materials.value = r.data
  } finally { loading.value = false }
}

const onUpload = async () => {
  if (!uploadForm.file) return ElMessage.warning('请选择文件')
  if (!uploadForm.chapter) return ElMessage.warning('请填写章节')
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', uploadForm.file)
    fd.append('chapter', uploadForm.chapter)
    fd.append('course', uploadForm.course)
    fd.append('description', uploadForm.description || '')
    fd.append('isPublic', uploadForm.isPublic)
    await materialUpload(fd)
    ElMessage.success('上传成功')
    uploadVisible.value = false
    Object.assign(uploadForm, { file: null, chapter: '', course: 'Java', description: '', isPublic: '1' })
    refresh()
  } finally { uploading.value = false }
}

const onDelete = async (row) => {
  await materialDelete(row.id)
  ElMessage.success('已删除')
  refresh()
}

const onDownload = (row) => downloadFile(materialDownloadUrl(row.id), row.fileName)

onMounted(refresh)
</script>
