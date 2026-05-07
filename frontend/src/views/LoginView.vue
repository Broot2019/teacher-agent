<template>
  <div class="login-bg">
    <div class="auth-split">
      <!-- 左侧品牌区 -->
      <aside class="brand-side">
        <div class="brand-icon">📚</div>
        <h1>教师助手系统</h1>
        <p class="brand-desc">
          基于大模型的智能教学辅助平台<br>
          自动生成符合规范的教案与题库
        </p>
        <ul class="brand-features">
          <li>多家主流大模型一键切换，按需扩展</li>
          <li>智能并行生成，一次出多周教案与多题型题库</li>
          <li>BM25 检索精筛素材，章节内容不重复</li>
          <li>多维质量保障：合规校验 + 批量评审 + 答案均衡</li>
        </ul>
      </aside>

      <!-- 右侧表单 -->
      <section class="auth-form-side">
        <h2>欢迎登录</h2>
        <p class="auth-sub">请输入您的账号密码</p>
        <el-form :model="form" :rules="rules" ref="formRef" label-width="0" size="large" @submit.prevent="onSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" prefix-icon="User" autofocus clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码"
                      prefix-icon="Lock" @keyup.enter="onSubmit" />
          </el-form-item>
          <el-form-item prop="captchaCode">
            <div class="captcha-row">
              <el-input v-model="form.captchaCode" placeholder="请输入验证码"
                        prefix-icon="Picture" maxlength="6" @keyup.enter="onSubmit" />
              <img class="captcha-img" :src="captchaImg" alt="验证码"
                   @click="refreshCaptcha" :title="'点击刷新'" />
            </div>
          </el-form-item>
          <el-form-item>
            <div style="display:flex; flex-direction:column; gap:4px; width:100%">
              <div style="display:flex; justify-content:space-between; align-items:center; width:100%">
                <el-checkbox v-model="form.remember">记住账号密码（7 天免登录）</el-checkbox>
                <router-link to="/forgot-password" class="auth-link" style="font-size:12px">
                  忘记密码？
                </router-link>
              </div>
              <span class="auth-warn-text" v-if="form.remember">⚠ 公共电脑请勿勾选此项</span>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" style="width:100%; height:44px; font-size:15px; letter-spacing:2px"
                       :loading="loading" @click="onSubmit">登 录</el-button>
          </el-form-item>
          <div style="text-align:center; font-size:13px; color:#8c8c8c">
            没有账号？<router-link to="/register" class="auth-link">点击注册</router-link>
          </div>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { getCaptcha } from '@/api/captcha'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const captchaImg = ref('')

const form = reactive({
  username: '',
  password: '',
  remember: false,
  captchaKey: '',
  captchaCode: ''
})
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

// 简单 base64 编解码（避免 dev tools 一眼看到明文，非真正加密）
const encodeCred = (s) => btoa(unescape(encodeURIComponent(s)))
const decodeCred = (s) => {
  try { return decodeURIComponent(escape(atob(s))) } catch (e) { return '' }
}

const refreshCaptcha = async () => {
  try {
    const r = await getCaptcha()
    captchaImg.value = r.data.image
    form.captchaKey = r.data.key
    form.captchaCode = ''  // 刷新后清空旧输入
  } catch (e) {
    captchaImg.value = ''
    form.captchaKey = ''
    // 显式给出可执行的诊断指引（区分网络/后端版本/业务错误）
    ElMessage.error({
      message: '验证码加载失败：' + (e.message || '未知错误'),
      duration: 6000,
      showClose: true
    })
  }
}

// 进入登录页时恢复已记住的账号密码 + 拉验证码
onMounted(() => {
  const saved = localStorage.getItem('remembered_credentials')
  if (saved) {
    try {
      const data = JSON.parse(saved)
      form.username = data.u ? decodeCred(data.u) : ''
      form.password = data.p ? decodeCred(data.p) : ''
      form.remember = true
    } catch (e) {
      localStorage.removeItem('remembered_credentials')
    }
  }
  refreshCaptcha()
})

const onSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const r = await login({
      username: form.username,
      password: form.password,
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    userStore.setLogin(r.data)

    if (form.remember) {
      localStorage.setItem('remembered_credentials', JSON.stringify({
        u: encodeCred(form.username),
        p: encodeCred(form.password)
      }))
    } else {
      localStorage.removeItem('remembered_credentials')
    }

    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // 登录失败时无论原因都刷新验证码（一次性消费）
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.captcha-row {
  display: flex;
  align-items: stretch;
  gap: 8px;
  width: 100%;
}
.captcha-row .el-input {
  flex: 1;
  min-width: 0;
}
.captcha-img {
  flex-shrink: 0;
  width: 130px;
  height: 44px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  background: #fff;
  transition: border-color 0.2s;
  object-fit: cover;
}
.captcha-img:hover {
  border-color: var(--tg-primary);
}
@media (max-width: 480px) {
  .captcha-img { width: 110px; }
}
</style>
