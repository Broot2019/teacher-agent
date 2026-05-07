package com.teacheragent.service.lessonplan;

/**
 * 教案 / 题库 prompt 的可复用模块。
 *
 * <p>职责：抽出可在多处使用的工具方法，避免 LessonPlanPrompts 与 QuestionBankPrompts
 * 各自维护一套 contentLevelName / truncate 等"看似一样但容易出现漂移"的常量与小函数。
 *
 * <p>注意：本模块刻意不动 SYSTEM_*_TEMPLATE 的整段文本——LLM 对 prompt 字面量的细微差异敏感，
 * 抽取与字符替换可能改变模型输出特性；当前阶段只整合纯工具方法，prompt 字面文本保持原样。
 */
public final class PromptModules {

    private PromptModules() {}

    /** 内容等级中文名 */
    public static String contentLevelName(String level) {
        return switch (nullSafe(level)) {
            case "basic" -> "基础版";
            case "detailed" -> "详尽版";
            case "comprehensive" -> "特详版";
            default -> "标准版";
        };
    }

    /** 教案的等级指令（建议性话术） */
    public static String lessonLevelInstruction(String level) {
        return switch (nullSafe(level)) {
            case "basic" -> "生成适合快速备课的基础教案：目标、重难点和活动描述保持简明，每项以可执行为主，避免长篇理论展开。";
            case "detailed" -> "生成较详尽教案：教学目标、学情分析、活动步骤和反思诊改都要展开说明，突出案例、师生互动、分层指导和课堂组织细节。";
            case "comprehensive" -> "生成特详教案：在详尽教案基础上进一步细化任务驱动、案例演示、学生实操、课堂评价、分层指导和诊改闭环，必须满足直接提交检查的标准。";
            default -> "生成标准教案：内容完整、详略适中，覆盖目标、重难点、学情、教学过程、反思和诊改，适合日常教学使用。";
        };
    }

    /** 题库的等级指令 */
    public static String questionLevelInstruction(String level) {
        return switch (nullSafe(level)) {
            case "basic" -> "题干和解析保持简明，侧重基础概念识记与直接应用。";
            case "detailed" -> "题干可加入代码片段或课堂场景，解析需说明关键知识点和易错点。";
            case "comprehensive" -> "题目应体现综合应用和分层训练，解析需包含思路、易错点和教学提示，编程题答案要给出关键步骤说明。";
            default -> "题目和解析详略适中，覆盖基础理解与课堂应用，适合日常作业使用。";
        };
    }

    /** 教案的字数规范 */
    public static String lessonWordSpec(String level) {
        return switch (nullSafe(level)) {
            case "basic" -> """
                    - 教学目标各 30-60 字（精简至核心要点，每类仅 1 条）
                    - 重难点 15-40 字
                    - 学情分析 15-40 字
                    - 教师/学生活动各 15-50 字
                    - 教学反思 15-40 字，教学诊改 15-40 字
                    """;
            case "detailed" -> """
                    - 教学目标各 80-200 字（展开说明，含具体衡量标准）
                    - 重难点 50-120 字（含常见误区与突破策略）
                    - 学情分析 50-100 字（含分层分析）
                    - 教师/学生活动各 50-150 字（含具体操作步骤和互动细节）
                    - 教学反思 50-120 字（含数据预判），教学诊改 50-120 字
                    - layeredTask 100-200 字
                    """;
            case "comprehensive" -> """
                    - 教学目标各 100-250 字（详尽展开，含具体衡量标准和分层目标）
                    - 重难点 80-180 字（含典型案例、常见误区、突破策略和分层处理）
                    - 学情分析 80-150 字（含分层分析、课前调研结论和学习风格）
                    - 教师/学生活动各 80-200 字（含具体步骤、互动细节、可量化指标）
                    - 教学反思 80-180 字（含参与率/达成率/掉队率数据预判）
                    - 教学诊改 80-180 字（含可量化改进指标）
                    - layeredTask 150-300 字，evaluation 150-300 字
                    - evaluationCriteria 50-120 字
                    """;
            default -> """
                    - 教学目标各 60-150 字
                    - 重难点 30-80 字
                    - 学情分析 30-80 字
                    - 教师/学生活动各 30-100 字
                    - 教学反思 30-80 字，教学诊改 30-80 字
                    """;
        };
    }

