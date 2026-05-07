<template>
  <div v-if="$route.meta?.public" class="public-layout">
    <router-view />
  </div>
  <div v-else class="layout-container">
    <header class="app-header">
      <h1>📚 教师助手</h1>
      <router-link to="/" class="nav-item">首页</router-link>
      <router-link v-if="userStore.isAdmin" to="/dashboard" class="nav-item nav-collapsible">仪表盘</router-link>
      <router-link v-if="userStore.isAdmin" to="/llm-config" class="nav-item nav-collapsible">模型</router-link>
      <router-link to="/course-config" class="nav-item nav-collapsible">课程</router-link>
      <router-link to="/lesson-plan" class="nav-item">教案</router-link>
      <router-link to="/question-bank" class="nav-item">题库</router-link>

      <el-dropdown class="nav-collapsible">
        <span class="nav-item">教学资源 ▾</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$router.push('/question-bank-mgmt')">题库管理</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/knowledge-base')">知识库</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/qa')">智能答疑</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/materials')">资料库</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-dropdown class="nav-collapsible">
        <span class="nav-item">我的 ▾</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$router.push('/tasks')">任务中心</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/history')">历史记录</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/point-logs')">积分记录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-dropdown v-if="userStore.isAdmin" class="nav-collapsible">
        <span class="nav-item">管理 ▾</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$router.push('/users')">用户管理</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/invitation')">邀请码</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/system-config')">系统配置</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/audit')">审计日志</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 移动端汉堡菜单按钮（仅 ≤768px 显示） -->
      <button class="nav-burger" @click="mobileMenuOpen = true" aria-label="菜单">☰</button>

      <div class="header-actions">
        <span v-if="activeModel" class="nav-model">🤖 {{ activeModel.provider }} / {{ activeModel.modelName }}</span>
        <span v-if="userPoints !== null" class="nav-points">💰 {{ userPoints }} 积分</span>
        <el-dropdown @command="onUserCommand">
          <span class="user-tag">
            👤 <span class="nav-actions-mobile-hide">{{ userStore.user?.realName || userStore.user?.username }}</span>
            <el-tag :type="userStore.isAdmin ? 'danger' : 'success'" size="small" effect="dark">
              {{ userStore.isAdmin ? '管理员' : '教师' }}
            </el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="changePwd">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <main class="app-body">
      <router-view />
    </main>
    <TaskFloatButton />

    <!-- 移动端折叠菜单 Drawer -->
    <el-drawer v-model="mobileMenuOpen" title="导航菜单" direction="rtl" size="78%">
      <div class="mobile-menu">
        <router-link to="/" class="mobile-menu-item" @click="mobileMenuOpen = false">🏠 首页</router-link>
        <router-link v-if="userStore.isAdmin" to="/dashboard" class="mobile-menu-item" @click="mobileMenuOpen = false">📊 仪表盘</router-link>
        <router-link v-if="userStore.isAdmin" to="/llm-config" class="mobile-menu-item" @click="mobileMenuOpen = false">⚙️ 模型配置</router-link>
        <router-link to="/course-config" class="mobile-menu-item" @click="mobileMenuOpen = false">🎓 课程配置</router-link>
        <router-link to="/lesson-plan" class="mobile-menu-item" @click="mobileMenuOpen = false">📝 教案生成</router-link>
        <router-link to="/question-bank" class="mobile-menu-item" @click="mobileMenuOpen = false">❓ 题库生成</router-link>
        <div class="mobile-menu-section">教学资源</div>
        <router-link to="/question-bank-mgmt" class="mobile-menu-item" @click="mobileMenuOpen = false">题库管理</router-link>
        <router-link to="/knowledge-base" class="mobile-menu-item" @click="mobileMenuOpen = false">知识库</router-link>
        <router-link to="/qa" class="mobile-menu-item" @click="mobileMenuOpen = false">智能答疑</router-link>
        <router-link to="/materials" class="mobile-menu-item" @click="mobileMenuOpen = false">资料库</router-link>
        <div class="mobile-menu-section">我的</div>
        <router-link to="/tasks" class="mobile-menu-item" @click="mobileMenuOpen = false">任务中心</router-link>
        <router-link to="/history" class="mobile-menu-item" @click="mobileMenuOpen = false">历史记录</router-link>
        <router-link to="/point-logs" class="mobile-menu-item" @click="mobileMenuOpen = false">积分记录</router-link>
        <template v-if="userStore.isAdmin">
          <div class="mobile-menu-section">管理</div>
          <router-link to="/users" class="mobile-menu-item" @click="mobileMenuOpen = false">用户管理</router-link>
          <router-link to="/invitation" class="mobile-menu-item" @click="mobileMenuOpen = false">邀请码</router-link>
          <router-link to="/system-config" class="mobile-menu-item" @click="mobileMenuOpen = false">系统配置</router-link>
          <router-link to="/audit" class="mobile-menu-item" @click="mobileMenuOpen = false">审计日志</router-link>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="400px">
      <el-form :model="pwdForm" label-width="100px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.newPassword2" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onChangePwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, provide } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getActiveLlm } from '@/api/llmConfig'
