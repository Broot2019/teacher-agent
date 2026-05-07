<template>
  <div class="login-bg">
    <div class="auth-split">
      <!-- 左侧品牌区 -->
      <aside class="brand-side">
        <div class="brand-icon">🎓</div>
        <h1>教师注册</h1>
        <p class="brand-desc">
          凭邀请码加入<br>
          开启 AI 辅助教学之旅
        </p>
        <ul class="brand-features">
          <li>注册即赠教学积分，可立即体验</li>
          <li>支持单章节、范围批量、按文件三种生成模式</li>
          <li>自动两阶段规划，多份教案知识点不重复</li>
          <li>按文件配置题型分布，章节互不混淆</li>
        </ul>
      </aside>

      <!-- 右侧表单 -->
      <section class="auth-form-side">
        <h2>创建账号</h2>
        <p class="auth-sub">填写信息完成注册</p>
        <el-form :model="form" :rules="rules" ref="formRef" label-width="0" size="large">
          <el-form-item prop="invitationCode">
            <el-input v-model="form.invitationCode" placeholder="请输入邀请码" prefix-icon="Ticket" clearable />
          </el-form-item>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名（3-32位）" prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码（至少6位）" prefix-icon="Lock" />
            <div class="pw-strength">
              <div :class="['pw-strength-bar', strength >= 1 ? strengthClass : '']"></div>
              <div :class="['pw-strength-bar', strength >= 2 ? strengthClass : '']"></div>
              <div :class="['pw-strength-bar', strength >= 3 ? strengthClass : '']"></div>
            </div>
            <div class="pw-strength-label" v-if="form.password">{{ strengthLabel }}</div>
          </el-form-item>
          <el-form-item prop="password2">
            <el-input v-model="form.password2" type="password" show-password placeholder="请再次输入密码" prefix-icon="Lock" />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item>
                <el-input v-model="form.realName" placeholder="真实姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                <el-input v-model="form.email" placeholder="邮箱（选填）" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item>
            <el-button type="primary" style="width:100%; height:44px; font-size:15px; letter-spacing:2px"
                       :loading="loading" @click="onSubmit">注 册</el-button>
          </el-form-item>
          <div style="text-align:center; font-size:13px; color:#8c8c8c">
            已有账号？<router-link to="/login" class="auth-link">立即登录</router-link>
          </div>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ invitationCode: '', username: '', password: '', password2: '', realName: '', email: '' })
const rules = {
  invitationCode: [{ required: true, message: '请输入邀请码', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 3, max: 32, message: '长度 3-32', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '至少 6 位', trigger: 'blur' }],
  password2: [{ validator: (r, v, cb) => v === form.password ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' }]
}

// 密码强度评估：长度 + 字符类多样性
const strength = computed(() => {
  const p = form.password || ''
  if (p.length < 6) return 0
  let score = 0
  if (p.length >= 6) score++
  if (p.length >= 10) score++
  let typeCnt = 0
  if (/[a-z]/.test(p)) typeCnt++
  if (/[A-Z]/.test(p)) typeCnt++
  if (/\d/.test(p)) typeCnt++
  if (/[^a-zA-Z0-9]/.test(p)) typeCnt++
  if (typeCnt >= 3) score++
  return Math.min(3, score)
})
const strengthClass = computed(() => {
  if (strength.value <= 1) return 'active-weak'
  if (strength.value === 2) return 'active-medium'
  return 'active-strong'
})
const strengthLabel = computed(() => {
  if (strength.value <= 1) return '密码强度：弱'
  if (strength.value === 2) return '密码强度：中等'
  return '密码强度：强'
})

const onSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const r = await register({
      invitationCode: form.invitationCode.trim(),
      username: form.username,
      password: form.password,
      realName: form.realName,
      email: form.email
    })
    userStore.setLogin(r.data)
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>
