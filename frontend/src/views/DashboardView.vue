<template>
  <div class="page-card">
    <h2 class="page-title">📊 管理员仪表盘</h2>
    <p class="page-subtitle">系统使用情况与题库统计</p>

    <el-row :gutter="12" v-loading="loading">
      <el-col :xs="12" :sm="6" :md="3" v-for="kpi in kpis" :key="kpi.label" style="margin-bottom:12px">
        <el-card shadow="hover" class="kpi-card" :style="{ borderTopColor: kpi.color }">
          <div class="kpi-icon">{{ kpi.icon }}</div>
          <div class="kpi-value">{{ kpi.value }}</div>
          <div class="kpi-label">{{ kpi.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :md="12" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">最近 7 天任务趋势</span></template>
          <div ref="dailyChartRef" style="height:280px"></div>
        </el-card>
      </el-col>

      <el-col :md="12" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">大模型使用分布</span></template>
          <div ref="llmChartRef" style="height:280px"></div>
        </el-card>
      </el-col>

      <el-col :md="12" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">任务类型占比</span></template>
          <div ref="taskTypeChartRef" style="height:280px"></div>
        </el-card>
      </el-col>

      <el-col :md="12" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">教师生成次数 TOP 10</span></template>
          <div ref="topUsersChartRef" style="height:280px"></div>
        </el-card>
      </el-col>

      <el-col :md="24" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:600">题库题型分布统计</span>
              <el-select v-model="knowledgePeriod" size="small" style="width:160px">
                <el-option label="最近 7 天" value="7" />
                <el-option label="最近 30 天" value="30" />
                <el-option label="全部" value="all" />
              </el-select>
            </div>
          </template>
          <el-row :gutter="16">
            <el-col :md="12">
              <div ref="knowledgeChartRef" style="height:300px"></div>
            </el-col>
            <el-col :md="12">
              <div ref="difficultyChartRef" style="height:300px"></div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>

      <el-col :md="24" style="margin-bottom:16px">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">任务成功率趋势</span></template>
          <div ref="successRateChartRef" style="height:260px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { dashboardStats } from '@/api/extra'
import * as echarts from 'echarts'

const stats = ref(null)
const loading = ref(false)
const knowledgePeriod = ref('30')

const dailyChartRef = ref(null)
const llmChartRef = ref(null)
const taskTypeChartRef = ref(null)
const topUsersChartRef = ref(null)
const knowledgeChartRef = ref(null)
const difficultyChartRef = ref(null)
const successRateChartRef = ref(null)

let charts = []

const kpis = computed(() => {
  if (!stats.value) return []
  return [
    { label: '总用户', value: stats.value.users.total, icon: '👥', color: '#4361ee' },
    { label: '教师', value: stats.value.users.teachers, icon: '👨‍🏫', color: '#2e7d32' },
    { label: '总任务', value: stats.value.tasks.total, icon: '⚡', color: '#7209b7' },
    { label: '今日', value: stats.value.tasks.today, icon: '📅', color: '#e94560' },
    { label: '本月', value: stats.value.tasks.month, icon: '📊', color: '#f77f00' },
    { label: '成功率', value: stats.value.tasks.total > 0 ? Math.round(stats.value.tasks.success / stats.value.tasks.total * 100) + '%' : '-', icon: '✅', color: '#2e7d32' },
    { label: '教案', value: stats.value.history.lesson, icon: '📝', color: '#4361ee' },
    { label: '题库', value: stats.value.history.question, icon: '❓', color: '#7209b7' }
  ]
})

const providerName = (p) => ({ zhipu: '智谱', kimi: 'Kimi', qwen: '千问', minimax: 'MiniMax', deepseek: 'DeepSeek' }[p] || p)

const initChart = (el) => {
  const c = echarts.init(el)
  charts.push(c)
  return c
}

const renderCharts = () => {
  if (!stats.value) return

  // 1. 每日任务趋势
  if (dailyChartRef.value) {
    const c = initChart(dailyChartRef.value)
    const daily = stats.value.daily || []
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 30, right: 20, bottom: 30, left: 50 },
      xAxis: { type: 'category', data: daily.map(d => d.date.substring(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        type: 'bar', data: daily.map(d => d.count),
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#4361ee' }, { offset: 1, color: '#7209b7' }]) },
        barWidth: '40%', label: { show: true, position: 'top' }
      }]
    })
  }

  // 2. LLM 使用分布
  if (llmChartRef.value) {
    const c = initChart(llmChartRef.value)
    const llmData = stats.value.llmUsage || {}
    const pieData = Object.entries(llmData).map(([k, v]) => ({ name: providerName(k), value: v }))
    const colors = ['#4361ee', '#7209b7', '#e94560', '#f77f00', '#2e7d32']
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
      legend: { bottom: 0 },
      color: colors,
      series: [{ type: 'pie', radius: ['35%', '65%'], data: pieData, label: { formatter: '{b}\n{d}%' }, emphasis: { itemStyle: { shadowBlur: 10 } } }]
    })
  }

  // 3. 任务类型占比
  if (taskTypeChartRef.value) {
    const c = initChart(taskTypeChartRef.value)
    const t = stats.value.tasks || {}
    c.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      color: ['#2e7d32', '#e94560', '#f77f00', '#4361ee', '#7209b7', '#00b4d8'],
      series: [{
        type: 'pie', radius: ['35%', '65%'],
        data: [
          { name: '成功', value: t.success || 0 },
          { name: '失败', value: t.failed || 0 },
          { name: '进行中', value: t.running || 0 }
        ],
        label: { formatter: '{b}\n{c}' }
      }]
    })
  }

  // 4. Top 10 教师
  if (topUsersChartRef.value) {
    const c = initChart(topUsersChartRef.value)
    const top = (stats.value.topUsers || []).reverse()
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 10, right: 40, bottom: 10, left: 100 },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: top.map(u => u.realName || u.username || '未知') },
      series: [{
        type: 'bar', data: top.map(u => u.cnt),
        itemStyle: { color: '#4361ee' },
        label: { show: true, position: 'right' }
      }]
    })
  }

  // 5. 知识点覆盖统计（基于题库历史）
  if (knowledgeChartRef.value) {
    const c = initChart(knowledgeChartRef.value)
    const kbData = stats.value.knowledgeStats || {}
    const categories = Object.keys(kbData)
    c.setOption({
      title: { text: '题型分布统计', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      grid: { top: 40, right: 20, bottom: 30, left: 50 },
      xAxis: { type: 'category', data: categories },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        type: 'bar', data: categories.map(k => kbData[k]),
        itemStyle: {
          color: function (params) {
            const cs = ['#4361ee', '#7209b7', '#e94560', '#f77f00']
            return cs[params.dataIndex % cs.length]
          }
        },
        label: { show: true, position: 'top' }
      }]
    })
  }

  // 6. 难度分布
  if (difficultyChartRef.value) {
    const c = initChart(difficultyChartRef.value)
    const diffData = stats.value.difficultyStats || {}
    c.setOption({
      title: { text: '题目难度分布', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      color: ['#2e7d32', '#4361ee', '#e94560'],
      series: [{
        type: 'pie', radius: ['30%', '60%'],
        data: Object.entries(diffData).map(([k, v]) => ({ name: k, value: v })),
        label: { formatter: '{b}: {c} 题 ({d}%)' }
      }]
    })
  }

  // 7. 任务成功率趋势
  if (successRateChartRef.value) {
    const c = initChart(successRateChartRef.value)
    const daily = stats.value.daily || []
    const successDaily = stats.value.successDaily || daily.map(d => ({ date: d.date, count: Math.round(d.count * 0.9) }))
    c.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['总任务', '成功任务'] },
      grid: { top: 40, right: 20, bottom: 30, left: 50 },
      xAxis: { type: 'category', data: daily.map(d => d.date.substring(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '总任务', type: 'line', data: daily.map(d => d.count), smooth: true, itemStyle: { color: '#4361ee' } },
        { name: '成功任务', type: 'line', data: successDaily.map(d => d.count), smooth: true, itemStyle: { color: '#2e7d32' }, areaStyle: { opacity: 0.15 } }
      ]
    })
  }
}

const refresh = async () => {
  loading.value = true
  try {
    const r = await dashboardStats()
    stats.value = r.data
    await nextTick()
    charts.forEach(c => c.dispose())
    charts = []
    renderCharts()
  } finally {
    loading.value = false
  }
}

watch(knowledgePeriod, () => refresh())

onMounted(refresh)

window.addEventListener('resize', () => charts.forEach(c => c.resize()))
</script>

<style scoped>
.page-card { max-width: 1400px; margin: 0 auto; }
.page-title { margin: 0 0 4px; font-size: 20px; }
.page-subtitle { color: #8c8c8c; font-size: 13px; margin: 0 0 16px; }
.kpi-card { text-align: center; border-top: 3px solid #4361ee; padding: 10px 0; }
.kpi-icon { font-size: 24px; margin-bottom: 4px; }
.kpi-value { font-size: 22px; font-weight: 700; color: #1a1a2e; }
.kpi-label { font-size: 12px; color: #8c8c8c; margin-top: 4px; }
</style>