    /** 题库的解析字数规范 */
    public static String questionExplanationSpec(String level) {
        return switch (nullSafe(level)) {
            case "basic" -> "解析 20-50 字，仅说明正确答案和直接原因";
            case "detailed" -> "解析 60-120 字，包含关键知识点、易错点分析";
            case "comprehensive" -> "解析 80-180 字，包含解题思路、知识点关联、常见错误分析和教学建议";
            default -> "解析 30-100 字，说明正确原因和关键知识点";
        };
    }

    /** 教案 timeline JSON 模板（按段数生成） */
    public static String timelineSchema(int segments) {
        int total = 80;
        int unit = total / segments;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            int start = i * unit;
            int end = i == segments - 1 ? total : (i + 1) * unit;
            sb.append("        {\"time\": \"").append(start).append("-").append(end)
                    .append("分钟\", \"teacherAction\": \"教师活动具体描述\", \"studentAction\": \"学生对应活动\"}");
            if (i < segments - 1) sb.append(",");
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 教案顶层额外字段 schema */
    public static String topLevelExtraSchema(String level) {
        return switch (nullSafe(level)) {
            case "detailed" -> "\n                  \"layeredTask\": \"分层任务（基础/中等/拔高三档具体任务）\",";
            case "comprehensive" -> """

                                  "layeredTask": "分层任务（基础/中等/拔高三档具体任务）",
                                  "evaluation": "评价量规（含分层评价标准与分值）",""";
            default -> "";
        };
    }

    /** 教案 session 级额外字段 schema */
    public static String sessionExtraSchema(String level) {
        return switch (nullSafe(level)) {
            case "comprehensive" -> "\n                      \"evaluationCriteria\": \"本节课形成性评价方法（含课堂观察+作品评分）\",";
            default -> "";
        };
    }

    /** 教案的等级强制要求 */
    public static String lessonExtraRequirements(String level) {
        return switch (nullSafe(level)) {
            case "basic" -> "";
            case "detailed" -> """

                    【详尽版强制要求】
                    - 重难点必须含【常见误区】与【突破策略】
                    - 学情分析必须含【分层分析】
                    - 必须输出 layeredTask（分层任务）
                    - 教学反思必须含数据预判（如"约 X%% 学生能 ..."）""";
            case "comprehensive" -> """

                    【特详版强制要求】
                    - 重难点必须含【典型案例】+【常见误区】+【突破策略】+【分层处理】
                    - 学情分析必须含【分层分析 + 课前调研结论 + 学习风格预判】
                    - 必须输出 layeredTask（分层任务）和 evaluation（评价量规）
                    - 每次 session 必须输出 evaluationCriteria（评价细则）
                    - 教学反思必须含数据预判（参与率/达成率/掉队率）
                    - 教学诊改必须含可量化改进指标""";
            default -> "";
        };
    }

    /** JSON 合法性硬约束块（教案 / 题库通用） */
    public static String jsonHardConstraint() {
        return """
                ⚠️ JSON 合法性硬约束（违反将导致解析失败）：
                - 字符串值内禁止使用 ASCII 双引号 " ；如需引用请用中文「」或全角""
                - 字符串值内若必须使用 ASCII 双引号，必须用反斜杠转义：\\"
                - 不允许出现未转义的换行；多行内容请用 \\n 表示
                - 严格使用英文逗号、英文冒号、英文方括号/花括号""";
    }

    /** 字符串安全截断（保留与原 truncate 行为一致） */
    public static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n...(已截断)";
    }

    private static String nullSafe(String s) {
        return s == null ? "standard" : s;
    }
}
