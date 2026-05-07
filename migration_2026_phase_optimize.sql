-- ============================================
-- 教师助手系统 数据库迁移脚本
-- Phase 1-5 优化（教案/题库生成性能与质量基础设施）
-- 适用：已运行 init.sql 的旧库
-- 兼容：MySQL 5.7 / 8.0+（不依赖 8.0.29 引入的 ADD COLUMN IF NOT EXISTS）
-- 日期：2026-05
-- ============================================

USE teacher_agent;

-- ----------------------------------------
-- llm_config：增加 LLM 调用并发限制与默认 max_tokens
-- 用 INFORMATION_SCHEMA + PREPARE 实现幂等，避免列已存在时报错
-- ----------------------------------------

-- 1. max_concurrent
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'llm_config' AND COLUMN_NAME = 'max_concurrent');
SET @stmt = IF(@col_exists = 0,
    'ALTER TABLE llm_config ADD COLUMN max_concurrent INT DEFAULT 4 COMMENT ''LLM 调用最大并发（限流器按 provider 维护信号量）'' AFTER last_test_message',
    'SELECT ''max_concurrent already exists'' AS msg');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. rpm_limit
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'llm_config' AND COLUMN_NAME = 'rpm_limit');
SET @stmt = IF(@col_exists = 0,
    'ALTER TABLE llm_config ADD COLUMN rpm_limit INT DEFAULT 60 COMMENT ''每分钟最大请求数（保留字段，Phase 2/3 启用滑动窗口）'' AFTER max_concurrent',
    'SELECT ''rpm_limit already exists'' AS msg');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. default_max_tokens
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'llm_config' AND COLUMN_NAME = 'default_max_tokens');
SET @stmt = IF(@col_exists = 0,
    'ALTER TABLE llm_config ADD COLUMN default_max_tokens INT COMMENT ''LLM 调用默认 max_tokens（为空表示不传）'' AFTER rpm_limit',
    'SELECT ''default_max_tokens already exists'' AS msg');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------------------
-- 历史数据回填：为已存在的 provider 设置合理默认
-- ----------------------------------------
UPDATE llm_config SET max_concurrent = 4 WHERE max_concurrent IS NULL;
UPDATE llm_config SET rpm_limit = 60 WHERE rpm_limit IS NULL;

-- ----------------------------------------
-- default_max_tokens 差异化默认（仅对未设置的 provider 设安全上限，避免 max_tokens 触发厂商拒绝）
-- 用户可在「模型配置」UI 按需调整
-- ----------------------------------------
UPDATE llm_config SET default_max_tokens = 4096 WHERE provider = 'kimi'        AND default_max_tokens IS NULL;
UPDATE llm_config SET default_max_tokens = 6000 WHERE provider = 'qwen'        AND default_max_tokens IS NULL;
UPDATE llm_config SET default_max_tokens = 6000 WHERE provider = 'minimax'     AND default_max_tokens IS NULL;
UPDATE llm_config SET default_max_tokens = 8000 WHERE provider = 'deepseek'    AND default_max_tokens IS NULL;
-- zhipu 上下文 128K，留 NULL 不传 max_tokens 让模型用自身默认

-- ----------------------------------------
-- 方案 A：默认模型升级（仅当用户未自定义 model_name 时才覆盖；保留厂商旧默认值的实例升级到新推荐）
-- 用户已在 UI 改过模型的不会被覆盖
-- ----------------------------------------
UPDATE llm_config SET model_name = 'MiniMax-M2.7'
    WHERE provider = 'minimax' AND model_name = 'MiniMax-M2.5-highspeed';

-- ----------------------------------------
-- system_config：补录 Phase 1-5 引入的新配置项（INSERT IGNORE 幂等）
-- ----------------------------------------
INSERT IGNORE INTO system_config (config_key, config_value, description) VALUES
('lesson_plan_compliance_check_enabled', 'true',  '教案合规校验（关键字段非空、段数匹配、字数下限）'),
('lesson_plan_self_critique_enabled',    'true',  '教案自检（旧 key，逐份评审）'),
('lesson_plan_batch_review_enabled',     'false', '教案批量评审（true 时一次评 N 份，省约 90% 评审 token）'),
('question_bank_self_critique_enabled',         'false', '题库逐份自检（旧 key，默认关闭）'),
('question_bank_semantic_validate_enabled',     'false', '题目语义校验（answer 字母 vs 选项内容是否匹配）'),
('question_bank_balance_distribution_enabled',  'true',  '单选题答案 A/B/C/D 分布均衡（本地洗牌）'),
('question_bank_dedup_enabled',                 'true',  '题干 Jaccard 相似度去重（阈值 0.85）'),
('question_bank_per_question_cost',             '1',     '题库每多生成 1 道题增加的积分（总成本 = 基础 + N × 单题增量）'),
('program_question_compile_check_enabled',      'true',  '编程题编译校验'),
('program_question_runtime_check_enabled',      'false', '编程题运行校验（启用后从 stem 提取样例输入输出比对）'),
('admin_skip_points_consume',                   'false', '管理员是否豁免积分扣减（默认 false：管理员也按规则扣分）'),
('admin_skip_quota_check',                      'true',  '管理员是否豁免月配额限制（默认 true：管理员运维频繁调用不受月配额限制）');

-- ----------------------------------------
-- course_config：增加 programming_language 列（编程题多语言支持）
-- ----------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_config' AND COLUMN_NAME = 'programming_language');
SET @stmt = IF(@col_exists = 0,
    'ALTER TABLE course_config ADD COLUMN programming_language VARCHAR(32) DEFAULT NULL COMMENT ''编程语言（仅程序设计类课程使用）'' AFTER class_name',
    'SELECT ''programming_language exists'' AS msg');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ============================================
-- 验证
-- ============================================
-- SELECT id, provider, max_concurrent, rpm_limit, default_max_tokens FROM llm_config;
-- SELECT config_key, config_value FROM system_config WHERE config_key LIKE 'lesson_plan%' OR config_key LIKE 'question_bank%' OR config_key LIKE 'program_question%';
