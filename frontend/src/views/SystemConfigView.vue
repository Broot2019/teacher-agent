<template>
  <div class="page-card">
    <h2 class="page-title">🔧 系统配置</h2>
    <p class="page-subtitle">配置积分消耗、注册赠送等系统参数</p>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="14">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">积分与配额</span></template>
          <el-form :model="form" label-width="160px" v-loading="loading">
            <el-form-item label="生成教案基础积分">
              <el-input-number v-model="form.lesson_plan_cost" :min="0" :max="999" />
              <span class="cfg-hint">每次任务的固定开销</span>
            </el-form-item>
            <el-form-item label="教案每周增量">
              <el-input-number v-model="form.lesson_plan_range_cost" :min="0" :max="99" />
              <span class="cfg-hint">每多 1 周/份增加的积分</span>
            </el-form-item>
            <el-form-item label="生成题库基础积分">
              <el-input-number v-model="form.question_bank_cost" :min="0" :max="999" />
              <span class="cfg-hint">每次任务的固定开销</span>
            </el-form-item>
            <el-form-item label="题库每题增量">
              <el-input-number v-model="form.question_bank_per_question_cost" :min="0" :max="99" />
              <span class="cfg-hint">每多 1 道题增加的积分；总成本 = 基础 + N × 单题增量</span>
            </el-form-item>
            <el-form-item label="注册赠送积分">
              <el-input-number v-model="form.register_initial_points" :min="0" :max="999999" />
            </el-form-item>
            <el-form-item label="默认月配额">
              <el-input-number v-model="form.default_monthly_quota" :min="0" :max="9999" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="hover" style="margin-top:16px">
          <template #header><span style="font-weight:600">🎯 教案质量保障</span></template>
          <el-form :model="form" label-width="220px" v-loading="loading">
            <el-form-item label="教案合规检查">
              <el-switch v-model="form.lesson_plan_compliance_check_enabled" />
              <span class="cfg-hint">检查必填字段、教学过程段数、字数下限；不合规自动重生</span>
            </el-form-item>
            <el-form-item label="教案逐份自检">
              <el-switch v-model="form.lesson_plan_self_critique_enabled" />
              <span class="cfg-hint">每份教案生成后单独评分；&lt;7 分触发重生（与批量评审互斥）</span>
            </el-form-item>
            <el-form-item label="教案批量评审">
              <el-switch v-model="form.lesson_plan_batch_review_enabled" />
              <span class="cfg-hint">N 份教案一次性批量评分，省约 90% 评审 token；启用后优先于逐份自检</span>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="hover" style="margin-top:16px">
          <template #header><span style="font-weight:600">📝 题库质量保障</span></template>
          <el-form :model="form" label-width="220px" v-loading="loading">
            <el-form-item label="题库逐份自检">
              <el-switch v-model="form.question_bank_self_critique_enabled" />
              <span class="cfg-hint">每批题目自动评分，&lt;7 分重生 1 次</span>
            </el-form-item>
            <el-form-item label="题目语义校验">
              <el-switch v-model="form.question_bank_semantic_validate_enabled" />
              <span class="cfg-hint">批量调 LLM 校验答案字母与选项内容是否真的一致；多 N/10 次低温度调用</span>
            </el-form-item>
            <el-form-item label="单选题答案分布均衡">
              <el-switch v-model="form.question_bank_balance_distribution_enabled" />
              <span class="cfg-hint">检测 A/B/C/D 频次偏差并本地洗牌；零额外调用</span>
            </el-form-item>
            <el-form-item label="题干相似度去重">
              <el-switch v-model="form.question_bank_dedup_enabled" />
              <span class="cfg-hint">Jaccard 相似度 &gt; 0.85 视为重复，本地保留前一题</span>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="hover" style="margin-top:16px">
          <template #header><span style="font-weight:600">💻 编程题校验</span></template>
          <el-form :model="form" label-width="220px" v-loading="loading">
            <el-form-item label="编程题代码编译校验">
              <el-switch v-model="form.program_question_compile_check_enabled" />
              <span class="cfg-hint">编程题答案自动调用对应语言编译器校验，编译失败的题目剔除</span>
            </el-form-item>
            <el-form-item label="编程题运行校验">
              <el-switch v-model="form.program_question_runtime_check_enabled" />
              <span class="cfg-hint">编译通过后再运行；启用时优先按题干样例输入输出比对，无样例则验证不崩溃</span>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="hover" style="margin-top:16px">
          <template #header><span style="font-weight:600">👑 管理员豁免策略</span></template>
          <el-form :model="form" label-width="220px" v-loading="loading">
            <el-form-item label="管理员豁免积分扣减">
              <el-switch v-model="form.admin_skip_points_consume" />
              <span class="cfg-hint">关闭：管理员生成内容也按规则扣积分（默认）；开启：管理员生成不扣积分</span>
            </el-form-item>
            <el-form-item label="管理员豁免月配额">
              <el-switch v-model="form.admin_skip_quota_check" />
              <span class="cfg-hint">开启：管理员不受本月生成次数限制（默认）；关闭：与普通教师同样受配额约束</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onSave" :loading="saving">保存全部配置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="10">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">积分流水</span></template>
          <el-table :data="logs" stripe size="small" max-height="400">
            <el-table-column prop="userId" label="用户" width="60" />
            <el-table-column prop="changeAmount" label="变动" width="70">
              <template #default="{ row }">
                <span :style="{ color: row.changeAmount > 0 ? '#2e7d32' : '#c62828', fontWeight: 600 }">
                  {{ row.changeAmount > 0 ? '+' : '' }}{{ row.changeAmount }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="balance" label="余额" width="70" />
            <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="140" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { systemConfigList, systemConfigSave, pointLogs } from '@/api/admin'

const form = reactive({
  // 积分
  lesson_plan_cost: 10,
  lesson_plan_range_cost: 5,
  question_bank_cost: 5,
  question_bank_per_question_cost: 1,
  register_initial_points: 1000,
  default_monthly_quota: 100,
  // 教案质量
  lesson_plan_compliance_check_enabled: true,
  lesson_plan_self_critique_enabled: true,
  lesson_plan_batch_review_enabled: false,
  // 题库质量
  question_bank_self_critique_enabled: false,
  question_bank_semantic_validate_enabled: false,
  question_bank_balance_distribution_enabled: true,
  question_bank_dedup_enabled: true,
  // 编程题
  program_question_compile_check_enabled: true,
  program_question_runtime_check_enabled: false,
  // 管理员豁免
  admin_skip_points_consume: false,
  admin_skip_quota_check: true
})

const logs = ref([])
const loading = ref(false)
const saving = ref(false)

const BOOL_KEYS = [
  'lesson_plan_compliance_check_enabled',
  'lesson_plan_self_critique_enabled',
  'lesson_plan_batch_review_enabled',
  'question_bank_self_critique_enabled',
  'question_bank_semantic_validate_enabled',
  'question_bank_balance_distribution_enabled',
  'question_bank_dedup_enabled',
  'program_question_compile_check_enabled',
  'program_question_runtime_check_enabled',
  'admin_skip_points_consume',
  'admin_skip_quota_check'
]

const refresh = async () => {
  loading.value = true
  try {
    const [configR, logR] = await Promise.all([systemConfigList(), pointLogs({ limit: 50 })])
    const map = configR.data || {}
    for (const k of Object.keys(form)) {
      if (map[k] === undefined) continue
      if (BOOL_KEYS.includes(k)) {
        form[k] = ['true', '1', 'yes', 'on'].includes(String(map[k]).toLowerCase())
      } else {
        form[k] = parseInt(map[k])
      }
    }
    logs.value = logR.data || []
  } finally { loading.value = false }
}

const onSave = async () => {
  saving.value = true
  try {
    const map = {}
    for (const [k, v] of Object.entries(form)) {
      map[k] = String(v)
    }
    await systemConfigSave(map)
    ElMessage.success('配置已保存')
  } finally { saving.value = false }
}

onMounted(refresh)
</script>

<style scoped>
.cfg-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #8c8c8c;
}
</style>
