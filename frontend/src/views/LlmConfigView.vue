<template>
  <div class="page-card">
    <h2 class="page-title">⚙️ 大模型配置</h2>
    <p class="page-subtitle">配置大模型 API Key 与模型名，测试连通后激活为默认模型</p>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="8" v-for="cfg in configs" :key="cfg.provider" style="margin-bottom:16px">
        <el-card shadow="hover" :class="{ 'active-card': cfg.isActive === 1 }" class="provider-card">
          <template #header>
            <div style="display:flex; align-items:center; justify-content:space-between">
              <div style="display:flex; align-items:center; gap:8px">
                <span class="provider-icon">{{ providerIcon(cfg.provider) }}</span>
                <span style="font-weight:600; font-size:15px">{{ providerName(cfg.provider) }}</span>
              </div>
              <el-tag v-if="cfg.isActive === 1" type="success" effect="dark" size="small">激活</el-tag>
              <el-tag v-else-if="cfg.lastTestStatus === 'success'" type="primary" size="small">已测试</el-tag>
              <el-tag v-else-if="cfg.lastTestStatus === 'failed'" type="danger" size="small">失败</el-tag>
              <el-tag v-else type="info" size="small">未测试</el-tag>
            </div>
          </template>

          <el-form label-width="80px" size="small">
            <el-form-item label="API Key">
              <el-input v-model="cfg.apiKey" type="password" show-password placeholder="请输入" />
            </el-form-item>
            <el-form-item label="模型名">
              <el-input v-model="cfg.modelName" :placeholder="defaultModelHint(cfg.provider)" />
            </el-form-item>
            <el-form-item label="接口地址">
              <el-input v-model="cfg.baseUrl" />
            </el-form-item>
          </el-form>

          <div v-if="cfg.lastTestMessage" class="test-message" :class="cfg.lastTestStatus">
            {{ cfg.lastTestMessage }}
          </div>

          <div style="display:flex; gap:6px; margin-top:12px">
            <el-button type="primary" plain size="small" @click="onSave(cfg)">保存</el-button>
            <el-button plain size="small" @click="onTest(cfg)" :loading="testingMap[cfg.provider]">测试</el-button>
            <el-button type="success" size="small" @click="onActivate(cfg)" :disabled="cfg.isActive === 1 || !cfg.apiKey">激活</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { listLlm, saveLlm, testLlm, activateLlm } from '@/api/llmConfig'

const configs = ref([])
const testingMap = reactive({})
const refreshActive = inject('refreshActive')

const providerName = (p) => ({
  zhipu: '智谱 GLM', kimi: 'Kimi', qwen: '通义千问',
  minimax: 'MiniMax', deepseek: 'DeepSeek'
}[p] || p)

const providerIcon = (p) => ({
  zhipu: '🟣', kimi: '🌙', qwen: '☁️',
  minimax: '🟠', deepseek: '🐋'
}[p] || '🤖')

const defaultModelHint = (p) => ({
  zhipu: 'glm-4-flash / glm-4-plus',
  kimi: 'moonshot-v1-32k',
  qwen: 'qwen-plus / qwen-max',
  minimax: 'abab6.5s-chat',
  deepseek: 'deepseek-chat'
}[p] || '')

const refresh = async () => {
  const r = await listLlm()
  configs.value = r.data
}

const onSave = async (cfg) => {
  await saveLlm({ provider: cfg.provider, apiKey: cfg.apiKey, modelName: cfg.modelName, baseUrl: cfg.baseUrl })
  ElMessage.success('保存成功')
  await refresh()
}

const onTest = async (cfg) => {
  testingMap[cfg.provider] = true
  try {
    await onSave(cfg)
    const r = await testLlm(cfg.provider)
    if (r.data.success) ElMessage.success(`连通成功 (${r.data.latencyMs}ms)`)
    else ElMessage.error(`连接失败: ${r.data.message}`)
    await refresh()
  } finally {
    testingMap[cfg.provider] = false
  }
}

const onActivate = async (cfg) => {
  await activateLlm(cfg.provider)
  ElMessage.success(`已激活 ${providerName(cfg.provider)}`)
  await refresh()
  if (refreshActive) refreshActive()
}

onMounted(refresh)
</script>

<style scoped>
.provider-card { transition: transform 0.2s; }
.provider-card:hover { transform: translateY(-2px); }
.provider-icon { font-size: 20px; }
</style>
