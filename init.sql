-- ============================================
-- 教师助手系统 数据库 v4 初始化脚本
-- MySQL 8.0+
-- 用户名: root  密码: 123456
-- ============================================

CREATE DATABASE IF NOT EXISTS teacher_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE teacher_agent;

-- ============================================
-- 1. 大模型配置表
-- ============================================
DROP TABLE IF EXISTS llm_config;
CREATE TABLE llm_config (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    provider        VARCHAR(32)     NOT NULL                   COMMENT '厂商: zhipu/kimi/minimax/qwen/deepseek',
    api_key         VARCHAR(1024)                              COMMENT 'API Key',
    model_name      VARCHAR(128)                               COMMENT '模型名',
    base_url        VARCHAR(255)                               COMMENT '接口地址(可选)',
    is_active       TINYINT         DEFAULT 0                  COMMENT '是否激活: 0否 1是',
    last_test_status VARCHAR(32)    DEFAULT 'untested'         COMMENT '上次测试状态: success/failed/untested',
    last_test_time  DATETIME                                   COMMENT '上次测试时间',
    last_test_message VARCHAR(1024)                            COMMENT '测试消息',
    max_concurrent   INT             DEFAULT 4                 COMMENT 'LLM 调用最大并发（限流器按 provider 维护信号量）',
    rpm_limit        INT             DEFAULT 60                COMMENT '每分钟最大请求数（保留字段，Phase 2/3 启用滑动窗口）',
    default_max_tokens INT                                      COMMENT 'LLM 调用默认 max_tokens（为空表示不传）',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         DEFAULT 0                  COMMENT '逻辑删除',
    UNIQUE KEY uk_provider (provider, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型配置';

-- 初始化 5 家厂商占位记录（默认全部未激活、未填 key；首次启动后由管理员在 UI 配置）
-- 模型名 + default_max_tokens 已按"方案 A：速度/成本最优"配置，可在 UI 调整
INSERT INTO llm_config (provider, api_key, model_name, base_url, is_active, default_max_tokens) VALUES
('zhipu',    '', 'glm-4-flash',       'https://open.bigmodel.cn/api/paas/v4',              0, NULL),
('kimi',     '', 'moonshot-v1-32k',   'https://api.moonshot.cn/v1',                        0, 4096),
('qwen',     '', 'qwen-plus',         'https://dashscope.aliyuncs.com/compatible-mode/v1', 0, 6000),
('minimax',  '', 'MiniMax-M2.7',      'https://api.minimax.chat/v1',                       0, 6000),
('deepseek', '', 'deepseek-chat',     'https://api.deepseek.com/v1',                       0, 8000);

-- ============================================
-- 2. 用户表
-- ============================================
DROP TABLE IF EXISTS user;
CREATE TABLE `user` (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username        VARCHAR(64)     NOT NULL UNIQUE            COMMENT '用户名',
    password_hash   VARCHAR(255)    NOT NULL                   COMMENT '密码哈希',
    role            VARCHAR(32)     DEFAULT 'teacher'          COMMENT '角色: admin/teacher',
    email           VARCHAR(128)                               COMMENT '邮箱',
    real_name       VARCHAR(64)                                COMMENT '真实姓名',
    status          VARCHAR(32)     DEFAULT 'enabled'          COMMENT '状态: enabled/disabled',
    points          INT             DEFAULT 0                  COMMENT '积分余额',
    monthly_quota   INT             DEFAULT 100                COMMENT '每月最大生成次数(0=不限)',
    last_login_time DATETIME                                   COMMENT '最后登录时间',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         DEFAULT 0                  COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 初始管理员（密码 123456，BCrypt 哈希）
INSERT INTO `user` (username, password_hash, role, real_name, status, points) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin', '系统管理员', 'enabled', 99999);

-- ============================================
-- 3. 教案生成历史表
-- ============================================
DROP TABLE IF EXISTS lesson_plan_history;
CREATE TABLE lesson_plan_history (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    chapter         VARCHAR(255)                               COMMENT '章节',
    week_no         VARCHAR(64)                                COMMENT '周次',
    week_start      INT                                        COMMENT '起始周',
    week_end        INT                                        COMMENT '结束周',
    hours_per_week  INT             DEFAULT 4                  COMMENT '每周学时数 4 或 2',
    package_mode    VARCHAR(32)                                COMMENT '打包模式: single/weekly/full',
    llm_provider    VARCHAR(32)                                COMMENT 'LLM 厂商',
    llm_model       VARCHAR(128)                               COMMENT 'LLM 模型',
    source_files    TEXT                                       COMMENT '源文件列表',
    output_file_path VARCHAR(512)                              COMMENT '生成文件路径',
    output_file_name VARCHAR(255)                              COMMENT '生成文件名',
    status          VARCHAR(32)     DEFAULT 'success'          COMMENT 'success/failed',
    error_msg       TEXT                                       COMMENT '错误信息',
    owner_id        BIGINT                                     COMMENT '所属用户ID',
    task_id         VARCHAR(64)                                COMMENT '关联任务ID',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted         TINYINT         DEFAULT 0                  COMMENT '逻辑删除',
    INDEX idx_create_time (create_time DESC),
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教案生成历史';

-- ============================================
-- 4. 题库生成历史表
-- ============================================
DROP TABLE IF EXISTS question_bank_history;
CREATE TABLE question_bank_history (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    chapter         VARCHAR(255)                               COMMENT '章节',
    question_types  VARCHAR(512)                               COMMENT '勾选的题型 JSON',
    difficulty_dist VARCHAR(512)                               COMMENT '难度分布 JSON',
    total_count     INT                                        COMMENT '总题数',
    llm_provider    VARCHAR(32)                                COMMENT 'LLM 厂商',
    llm_model       VARCHAR(128)                               COMMENT 'LLM 模型',
    source_files    TEXT                                       COMMENT '源文件列表',
    output_file_path VARCHAR(512)                              COMMENT '生成文件路径',
    output_file_name VARCHAR(255)                              COMMENT '生成文件名',
    status          VARCHAR(32)     DEFAULT 'success'          COMMENT 'success/failed',
    error_msg       TEXT                                       COMMENT '错误信息',
    owner_id        BIGINT                                     COMMENT '所属用户ID',
    task_id         VARCHAR(64)                                COMMENT '关联任务ID',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted         TINYINT         DEFAULT 0                  COMMENT '逻辑删除',
    INDEX idx_create_time (create_time DESC),
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库生成历史';

-- ============================================
-- 5. 生成任务表
-- ============================================
DROP TABLE IF EXISTS generation_task;
CREATE TABLE generation_task (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_id         VARCHAR(64)     NOT NULL UNIQUE            COMMENT 'UUID',
    type            VARCHAR(32)     NOT NULL                   COMMENT 'lesson_plan/question_bank',
    owner_id        BIGINT                                     COMMENT '所属用户',
    status          VARCHAR(32)     DEFAULT 'pending'          COMMENT 'pending/running/success/failed/cancelled',
    progress        INT             DEFAULT 0                  COMMENT '进度 0-100',
    stage_text      VARCHAR(255)                               COMMENT '当前阶段描述',
    params_json     TEXT                                       COMMENT '请求参数JSON',
    result_history_id BIGINT                                   COMMENT '成功后的历史记录ID',
    error_msg       TEXT                                       COMMENT '错误信息',
    uploaded_files  TEXT                                       COMMENT '上传文件路径JSON(用于重跑)',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    start_time      DATETIME                                   COMMENT '开始时间',
    finish_time     DATETIME                                   COMMENT '完成时间',
    deleted         TINYINT         DEFAULT 0                  COMMENT '逻辑删除',
    INDEX idx_owner (owner_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务';

-- ============================================
-- 6. 邀请码表
-- ============================================
DROP TABLE IF EXISTS invitation_code;
CREATE TABLE invitation_code (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code            VARCHAR(32)     NOT NULL UNIQUE            COMMENT '邀请码',
    initial_points  INT             DEFAULT 1000               COMMENT '初始积分',
    initial_quota   INT             DEFAULT 100                COMMENT '初始月配额',
    created_by      BIGINT                                     COMMENT '创建人(管理员)',
    used_by         BIGINT                                     COMMENT '使用者ID',
    used_time       DATETIME                                   COMMENT '使用时间',
    expire_time     DATETIME                                   COMMENT '过期时间',
    note            VARCHAR(255)                               COMMENT '备注',
    status          VARCHAR(32)     DEFAULT 'unused'           COMMENT 'unused/used/expired/disabled',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted         TINYINT         DEFAULT 0                  COMMENT '逻辑删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码';

-- ============================================
-- 7. 系统配置表
-- ============================================
DROP TABLE IF EXISTS system_config;
CREATE TABLE system_config (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    config_key      VARCHAR(128)    NOT NULL UNIQUE            COMMENT '配置键',
    config_value    VARCHAR(1024)                              COMMENT '配置值',
    description     VARCHAR(255)                               COMMENT '说明',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- 初始系统配置
INSERT INTO system_config (config_key, config_value, description) VALUES
('lesson_plan_cost',       '10',  '生成教案基础积分（每次任务的固定开销）'),
('lesson_plan_range_cost', '5',   '教案每增加一周/份额外消耗积分'),
('question_bank_cost',     '5',   '生成题库基础积分（每次任务的固定开销）'),
('question_bank_per_question_cost', '1', '题库每多生成 1 道题增加的积分（总成本 = 基础 + N × 单题增量）'),
('register_initial_points','1000','注册赠送初始积分'),
('default_monthly_quota',  '100', '默认月配额'),
-- 教案质量保障开关
('lesson_plan_compliance_check_enabled', 'true',  '教案合规校验（关键字段非空、段数匹配、字数下限）'),
('lesson_plan_self_critique_enabled',    'true',  '教案自检（旧 key，逐份评审；与 batch_review 互斥优先级低）'),
('lesson_plan_batch_review_enabled',     'false', '教案批量评审（true 时一次评 N 份，省约 90% 评审 token）'),
-- 题库质量保障开关
('question_bank_self_critique_enabled',         'false', '题库逐份自检（旧 key，默认关闭）'),
('question_bank_semantic_validate_enabled',     'false', '题目语义校验（answer 字母 vs 选项内容是否匹配，约多 N/10 次低温度调用）'),
('question_bank_balance_distribution_enabled',  'true',  '单选题答案 A/B/C/D 分布均衡（本地洗牌）'),
('question_bank_dedup_enabled',                 'true',  '题干 Jaccard 相似度去重（阈值 0.85）'),
-- 编程题校验
('program_question_compile_check_enabled',      'true',  '编程题编译校验'),
('program_question_runtime_check_enabled',      'false', '编程题运行校验（启用后从 stem 提取样例输入输出比对）'),
-- 管理员豁免策略（与角色权限相关）
('admin_skip_points_consume',                   'false', '管理员是否豁免积分扣减（默认 false：管理员也按规则扣分）'),
('admin_skip_quota_check',                      'true',  '管理员是否豁免月配额限制（默认 true：管理员运维频繁调用不受月配额限制）');

-- ============================================
-- 8. 积分流水表
-- ============================================
DROP TABLE IF EXISTS point_log;
CREATE TABLE point_log (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id         BIGINT          NOT NULL                   COMMENT '用户ID',
    change_amount   INT             NOT NULL                   COMMENT '变动量(正负)',
    balance         INT             NOT NULL                   COMMENT '变动后余额',
    reason          VARCHAR(255)                               COMMENT '原因',
    related_type    VARCHAR(64)                                COMMENT '关联类型',
    related_id      VARCHAR(64)                                COMMENT '关联ID',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

-- ============================================
-- 9. 题目项表（题库明细）
-- ============================================
DROP TABLE IF EXISTS question_item;
CREATE TABLE question_item (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    bank_id         BIGINT          NOT NULL                   COMMENT '所属题库历史ID',
    type            VARCHAR(32)                                COMMENT '题型',
    knowledge       VARCHAR(255)                               COMMENT '知识点',
    stem            TEXT                                       COMMENT '题干',
    difficulty      VARCHAR(16)     DEFAULT '一般'              COMMENT '难度',
    answer          TEXT                                       COMMENT '答案',
    explanation     TEXT                                       COMMENT '解析',
    options_json    TEXT                                       COMMENT '选项JSON',
    sort_order      INT             DEFAULT 0                  COMMENT '排序',
    owner_id        BIGINT                                     COMMENT '所属用户',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted         TINYINT         DEFAULT 0                  COMMENT '逻辑删除',
    INDEX idx_bank (bank_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目项';

-- ============================================
-- 10. 章节资料库表
-- ============================================
DROP TABLE IF EXISTS chapter_material;
CREATE TABLE chapter_material (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    chapter         VARCHAR(255)    NOT NULL                   COMMENT '章节',
    course          VARCHAR(128)    DEFAULT 'Java程序设计基础'  COMMENT '课程名',
    file_name       VARCHAR(255)    NOT NULL                   COMMENT '文件名',
    file_path       VARCHAR(512)    NOT NULL                   COMMENT '文件路径',
    file_size       BIGINT                                     COMMENT '文件大小(字节)',
    file_type       VARCHAR(32)                                COMMENT '文件类型',
    owner_id        BIGINT                                     COMMENT '上传者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    deleted         TINYINT         DEFAULT 0                  COMMENT '逻辑删除',
    INDEX idx_chapter (chapter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节资料库';

-- ============================================
-- 11. 审计日志表
-- ============================================
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id         BIGINT                                     COMMENT '操作人ID',
    username        VARCHAR(64)                                COMMENT '操作人用户名',
    action          VARCHAR(64)     NOT NULL                   COMMENT '操作类型',
    target_type     VARCHAR(64)                                COMMENT '目标类型',
    target_id       VARCHAR(64)                                COMMENT '目标ID',
    detail          TEXT                                       COMMENT '详情',
    status          VARCHAR(32)     DEFAULT 'success'          COMMENT '操作状态',
    error_msg       TEXT                                       COMMENT '错误信息',
    ip              VARCHAR(64)                                COMMENT 'IP地址',
    user_agent      VARCHAR(512)                               COMMENT '浏览器UA',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    INDEX idx_action (action),
    INDEX idx_user (user_id),
    INDEX idx_create_time (create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';
