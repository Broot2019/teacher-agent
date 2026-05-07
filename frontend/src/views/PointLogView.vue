<template>
  <div class="page-card">
    <h2 class="page-title">积分记录</h2>
    <p class="page-subtitle">查看积分变动明细</p>

    <div style="display:flex; justify-content:flex-end; margin-bottom:16px; gap:12px; align-items:center">
      <el-select v-model="filterType" style="width:160px" clearable placeholder="筛选类型">
        <el-option label="全部" value="" />
        <el-option label="消耗" value="consume" />
        <el-option label="充值/调整" value="grant" />
        <el-option label="退款" value="refund" />
      </el-select>
      <el-button @click="loadLogs" :loading="loading">刷新</el-button>
    </div>

    <el-table :data="filteredLogs" stripe v-loading="loading" style="width:100%">
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="tagType(logType(row))" size="small">{{ typeName(logType(row)) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="变动" width="100" align="center">
        <template #default="{ row }">
          <span :style="{ color: row.changeAmount > 0 ? '#67c23a' : '#f56c6c', fontWeight: 600 }">
            {{ row.changeAmount > 0 ? '+' : '' }}{{ row.changeAmount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="余额" width="100" align="center">
        <template #default="{ row }">
          <span style="font-weight:600">{{ row.balance }}</span>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="200">
        <template #default="{ row }">{{ row.reason }}</template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && filteredLogs.length === 0" style="text-align:center; padding:40px; color:#8c8c8c">
      暂无积分记录
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { myPointLogs } from '@/api/auth'

const logs = ref([])
const loading = ref(false)
const filterType = ref('')

const logType = (row) => {
  if (row.changeAmount < 0) return 'consume'
  if (row.changeAmount > 0 && row.relatedType === 'admin_grant') return 'grant'
  if (row.changeAmount > 0) return 'refund'
  return 'other'
}

const loadLogs = async () => {
  loading.value = true
  try {
    const r = await myPointLogs(200)
    logs.value = r.data || []
  } catch (e) {
    logs.value = []
  } finally { loading.value = false }
}
onMounted(loadLogs)

const filteredLogs = computed(() => {
  if (!filterType.value) return logs.value
  return logs.value.filter(l => logType(l) === filterType.value)
})

const typeName = (t) => ({ consume: '消耗', grant: '充值', refund: '退款', other: '其他' }[t] || t)
const tagType = (t) => ({ consume: 'danger', grant: 'success', refund: 'warning', other: 'info' }[t] || 'info')

const formatTime = (t) => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}
</script>