import { changePassword, logout, me } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import TaskFloatButton from '@/components/TaskFloatButton.vue'

const router = useRouter()
const userStore = useUserStore()
const activeModel = ref(null)
const userPoints = ref(null)
const pwdDialogVisible = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', newPassword2: '' })
const mobileMenuOpen = ref(false)

const refreshActive = async () => {
  if (!userStore.isLogged) return
  try {
    const r = await getActiveLlm()
    activeModel.value = r.data
  } catch (e) { activeModel.value = null }
}

const refreshUserInfo = async () => {
  if (!userStore.isLogged) return
  try {
    const r = await me()
    userPoints.value = r.data?.points ?? null
  } catch (e) {}
}

provide('refreshActive', refreshActive)
provide('refreshUserInfo', refreshUserInfo)

const onUserCommand = async (cmd) => {
  if (cmd === 'changePwd') {
    Object.assign(pwdForm, { oldPassword: '', newPassword: '', newPassword2: '' })
    pwdDialogVisible.value = true
  } else if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    try { await logout() } catch (e) {}
    userStore.logout()
    router.push('/login')
    ElMessage.success('已退出')
  }
}

const onChangePwd = async () => {
  if (pwdForm.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (pwdForm.newPassword !== pwdForm.newPassword2) return ElMessage.warning('两次密码不一致')
  await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
  ElMessage.success('密码已更新')
  pwdDialogVisible.value = false
}

onMounted(() => { refreshActive(); refreshUserInfo() })

watch(() => userStore.isLogged, (logged) => {
  if (logged) {
    refreshActive()
    refreshUserInfo()
  } else {
    // 登出后清空顶栏残留信息，避免下一个用户看到前一个用户的状态
    activeModel.value = null
    userPoints.value = null
  }
})
</script>

<style scoped>
.header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  flex-shrink: 0;
}

.nav-model {
  background: rgba(255,255,255,0.1);
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  white-space: nowrap;
}

.nav-points {
  background: rgba(233, 69, 96, 0.2);
  color: #ffd700;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

/* 移动端菜单抽屉项 */
.mobile-menu {
  display: flex;
  flex-direction: column;
}
.mobile-menu-section {
  font-size: 11px;
  color: #909399;
  padding: 16px 12px 6px;
  text-transform: uppercase;
  letter-spacing: 1px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}
.mobile-menu-section:first-of-type {
  border-top: none;
  margin-top: 0;
}
.mobile-menu-item {
  display: block;
  padding: 12px 14px;
  color: #303133;
  text-decoration: none;
  border-radius: 6px;
  font-size: 14px;
  transition: background 0.15s;
}
.mobile-menu-item:hover,
.mobile-menu-item.router-link-active {
  background: #f0f7ff;
  color: #4361ee;
}

@media (max-width: 768px) {
  .header-actions { gap: 8px; }
}
</style>
