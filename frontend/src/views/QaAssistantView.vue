<template>
  <div>
    <el-card shadow="hover" style="margin-bottom:16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">智能答疑助手</span>
          <el-tag size="small" effect="dark" type="warning">AI生成</el-tag>
        </div>
      </template>

      <div class="qa-chat" ref="chatContainer">
        <div v-if="messages.length === 0" class="qa-empty">
          <p style="color:#8c8c8c;font-size:14px">
            请输入您的问题，系统将从知识库中检索相关内容并结合AI进行回答。
          </p>
          <div style="margin-top:12px;display:flex;flex-wrap:wrap;gap:8px">
            <el-tag v-for="q in quickQuestions" :key="q" style="cursor:pointer" @click="question = q" effect="plain">{{ q }}</el-tag>
          </div>
        </div>

        <div v-for="(msg, i) in messages" :key="i" :class="['qa-message', msg.role === 'user' ? 'qa-user' : 'qa-assistant']">
          <div class="qa-role">{{ msg.role === 'user' ? '👤 您' : '🤖 助手' }}</div>
          <div class="qa-content">
            <div style="white-space:pre-wrap;line-height:1.8">{{ msg.content }}</div>
            <div v-if="msg.source" class="qa-source">
              来源：{{ msg.source }} · 模型：{{ msg.model }}
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <div class="qa-input-bar">
      <el-input
        v-model="question"
        placeholder="输入您的问题..."
        @keyup.enter="onAsk"
        :disabled="loading"
        size="large"
      >
        <template #append>
          <el-button type="primary" @click="onAsk" :loading="loading">发送</el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { askQuestion } from '@/api/qa'

const messages = ref([])
const question = ref('')
const loading = ref(false)
const chatContainer = ref(null)

const quickQuestions = [
  '什么是数据可视化？',
  'ECharts 和 D3.js 的区别是什么？',
  '如何设计一个数据大屏？',
  '常见的数据可视化图表类型有哪些？'
]

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) chatContainer.value.scrollTop = chatContainer.value.scrollHeight
}

const onAsk = async () => {
  const q = question.value.trim()
  if (!q) return
  if (loading.value) return

  messages.value.push({ role: 'user', content: q })
  question.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const r = await askQuestion(q)
    messages.value.push({
      role: 'assistant',
      content: r.data.answer,
      source: r.data.source,
      model: r.data.model
    })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '抱歉，服务暂时不可用：' + (e.response?.data?.message || e.message) })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.qa-chat {
  height: 520px;
  overflow-y: auto;
  padding: 8px;
}
.qa-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.qa-message {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
}
.qa-user { align-items: flex-end; }
.qa-assistant { align-items: flex-start; }
.qa-role { font-size: 12px; color: #8c8c8c; margin-bottom: 4px; }
.qa-content {
  max-width: 85%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}
.qa-user .qa-content { background: #4361ee; color: #fff; border-bottom-right-radius: 4px; }
.qa-assistant .qa-content { background: #f5f5f5; color: #1a1a2e; border-bottom-left-radius: 4px; }
.qa-source { font-size: 11px; color: #aaa; margin-top: 6px; }
.qa-input-bar { position: sticky; bottom: 0; }
</style>
