<template>
  <div class="page-card">
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px">
      <div>
        <h2 class="page-title">👥 用户管理</h2>
        <p class="page-subtitle">管理教师账号、积分、配额</p>
      </div>
      <el-button type="primary" @click="onAdd">新增用户</el-button>
    </div>

    <el-table :data="users" stripe v-loading="loading" size="default">
      <el-table-column prop="id" label="ID" width="50" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column prop="role" label="角色" width="80">
        <template #default="{ row }">
          <el-tag :type="row.role === 'admin' ? 'danger' : 'primary'" size="small">{{ row.role === 'admin' ? '管理员' : '教师' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="points" label="积分" width="80" />
      <el-table-column prop="monthlyQuota" label="月配额" width="70" />
      <el-table-column prop="status" label="状态" width="70">
        <template #default="{ row }">
          <el-tag :type="row.status === 'enabled' ? 'success' : 'info'" size="small">{{ row.status === 'enabled' ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最近登录" width="150" />
      <el-table-column label="操作" min-width="300">
        <template #default="{ row }">
          <el-button size="small" @click="onEdit(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 'enabled' ? 'warning' : 'success'" @click="onToggle(row)" :disabled="row.id === 1">{{ row.status === 'enabled' ? '禁用' : '启用' }}</el-button>
          <el-button size="small" type="info" @click="onResetPwd(row)">重置密码</el-button>
          <el-button size="small" type="warning" plain @click="onAdjustPoints(row)">积分</el-button>
          <el-popconfirm title="确认删除？" @confirm="onDelete(row)">
            <template #reference><el-button size="small" type="danger" :disabled="row.id === 1">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑用户 -->
    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="editing" label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="editing.username" :disabled="!!editing.id" /></el-form-item>
        <el-form-item :label="editing.id ? '新密码' : '初始密码'" :required="!editing.id">
          <el-input v-model="editing.password" type="password" show-password :placeholder="editing.id ? '留空不修改' : ''" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editing.role" style="width:100%">
            <el-option label="教师" value="teacher" /><el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="editing.realName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="editing.email" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editing.status"><el-radio value="enabled">启用</el-radio><el-radio value="disabled">禁用</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="月配额">
          <el-input-number v-model="editing.monthlyQuota" :min="0" :max="9999" />
          <span style="margin-left:8px; font-size:12px; color:#8c8c8c">0 = 不限</span>
        </el-form-item>
        <el-form-item label="积分">
          <el-input-number v-model="editing.points" :min="0" :max="999999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 积分调整 -->
    <el-dialog v-model="pointsDialogVisible" title="调整积分" width="400px">
      <el-form :model="pointsForm" label-width="90px">
        <el-form-item label="用户">{{ pointsTargetUser?.username }} (当前: {{ pointsTargetUser?.points ?? 0 }})</el-form-item>
        <el-form-item label="调整量">
          <el-input-number v-model="pointsForm.amount" :min="-99999" :max="99999" />
          <span style="margin-left:8px; font-size:12px; color:#8c8c8c">正数=充值，负数=扣减</span>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="pointsForm.reason" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pointsDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSavePoints">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userList, userSave, userToggleStatus, userResetPassword, userDelete } from '@/api/auth'
import { grantPoints } from '@/api/admin'

const users = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editing = reactive({ id: null, username: '', password: '', role: 'teacher', realName: '', email: '', status: 'enabled', monthlyQuota: 100, points: 1000 })

const pointsDialogVisible = ref(false)
const pointsTargetUser = ref(null)
const pointsForm = reactive({ amount: 100, reason: '' })

const refresh = async () => {
  loading.value = true
  try { const r = await userList(); users.value = r.data } finally { loading.value = false }
}

const onAdd = () => {
  Object.assign(editing, { id: null, username: '', password: '', role: 'teacher', realName: '', email: '', status: 'enabled', monthlyQuota: 100, points: 1000 })
  dialogVisible.value = true
}

const onEdit = (row) => {
  Object.assign(editing, { ...row, password: '' })
  dialogVisible.value = true
}

const onSave = async () => {
  if (!editing.username) return ElMessage.warning('请填用户名')
  if (!editing.id && !editing.password) return ElMessage.warning('请填初始密码')
  saving.value = true
  try {
    await userSave(editing)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    refresh()
  } finally { saving.value = false }
}

const onToggle = async (row) => { await userToggleStatus(row.id); ElMessage.success('状态已切换'); refresh() }

const onResetPwd = async (row) => {
  const r = await userResetPassword(row.id)
  await ElMessageBox.alert(`新密码：${r.data.newPassword}\n请将此密码告知用户`, '密码已重置', { type: 'success' })
  refresh()
}

const onDelete = async (row) => { await userDelete(row.id); ElMessage.success('已删除'); refresh() }

const onAdjustPoints = (row) => {
  pointsTargetUser.value = row
  pointsForm.amount = 100
  pointsForm.reason = ''
  pointsDialogVisible.value = true
}

const onSavePoints = async () => {
  if (!pointsForm.amount) return
  await grantPoints({ userId: pointsTargetUser.value.id, amount: pointsForm.amount, reason: pointsForm.reason || '管理员调整积分' })
  ElMessage.success('积分已调整')
  pointsDialogVisible.value = false
  refresh()
}

onMounted(refresh)
</script>
