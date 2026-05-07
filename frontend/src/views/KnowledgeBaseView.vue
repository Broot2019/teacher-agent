<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">知识库管理</span>
          <el-button type="primary" size="small" @click="uploadDialogVisible = true">上传资料</el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        上传教材、教案、教学大纲等资料到知识库，系统将自动建立检索索引。生成教案和题库时，可从知识库中检索补充内容。
      </el-alert>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80" align="center" />
        <el-table-column label="大小" width="100" align="center">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="80" align="center" />
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ').substring(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-popconfirm title="确定删除？" @confirm="onDelete(row)">
              <template #reference>
                <el-button type="danger" size="small" text>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="暂无知识库资料，点击右上角上传" />
    </el-card>

    <el-dialog v-model="uploadDialogVisible" title="上传资料到知识库" width="480px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="uploadTitle" placeholder="如：第三章 面向对象编程 教材" />
        </el-form-item>
        <el-form-item label="文件">
          <input type="file" ref="fileInput" accept=".pdf,.docx,.doc,.pptx,.ppt,.txt,.md" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onUpload" :loading="uploading">上传并索引</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listKnowledgeBases, uploadKnowledgeBase, deleteKnowledgeBase } from '@/api/knowledgeBase'

const loading = ref(false)
const list = ref([])
const uploadDialogVisible = ref(false)
const uploadTitle = ref('')
const uploading = ref(false)
const fileInput = ref(null)

const formatSize = (bytes) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

const refresh = async () => {
  loading.value = true
  try {
    const r = await listKnowledgeBases()
    list.value = r.data || []
  } finally {
    loading.value = false
  }
}

const onUpload = async () => {
  const file = fileInput.value?.files?.[0]
  if (!file) return ElMessage.warning('请选择文件')
  uploading.value = true
  try {
    await uploadKnowledgeBase(uploadTitle.value || file.name, file)
    ElMessage.success('上传成功，已建立检索索引')
    uploadDialogVisible.value = false
    uploadTitle.value = ''
    refresh()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const onDelete = async (row) => {
  await deleteKnowledgeBase(row.id)
  ElMessage.success('已删除')
  refresh()
}

onMounted(refresh)
</script>
