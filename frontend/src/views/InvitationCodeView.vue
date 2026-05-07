<template>
  <div class="page-card">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px">
      <div>
        <h2 class="page-title">🎫 邀请码管理</h2>
        <p class="page-subtitle">生成邀请码供新教师注册使用</p>
      </div>
      <div style="display:flex; gap:8px">
        <el-button type="primary" @click="onBatchCreate">批量生成</el-button>
        <el-button @click="onSingleCreate">生成单个</el-button>
      </div>
    </div>

    <el-table :data="codes" stripe v-loading="loading" size="default">
      <el-table-column prop="id" label="ID" width="55" />
      <el-table-column prop="code" label="邀请码" width="160">
        <template #default="{ row }">
          <code style="font-size:13px; background:#f5f5f5; padding:2px 8px; border-radius:4px">{{ row.code }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="initialPoints" label="初始积分" width="90" />
      <el-table-column prop="initialQuota" label="初始配额" width="90" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusName(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="usedBy" label="使用者" width="80" />
      <el-table-column prop="expireTime" label="过期时间" width="160" />
      <el-table-column prop="note" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" width="150" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="row.status === 'unused'" size="small" type="danger" plain @click="onDisable(row)">禁用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="batchMode ? '批量生成邀请码' : '生成邀请码'" width="450px">
      <el-form :model="form" label-width="90px">
        <el-form-item v-if="batchMode" label="数量">
          <el-input-number v-model="form.count" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="有效天数">
          <el-input-number v-model="form.validDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="初始积分">
          <el-input-number v-model="form.initialPoints" :min="0" />
        </el-form-item>
        <el-form-item label="初始配额">
          <el-input-number v-model="form.initialQuota" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onGenerate" :loading="generating">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { invitationCodeList, invitationCodeCreate, invitationCodeBatchCreate, invitationCodeDisable } from '@/api/admin'

const codes = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const batchMode = ref(false)
const generating = ref(false)

const form = reactive({ count: 5, validDays: 30, initialPoints: 1000, initialQuota: 100, note: '' })

const statusName = (s) => ({ unused: '未使用', used: '已使用', expired: '已过期', disabled: '已禁用' }[s] || s)
const statusType = (s) => ({ unused: 'success', used: 'info', expired: 'warning', disabled: 'danger' }[s] || '')

const refresh = async () => {
  loading.value = true
  try { const r = await invitationCodeList(); codes.value = r.data } finally { loading.value = false }
}

const onSingleCreate = () => { batchMode.value = false; form.count = 1; dialogVisible.value = true }
const onBatchCreate = () => { batchMode.value = true; form.count = 5; dialogVisible.value = true }

const onGenerate = async () => {
  generating.value = true
  try {
    let r
    if (batchMode.value) {
      r = await invitationCodeBatchCreate(form)
    } else {
      r = await invitationCodeCreate(form)
    }
    ElMessage.success(r.message || '生成成功')
    dialogVisible.value = false
    refresh()
  } finally { generating.value = false }
}

const onDisable = async (row) => {
  await invitationCodeDisable(row.id)
  ElMessage.success('已禁用')
  refresh()
}

onMounted(refresh)
</script>
