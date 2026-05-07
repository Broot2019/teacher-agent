<template>
  <div class="page-card">
    <h2 class="page-title">📜 操作审计日志</h2>
    <p class="page-subtitle">记录关键操作（登录、用户管理、生成任务等）</p>

    <el-form inline>
      <el-form-item label="操作类型">
        <el-select v-model="filter.action" clearable @change="refresh" style="width:200px">
          <el-option label="全部" value="" />
          <el-option label="登录" value="LOGIN" />
          <el-option label="新增用户" value="USER_SAVE" />
          <el-option label="切换状态" value="USER_TOGGLESTATUS" />
          <el-option label="重置密码" value="USER_RESETPASSWORD" />
          <el-option label="删除用户" value="USER_DELETE" />
          <el-option label="教案生成" value="LESSON_GEN" />
          <el-option label="题库生成" value="QUESTION_GEN" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="refresh">🔄 刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="logs" stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="action" label="操作" width="180">
        <template #default="{ row }">
          <el-tag size="small" :type="actionType(row.action)">{{ actionName(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetType" label="对象" width="100" />
      <el-table-column prop="targetId" label="对象ID" width="120" />
      <el-table-column prop="status" label="结果" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'">
            {{ row.status === 'success' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
      <el-table-column prop="errorMsg" label="错误" min-width="150" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="160" />
    </el-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { auditList } from '@/api/extra'

const logs = ref([])
const loading = ref(false)
const filter = reactive({ action: '' })

const actionName = (a) => ({
  LOGIN: '登录', USER_SAVE: '保存用户', USER_TOGGLESTATUS: '切换状态',
  USER_RESETPASSWORD: '重置密码', USER_DELETE: '删除用户',
  LESSON_GEN: '教案生成', QUESTION_GEN: '题库生成'
}[a] || a)
const actionType = (a) => {
  if (a === 'LOGIN') return 'primary'
  if (a?.startsWith('USER_')) return 'warning'
  if (a?.includes('GEN')) return 'success'
  return ''
}

const refresh = async () => {
  loading.value = true
  try {
    const r = await auditList({ limit: 200, action: filter.action || undefined })
    logs.value = r.data
  } finally { loading.value = false }
}
onMounted(refresh)
</script>
