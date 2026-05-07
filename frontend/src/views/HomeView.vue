<template>
  <div>
    <div class="welcome-banner">
      <div class="banner-text">
        <h2>{{ greeting }}，{{ userStore.user?.realName || userStore.user?.username }}</h2>
        <p>欢迎使用教师助手系统 · AI 赋能教学，高效生成教案与题库</p>
      </div>
      <div class="banner-icon">🎓</div>
    </div>

    <el-row :gutter="16" style="margin-top:20px">
      <el-col :xs="24" :sm="12" :md="8" :lg="8" v-for="card in funcCards" :key="card.path" style="margin-bottom:16px">
        <router-link :to="card.path" class="func-card-link">
          <el-card shadow="hover" class="func-card" :style="{ borderTopColor: card.color }">
            <div class="func-icon">{{ card.icon }}</div>
            <h3>{{ card.title }}</h3>
            <p>{{ card.desc }}</p>
          </el-card>
        </router-link>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:8px">
      <el-col :xs="24" :sm="12" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">系统状态</span></template>
          <div class="info-grid">
            <div class="info-item"><span class="info-label">当前模型</span><span class="info-value">{{ activeModelVal }}</span></div>
            <div class="info-item"><span class="info-label">我的积分</span><span class="info-value" style="color:#e94560">{{ userPointsVal }}</span></div>
            <div class="info-item"><span class="info-label">角色</span><span class="info-value">{{ userStore.isAdmin ? '管理员' : '教师' }}</span></div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">使用指南</span></template>
          <div style="font-size:13px; color:#606266; line-height:2">
            <p>1. 在「模型配置」中设置 API Key 并激活模型</p>
            <p>2. 进入「教案生成」选择 <b>按文件配置</b> 模式：每个 PPT 一份独立配置（章节/周次/教案数）</p>
            <p>3. 进入「题库生成」上传 PPT 选择题型与数量</p>
            <p>4. 所有任务后台执行，可在「任务中心」查看进度</p>
            <p style="color:#e94560">📦 文件上限：单文件 ≤ 200MB，单次提交总 ≤ 500MB</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">🎯 提升生成质量的关键设置</span></template>
          <div style="font-size:13px; color:#606266; line-height:1.9">
            <p><b>① 模型选择</b>：质量从高到低推荐 <b>qwen-max / glm-4-plus / deepseek-chat</b> &gt; kimi/minimax；进入「模型配置」修改 modelName。</p>
            <p><b>② 内容等级</b>：日常用 <span style="color:#4361ee">标准版</span>；公开课/示范课用 <span style="color:#7209b7">详尽版</span>（含分层任务）；教学检查/送审用 <span style="color:#e94560">特详版</span>（含分层任务+评价量规+评价细则，10 段教学过程）。</p>
            <p><b>③ 素材质量</b>：上传完整、可读的 PPT/PDF（建议 &gt; 10 页或 2000 字），文件名直接使用章节名（如「第3章 面向对象.pptx」），系统会自动作为默认章节。</p>
            <p><b>④ 章节切分</b>：教案/题库均使用 <b>按文件配置</b> 模式，每章一个文件，避免 LLM 混淆不同章节内容。</p>
            <p><b>⑤ 教案数量</b>：每文件 sessionCount 设为该章实际课时数（4学时/周→2份；2学时/周→1份），后端会自动做 <b>知识点规划</b> 保证多份不重复。</p>
            <p><b>⑥ 题型分布</b>：每章建议至少含 5 单选 + 2 多选 + 3 判断；编程题 1-2 道（编程题对模型要求较高）。</p>
            <p><b>⑦ 难度倾向</b>：日常作业选「混合」让难度梯度合理；专项练习选「困难」；新生入门选「简单」。</p>
            <p><b>⑧ 失败重试</b>：任务失败积分会自动退回。可在「任务中心」点击 <b>重跑</b> 重新生成（重跑也会扣费）。</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { getActiveLlm } from '@/api/llmConfig'
import { me } from '@/api/auth'

const userStore = useUserStore()
const activeModelVal = ref('未配置')
const userPointsVal = ref('-')

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const funcCards = [
  { path: '/lesson-plan', icon: '📝', title: '教案生成', desc: '上传 PPT + 教学计划，AI 自动生成符合模板的 Word 教案', color: '#4361ee' },
  { path: '/question-bank', icon: '❓', title: '题库生成', desc: '上传章节素材，按题型与难度智能生成 Excel 题库', color: '#7209b7' },
  { path: '/knowledge-base', icon: '📚', title: '知识库', desc: '上传教材建立检索索引，生成时自动检索补充内容', color: '#06b6d4' },
  { path: '/qa', icon: '💬', title: '智能答疑', desc: '基于知识库的师生 AI 问答，附文献来源引用', color: '#10b981' },
  { path: '/materials', icon: '📂', title: '资料库', desc: '统一管理章节 PPT/PDF/Word 素材，支持复用', color: '#f59e0b' },
  { path: '/history', icon: '🕐', title: '历史记录', desc: '查看已生成的教案与题库，支持预览、下载和删除', color: '#e94560' }
]

onMounted(async () => {
  if (!userStore.isLogged) return
  try {
    const [modelR, userR] = await Promise.allSettled([getActiveLlm(), me()])
    if (modelR.status === 'fulfilled' && modelR.value.data) {
      activeModelVal.value = modelR.value.data.provider + ' / ' + modelR.value.data.modelName
    }
    if (userR.status === 'fulfilled' && userR.value.data) {
      userPointsVal.value = userR.value.data.points ?? '-'
    }
  } catch (e) {}
})

watch(() => userStore.isLogged, async (logged) => {
  if (!logged) return
  try {
    const [modelR, userR] = await Promise.allSettled([getActiveLlm(), me()])
    if (modelR.status === 'fulfilled' && modelR.value.data) {
      activeModelVal.value = modelR.value.data.provider + ' / ' + modelR.value.data.modelName
    }
    if (userR.status === 'fulfilled' && userR.value.data) {
      userPointsVal.value = userR.value.data.points ?? '-'
    }
  } catch (e) {}
})
</script>

<style scoped>
.welcome-banner {
  background: linear-gradient(135deg, #4361ee 0%, #7209b7 50%, #e94560 100%);
  color: #fff;
  padding: 28px 32px;
  border-radius: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.banner-text h2 { margin: 0 0 8px; font-size: 22px; }
.banner-text p { margin: 0; font-size: 14px; opacity: 0.85; }
.banner-icon { font-size: 56px; }

.func-card-link { text-decoration: none; }

.func-card {
  text-align: center;
  padding: 20px 16px;
  border-top: 3px solid #4361ee;
  cursor: pointer;
  transition: transform 0.2s;
}

.func-card:hover { transform: translateY(-4px); }
.func-icon { font-size: 36px; margin-bottom: 10px; }
.func-card h3 { margin: 0 0 8px; font-size: 16px; color: #1a1a2e; }
.func-card p { margin: 0; font-size: 13px; color: #8c8c8c; line-height: 1.6; }

.info-grid { display: flex; flex-direction: column; gap: 10px; }
.info-item { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #f0f0f0; }
.info-item:last-child { border-bottom: none; }
.info-label { color: #8c8c8c; font-size: 13px; }
.info-value { font-weight: 600; color: #1a1a2e; font-size: 14px; }
</style>
