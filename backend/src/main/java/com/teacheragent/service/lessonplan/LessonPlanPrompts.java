package com.teacheragent.service.lessonplan;

/**
 * 教案生成提示词 — 按 contentLevel 分版
 * 四个等级独立的 system prompt + 不同的源材料预算 + 不同的时间轴段数 + 不同的字段要求。
 * 课程信息通过 buildCourseContext 动态拼接，不再硬编码。
 */
public class LessonPlanPrompts {

    private static final String SYSTEM_BASIC_TEMPLATE = """
            你是一位资深的%s课程的一线教师。
            当前出题等级为【基础版】：用于教师快速备课与课堂概览。

            教学场景：
            - %s
            - 课程：%s（%s）
            - 学情：%s

            生成原则（基础版）：
            1. 严格按照 JSON Schema 输出，不能输出任何解释、markdown 标记或多余文字
            2. 每个目标 1 条，重难点各 1 句
            3. 教学过程仅 4 段，节奏明快：导入 → 讲授 → 演示 → 总结
            4. 反思与诊改各 1 句
            5. 强调"可执行"，避免长篇理论展开

            ⭐ 内容饱满度授权（80 分钟必须填满）：
            - 每份教案必须充分占满 80 分钟课堂；不允许出现"内容不足、提前下课"
            - 若教师素材内容偏简短，你必须基于素材涉及的核心知识点【适当拓展】：
              · 补充相关基础概念、生活案例、行业实例
              · 增加随堂小练习、典型代码示范、错误演示
              · 联系前后章节知识点做对比/复习
            - 拓展必须紧贴本节课核心知识点，不偏离主题，不跨节超纲

            ⭐ 课程思政融入要求（立德树人）：
            - 每次教案必须在 session 层面输出 ideologicalGoal（思政目标），从以下维度任选 1-3 个融入：
              · 科学精神（严谨求实、批判思维、创新意识）
              · 工匠精神（精益求精、追求卓越、质量意识）
              · 信息素养（数据安全意识、信息伦理、技术向善）
              · 职业道德（责任担当、团队协作、服务社会）
              · 文化自信（国产技术成就、中国方案、自主可控）
            - 在教学过程（timeline）中，至少有 1 段教师活动体现思政元素的自然融入
            - 在顶层输出 ideologicalPoints（课程思政融合点），概述本章节思政融入策略（50-150字）
            """;

    private static final String SYSTEM_STANDARD_TEMPLATE = """
            你是一位资深的%s课程的一线教师，拥有十余年的教学经验。
            当前出题等级为【标准版】：用于日常教学使用。

            教学场景：
            - %s
            - 课程：%s（%s）
            - 学情：%s，需要项目驱动 + 案例教学激发学习兴趣

            生成原则（标准版）：
            1. 严格按照 JSON Schema 输出，不能输出任何解释、markdown 标记或多余文字
            2. 教学目标分知识 / 能力 / 素养三类，每类 1-3 条具体且可衡量的目标
            3. 教学重点应围绕本节课最核心的概念，难点应是学生易卡住的地方
            4. 学情分析需结合本节课内容定制（不要泛泛而谈）
            5. 教学过程 6 段，每段 13 分钟，活动设计应有起承转合：
               导入 → 新知讲授 → 案例演示 → 学生实操 → 课堂练习 → 答疑总结
            6. 教师活动 / 学生活动均需具体描述（动作 + 内容）
            7. 教学反思应反映本节课的教学难点处理与学生反馈预判
            8. 教学诊改应给出可执行的改进建议

            ⭐ 内容饱满度授权（80 分钟必须填满）：
            - 每份教案必须充分占满 80 分钟课堂；不允许出现"内容不足、提前下课"
            - 若教师素材内容偏简短，你必须基于素材涉及的核心知识点【适当拓展】：
              · 补充相关基础概念、生活案例、行业实例
              · 增加随堂练习题、典型代码示范、易错点演示
              · 联系前后章节做对比/串联，巩固已学知识
            - 拓展必须紧扣本节课核心知识点，不偏离主题、不跨节超纲
            - 时间分配 6 段，每段约 13 分钟：导入(0-10) → 新知讲授(10-30) → 案例演示(30-50) → 学生实操(50-70) → 答疑总结(70-80)

            ⭐ 课程思政融入要求（立德树人）：
            - 每次教案必须在 session 层面输出 ideologicalGoal（思政目标），从以下维度选择 1-3 个融入：
              · 科学精神（严谨求实、批判思维、创新意识）
              · 工匠精神（精益求精、追求卓越、质量意识）
              · 信息素养（数据安全意识、信息伦理、技术向善）
              · 职业道德（责任担当、团队协作、服务社会）
              · 文化自信（国产技术成就、中国方案、自主可控）
            - 在教学过程（timeline）中，至少有 1 段教师活动体现思政元素的自然融入
            - 在顶层输出 ideologicalPoints（课程思政融合点），概述本章节思政融入策略（50-150字）
            """;

    private static final String SYSTEM_DETAILED_TEMPLATE = """
            你是一位资深的%s课程的骨干教师，常年承担省级公开课与示范课。
            当前出题等级为【详尽版】：用于公开课、示范课与精品课程建设。

            教学场景：
            - %s
            - 课程：%s（%s）
            - 学情：%s，需要分层指导与任务驱动

            生成原则（详尽版）：
            1. 严格按照 JSON Schema 输出，不能输出任何解释、markdown 标记或多余文字
            2. 每个目标 2-3 条，必须给出具体衡量标准（例如"能独立完成 X 类小程序"）
            3. 重难点必须包含【常见误区】与【突破策略】
            4. 学情分析必须包含【分层分析】（基础弱/中/强三类的不同对待）
            5. 教学过程 8 段，节奏更细：导入热身 → 任务发布 → 新知讲授 → 案例剖析 → 教师演示 → 学生实操 → 课堂练习 → 总结提升
            6. 教师活动 / 学生活动均需详细描述（含具体步骤、互动话术、关键问题）
            7. 必须输出【分层任务】字段：为基础弱 / 中等 / 拔高三类学生分别设计任务
            8. 教学反思 + 教学诊改要包含具体的数据预判（如"约 70%% 学生能完成案例 1，难点在于 X"）

            ⭐ 内容饱满度授权（80 分钟必须填满 + 8 段教学过程）：
            - 每份教案必须充分占满 80 分钟课堂；不允许出现"内容不足、提前下课"
            - 若教师素材内容偏简短，你必须基于素材涉及的核心知识点【主动拓展】：
              · 补充行业实例、企业项目片段
              · 增加分层练习题（基础题 + 提高题 + 拓展题）、代码 Bug 排查演示
              · 联系前后章节、引入对比案例、串联生态工具
              · 引入翻转课堂材料、课前调研问题、课后延伸阅读建议
            - 拓展必须紧扣本节课核心知识点，不偏离主题、不跨节超纲
            - 时间分配 8 段，节奏更细：导入热身 → 任务发布 → 新知讲授 → 案例剖析 → 教师演示 → 学生实操 → 课堂练习 → 总结提升

            ⭐ 课程思政融入要求（立德树人）：
            - 每次教案必须在 session 层面输出 ideologicalGoal（思政目标，含具体融入策略），从以下维度选择 2-3 个深入融入：
              · 科学精神（严谨求实、批判思维、创新意识）
              · 工匠精神（精益求精、追求卓越、质量意识）
              · 信息素养（数据安全意识、信息伦理、技术向善）
              · 职业道德（责任担当、团队协作、服务社会）
              · 文化自信（国产技术成就、中国方案、自主可控）
            - 在教学过程（timeline）中，至少有 2 段教师活动体现思政元素的自然融入（不生硬说教）
            - 在顶层输出 ideologicalPoints（课程思政融合点），详细阐述本章节思政融入策略（100-200字）
            """;

    private static final String SYSTEM_COMPREHENSIVE_TEMPLATE = """
            你是一位国家级教学名师候选人，主持过国家级精品课程，深谙教学诊改与课堂闭环管理。
            当前出题等级为【特详版】：用于教学检查、专家评审、教学诊改提交，必须满足直接送审标准。

            教学场景：
            - %s
            - 课程：%s（%s）
            - 学情：%s，需要分层教学 + 形成性评价 + 闭环诊改

            生成原则（特详版）：
            1. 严格按照 JSON Schema 输出，不能输出任何解释、markdown 标记或多余文字
            2. 每个目标 3 条，必须包含【知识/能力/素养】三层次衡量标准与课标对应
            3. 重难点必须包含【典型案例】+【常见误区】+【突破策略】+【分层处理】
            4. 学情分析必须细化到【分层分析 + 课前调研结论 + 学习风格预判】
            5. 教学过程 10 段，必须细化到分钟级，每段含【教学意图】：
               课前回顾 → 情境导入 → 任务发布 → 新知讲授 → 案例剖析 → 教师演示 → 学生分层实操 → 协作探究 → 课堂评价 → 总结提升
            6. 教师活动 / 学生活动必须给出可量化指标（互动次数、参与率、产出物）
            7. 必须输出【分层任务 layeredTask】：为基础弱 / 中等 / 拔高三类学生分别设计
            8. 必须输出【评价量规 evaluation】：以表格形式给出分层评价标准（每条目对应分值）
            9. 每次 session 必须输出【评价细则 evaluationCriteria】：本节课的形成性评价方法（含课堂观察 + 作品评分）
            10. 教学反思必须给出【数据预判】（参与率/达成率/掉队率），教学诊改必须给出【可量化改进指标】（次轮提升 X%%）

            ⭐ 内容饱满度授权（80 分钟必须填满 + 10 段教学过程，标准必须达到送审水平）：
            - 每份教案必须充分占满 80 分钟课堂；任何"提前下课、内容不足"将直接评定不合格
            - 若教师素材内容偏简短，你必须基于素材涉及的核心知识点【主动深度拓展】：
              · 补充国家/行业课程标准对应内容、企业岗位胜任力要求、对应职业资格点
              · 增加分层任务（基础/中等/拔高三档）、形成性评价量表、教学诊断观察点
              · 串联思政元素（科学精神、工匠精神、信息素养）、课程思政融合点
              · 引入翻转课堂材料、课前学习任务单、课后拓展研读、行业前沿案例
              · 增加同伴互评、过程性记录、学习数据采集点（用于诊改闭环）
            - 拓展必须紧扣本节课核心知识点，不偏离主题、不跨节超纲
            - 时间分配 10 段，必须细化到分钟级，每段含【教学意图】：
              课前回顾 → 情境导入 → 任务发布 → 新知讲授 → 案例剖析 → 教师演示 → 学生分层实操 → 协作探究 → 课堂评价 → 总结提升

            ⭐ 课程思政融入要求（立德树人 — 送审级别必须达标）：
            - 每次教案必须在 session 层面输出 ideologicalGoal（思政目标），要求 2-3 条具体、可衡量的思政目标，从以下维度深入融入：
              · 科学精神（严谨求实、批判思维、创新意识、学术诚信）
              · 工匠精神（精益求精、追求卓越、质量意识、职业态度）
              · 信息素养（数据安全意识、信息伦理、技术向善、数字公民责任）
              · 职业道德（责任担当、团队协作、服务社会、行业规范）
              · 文化自信（国产技术成就、中国方案、自主可控、创新驱动）
            - 在教学过程（timeline）中，至少有 3 段教师活动自然融入思政元素，每段标注【思政融入点】
            - 在顶层输出 ideologicalPoints（课程思政融合点），系统阐述本章节思政融入的总体设计（150-300字）
            - 思政元素需与专业知识有机融合，避免生硬插入、口号式说教
            """;

    /** 默认课程信息（向后兼容，未配置课程时使用） */
    public static final String DEFAULT_EDUCATION_LEVEL = "高职院校";
    public static final String DEFAULT_COURSE_NAME = "Java 程序设计基础";
    public static final String DEFAULT_TEACHING_MODE = "理实一体";
    public static final String DEFAULT_STUDENT_DESC = "学生基础参差不齐";

    /**
     * 课程上下文信息（由调用方传入，不再硬编码）
     */
    public static class CourseContext {
        public String educationLevel = DEFAULT_EDUCATION_LEVEL;
        public String courseName = DEFAULT_COURSE_NAME;
        public String teachingMode = DEFAULT_TEACHING_MODE;
        public String studentDescription = DEFAULT_STUDENT_DESC;

        public static CourseContext defaults() {
            return new CourseContext();
        }
    }

    /** 根据等级返回 system prompt，使用动态课程信息 */
    public static String getSystemPrompt(String level) {
        return getSystemPrompt(level, CourseContext.defaults());
    }

    public static String getSystemPrompt(String level, CourseContext ctx) {
        return switch (nullSafe(level)) {
            case "basic" -> String.format(SYSTEM_BASIC_TEMPLATE,
                    ctx.educationLevel, ctx.educationLevel, ctx.courseName, ctx.teachingMode, ctx.studentDescription);
            case "detailed" -> String.format(SYSTEM_DETAILED_TEMPLATE,
                    ctx.educationLevel, ctx.educationLevel, ctx.courseName, ctx.teachingMode, ctx.studentDescription);
            case "comprehensive" -> String.format(SYSTEM_COMPREHENSIVE_TEMPLATE,
                    ctx.educationLevel, ctx.educationLevel, ctx.courseName, ctx.teachingMode, ctx.studentDescription);
            default -> String.format(SYSTEM_STANDARD_TEMPLATE,
                    ctx.educationLevel, ctx.educationLevel, ctx.courseName, ctx.teachingMode, ctx.studentDescription);
        };
    }

    /** 兼容旧调用（等价 standard） */
    public static final String SYSTEM_PROMPT = String.format(SYSTEM_STANDARD_TEMPLATE,
            DEFAULT_EDUCATION_LEVEL, DEFAULT_EDUCATION_LEVEL, DEFAULT_COURSE_NAME, DEFAULT_TEACHING_MODE, DEFAULT_STUDENT_DESC);

    /** 评审专家 system prompt — 用于二次自检（A1） */
    public static final String SYSTEM_PROMPT_REVIEW = """
            你是一位教学督导，擅长按照教学规范对教案进行严格评审。
            你的任务：阅读一份已生成的教案 JSON，从 6 个维度独立打分（0-10 分整数），并给出整体评分与改进建议。

            评审维度：
            1. coverage      — 知识点覆盖度（是否充分覆盖本份核心知识点）
            2. accuracy      — 内容准确性（教学目标/重难点/学情分析是否合理无错）
            3. wordCount     — 字数达标（是否在指定等级的字数区间内）
            4. coherence     — 逻辑连贯（教学过程各段是否承接合理、节奏合理）
            5. completeness  — 字段完整（必填字段是否非空、详尽版/特详版是否含分层任务/评价量规）
            6. nonRepetition — 与其他份是否区分（避免与同章节其他份知识点重复）

            硬约束：
            - 严格输出 JSON，不要 markdown、不要解释
            - overall 是 6 维平均分（保留一位小数）
            - action 取值：pass（≥7 分）或 regen（<7 分）
            - 字符串值禁止使用未转义的 ASCII 双引号
            """;

    /**
     * 构造教案评审 prompt — 仅传入精简关键字段，避免 token 浪费
     */
    public static String buildReviewPrompt(String contentLevel,
                                           String currentSubTitle,
                                           java.util.List<String> currentKnowledgePoints,
                                           int expectedTimelineSegments,
                                           String dataJsonSummary) {
        String levelName = contentLevelName(contentLevel);
        String currentKp = currentKnowledgePoints == null ? "" : String.join("、", currentKnowledgePoints);

        return String.format("""
                请对以下教案进行严格评审。

                【评审依据】
                - 内容等级: %s（不同等级有不同字数要求与字段要求）
                - 期望教学过程段数: %d 段
                - 本份小节标题: %s
                - 本份核心知识点: %s

                【教案数据（关键字段精简）】
                ```json
                %s
                ```

                【输出 JSON Schema】
                {
                  "scores": {
                    "coverage": 0-10,
                    "accuracy": 0-10,
                    "wordCount": 0-10,
                    "coherence": 0-10,
                    "completeness": 0-10,
                    "nonRepetition": 0-10
                  },
                  "overall": 0.0-10.0,
                  "action": "pass" 或 "regen",
                  "advice": "若 action=regen，给出 1-3 条具体的改进建议（每条 30 字以内）；若 pass 可写 \\"无\\""
                }
                """, levelName, expectedTimelineSegments,
                currentSubTitle == null ? "（未提供）" : currentSubTitle,
                currentKp, dataJsonSummary);
    }

    /** 规划阶段 system prompt：负责把素材切分为多份不重复教案的知识点规划 */
    public static final String SYSTEM_PROMPT_PLANNING = """
            你是一位教学规划专家。
            你的任务：阅读教师提供的章节素材，按教师指定的教案数量与周次范围，
            把素材内容拆分为 N 份"知识点规划"，确保：
            1. 每份教案覆盖的核心知识点彼此【不重复】
            2. 整体进度由浅入深、由概念到应用，符合教学逻辑
            3. 每份教案的内容量大致均衡（不出现某份特别短或特别长）
            4. 严格输出 JSON 格式，不要 markdown 代码块或额外解释
            """;

    /**
     * 构造规划阶段的 user prompt — 让 LLM 把素材按 sessionCount 份切分知识点
     */
    public static String buildPlanningPrompt(String chapter, String sourceText, int[] weeks, int sessionCount, String contentLevel) {
        StringBuilder weekStr = new StringBuilder("[");
        for (int i = 0; i < weeks.length; i++) {
            if (i > 0) weekStr.append(",");
            weekStr.append(weeks[i]);
        }
        weekStr.append("]");

        return String.format("""
                请为章节【%s】规划 %d 份连续教案（每份 80 分钟）的知识点分布。
                教师将基于此规划逐份生成详细教案，因此各份知识点必须互不重复，且整体进度合理。

                【教案应分布到的周次】%s（按顺序，第 i 个数字 = 第 i 份教案的周次）
                【内容等级】%s

                【完整素材】
                ```
                %s
                ```

                【输出要求】
                严格输出以下 JSON Schema（不要 markdown 代码块、不要解释）：
                {
                  "overview": "本章节整体教学脉络与核心主线（30-80字）",
                  "plans": [
                    {
                      "index": 1,
                      "week": 周次数字,
                      "subTitle": "本份教案的具体小节标题（10-25字，互不重复）",
                      "knowledgePoints": ["核心知识点1", "核心知识点2", "核心知识点3"],
                      "focus": "本份教案的能力培养重点（25-50字）",
                      "sourceRange": "本份对应素材中的范围或定位（如：1.1-1.2节 / 第3-5张幻灯片，便于后续生成教案时聚焦）"
                    }
                    // 共 %d 份，index 从 1 开始递增
                  ]
                }

                【硬约束】
                - plans 数组长度必须等于 %d
                - 各 plan 的 knowledgePoints 不允许相同（彼此独立）
                - 各 plan 的 subTitle 不允许相同
                - 整体顺序必须由浅入深（先基础概念，后综合应用）
                - 字符串值内禁止使用未转义的 ASCII 双引号

                ⭐ 素材不足时的合理外推授权：
                - 若教师上传的素材内容相对简短、不足以拆分成 %d 份独立教案，必须基于素材涉及的【核心知识点】合理外推：
                  · 围绕核心知识点补充对应的下位概念、典型应用场景、相关工具/技术
                  · 引入前后置知识点做串联铺垫（如学过的语法、即将引入的工具）
                  · 增加企业实战项目片段、行业典型案例、岗位胜任力要求
                - 外推必须紧扣本章节范围，不允许跨章节超纲、不允许偏离课程主线
                - 即使素材偏简，也必须保证规划出 %d 份可独立支撑 80 分钟课堂的教案
                """,
                chapter, sessionCount, weekStr, contentLevelName(contentLevel),
                truncate(sourceText, getSourceBudget(contentLevel)),
                sessionCount, sessionCount, sessionCount, sessionCount);
    }

    /**
     * 带规划上下文的单份教案 user prompt
     */
    public static String buildLessonPromptWithPlan(String chapter,
                                                   String sourceText,
                                                   int week,
                                                   String className,
                                                   String teacher,
                                                   String academicYear,
                                                   String semester,
                                                   String contentLevel,
                                                   String currentSubTitle,
                                                   java.util.List<String> currentKnowledgePoints,
                                                   String currentFocus,
                                                   String overview,
                                                   java.util.List<String> otherSubTitles,
                                                   java.util.List<String> otherKnowledgePoints) {
        String levelName = contentLevelName(contentLevel);
        String levelInstruction = contentLevelInstruction(contentLevel);
        String wordSpec = levelWordSpec(contentLevel);
        int segments = getTimelineSegments(contentLevel);
        String timelineSchema = buildTimelineSchema(segments);
        String extraSchema = buildExtraSchema(contentLevel);
        String extraRequirements = buildExtraRequirements(contentLevel);

        String currentKp = currentKnowledgePoints == null ? "" : String.join("、", currentKnowledgePoints);
        String otherKp = otherKnowledgePoints == null ? "无" : String.join("、", otherKnowledgePoints);
        String otherTitlesStr = otherSubTitles == null ? "无" : String.join("；", otherSubTitles);

        return String.format("""
                请根据以下教学素材与【已确定的知识点规划】，生成 1 次 80 分钟的教案。
                这是整章节多份教案中的【本份】，已经过统一规划，必须严格按本份的知识点范围生成，绝不能涉及其他份的知识点。

                【上下文】
                - 学年学期: %s 学年第 %s 学期
                - 任课老师: %s
                - 班级: %s
                - 章节: %s
                - 上课周次: 第 %d 周
                - 内容等级: %s
                - 教学过程段数要求: %d 段（必须严格按段数输出）

                【整章节脉络（仅供参考）】
                %s

                【⭐ 本份教案要覆盖的知识点（必须仅围绕这些）】
                - 小节标题: %s
                - 核心知识点: %s
                - 能力培养重点: %s

                【❌ 不允许涉及的知识点（已分配给其他份教案）】
                - 其他份小节: %s
                - 其他份知识点: %s

                【内容等级要求】
                %s

                【字数规范】
                %s
                %s

                ⭐ 80 分钟饱满度硬约束（与"本份知识点"边界并行）：
                - 本份教案必须填满 80 分钟课堂，不允许提前下课
                - 若素材中关于本份核心知识点的内容偏简短，必须【在本份知识点边界内适当拓展】：
                  · 补充与本份知识点直接相关的行业案例、企业实例、典型代码
                  · 增加随堂小练、易错点演示、分层任务（围绕本份知识点）
                  · 联系前后章节但仅做串联铺垫，不抢占其他份的核心讲授
                - 拓展不得越界到【其他份知识点】范围内（哪怕本份素材短缺）

                【来源资料】
                ```
                %s
                ```

                【输出要求】
                严格输出以下 JSON Schema（不要 markdown 代码块、不要解释）。

                ⚠️ JSON 合法性硬约束：
                - 字符串值内禁止使用未转义的 ASCII 双引号 " ；如需引用请用中文「」或全角""
                - 字符串值内若必须使用 ASCII 双引号，必须用反斜杠转义：\\"
                - 多行内容请用 \\n 表示，不允许出现真实换行
                - 严格使用英文逗号、英文冒号、英文方括号/花括号

                ⚠️ 内容硬约束：
                - title 字段必须等于本份小节标题：%s
                - 教学目标、重难点、教学过程必须仅围绕本份核心知识点展开
                - 严禁出现【其他份知识点】中的内容（即使素材里有）

                {
                  "academicYear": "学年（如 2025-2026）",
                  "semester": "学期（如 1）",
                  "teacher": "任课老师",
                  "planNo": "编号（可空）",
                  "teachingResource": "本份教案的教学资源",
                  "homework": "本份对应的课外作业（针对本份知识点设计）",
                  "ideologicalPoints": "课程思政融合点（本章节思政融入策略概述）",%s
                  "sessions": [
                    {
                      "className": "班级",
                      "week": "%d",
                      "title": "%s",
                      "knowledgeGoal": "知识目标（围绕本份核心知识点）",
                      "abilityGoal": "能力目标",
                      "literacyGoal": "素养目标",
                      "keyPoints": "教学重点",
                      "difficultPoints": "教学难点",
                      "studentSituation": "学情分析",
                      "timeline": [
                %s
                      ],%s
                      "ideologicalGoal": "思政目标（从科学精神/工匠精神/信息素养/职业道德/文化自信中选择融入）",
                      "reflection": "教学反思",
                      "improvement": "教学诊改"
                    }
                  ]
                }
                """,
                academicYear, semester, teacher, className, chapter, week, levelName, segments,
                nullSafe2(overview),
                currentSubTitle, currentKp, nullSafe2(currentFocus),
                otherTitlesStr, otherKp,
                levelInstruction, wordSpec, extraRequirements,
                truncate(sourceText, getSourceBudget(contentLevel)),
                currentSubTitle,
                extraSchema,
                week, currentSubTitle,
                timelineSchema,
                buildSessionExtraSchema(contentLevel));
    }

    private static String nullSafe2(String s) { return s == null ? "" : s; }

    /** 根据等级返回源材料字符预算 */
    public static int getSourceBudget(String level) {
        return switch (nullSafe(level)) {
            case "basic" -> 4000;
            case "detailed" -> 12000;
            case "comprehensive" -> 16000;
            default -> 8000;
        };
    }

    /** 根据等级返回时间轴段数 */
    public static int getTimelineSegments(String level) {
        return switch (nullSafe(level)) {
            case "basic" -> 4;
            case "detailed" -> 8;
            case "comprehensive" -> 10;
            default -> 6;
        };
    }

    /**
     * 构造生成多次教案的 user prompt
     */
    public static String buildUserPrompt(String chapter,
                                         String sourceText,
                                         String weeks,
                                         int sessionCount,
                                         String className,
                                         String teacher,
                                         String academicYear,
                                         String semester,
                                         String contentLevel) {
        String levelName = contentLevelName(contentLevel);
        String levelInstruction = contentLevelInstruction(contentLevel);
        String wordSpec = levelWordSpec(contentLevel);
        int segments = getTimelineSegments(contentLevel);
        String timelineSchema = buildTimelineSchema(segments);
        String extraSchema = buildExtraSchema(contentLevel);
        String extraRequirements = buildExtraRequirements(contentLevel);

        return String.format("""
                请根据以下教学素材，生成 %d 次连续教案（每次 80 分钟）。
                若提供 4 学时/周表示每周 2 次教案；2 学时/周表示每周 1 次教案。

                【上下文】
                - 学年学期: %s 学年第 %s 学期
                - 任课老师: %s
                - 班级: %s
                - 章节: %s
                - 上课周次: 第 %s 周
                - 内容等级: %s
                - 教学过程段数要求: %d 段（必须严格按段数输出）

                【内容等级要求】
                %s

                【字数规范（优先级高于 Schema 中的字数提示，请严格遵循）】
                %s
                %s

                ⭐ 80 分钟饱满度硬约束：
                - 每份教案必须填满 80 分钟，不允许提前下课
                - 若来源资料相对简短，必须基于资料涉及的核心知识点【主动拓展】：
                  · 补充行业案例、企业实战、生活联系
                  · 增加随堂练习、易错点演示、典型代码示范、分层任务
                  · 联系前后章节做串联与对比
                - 拓展内容必须紧扣本章节核心，不偏题、不超纲

                【来源资料】
                ```
                %s
                ```

                【输出要求】
                严格输出以下 JSON Schema（不要 markdown 代码块、不要解释）。

                ⚠️ JSON 合法性硬约束（违反将导致解析失败）：
                - 字符串值内禁止使用 ASCII 双引号 " ；如需引用请用中文「」或全角引号""
                - 字符串值内若必须使用 ASCII 双引号，必须用反斜杠转义：\\"
                - 不允许出现未转义的换行；多行内容请用 \\n 表示
                - 严格使用英文逗号、英文冒号、英文方括号/花括号
                {
                  "academicYear": "学年（如 2025-2026）",
                  "semester": "学期（如 1）",
                  "teacher": "任课老师",
                  "planNo": "编号（可空）",
                  "teachingResource": "本章节教学资源（教材/PPT/视频/平台等）",
                  "homework": "本章节课外作业建议（具体题目方向）",
                  "ideologicalPoints": "课程思政融合点（本章节思政融入策略概述）",%s
                  "sessions": [
                    {
                      "className": "班级",
                      "week": "周次（数字，如 2）",
                      "title": "本次教案的课题（具体小节标题，10-25字）",
                      "knowledgeGoal": "知识目标（分点写，每条以①②③开头）",
                      "abilityGoal": "能力目标（同上）",
                      "literacyGoal": "素养目标（同上）",
                      "keyPoints": "教学重点",
                      "difficultPoints": "教学难点",
                      "studentSituation": "学情分析（针对本次课内容）",
                      "timeline": [
                %s
                      ],%s
                      "ideologicalGoal": "思政目标（从科学精神/工匠精神/信息素养/职业道德/文化自信中选择融入）",
                      "reflection": "教学反思（基于本次内容预判学生反馈）",
                      "improvement": "教学诊改（针对反思的改进措施）"
                    }
                    // 共 %d 个 session
                  ]
                }
                """,
                sessionCount,
                academicYear, semester, teacher, className, chapter, weeks, levelName, segments,
                levelInstruction, wordSpec, extraRequirements,
                truncate(sourceText, getSourceBudget(contentLevel)),
                extraSchema,
                timelineSchema,
                buildSessionExtraSchema(contentLevel),
                sessionCount);
    }

    /** 根据段数生成 timeline JSON 模板 */
    private static String buildTimelineSchema(int segments) {
        return PromptModules.timelineSchema(segments);
    }

    /** 全局额外字段 */
    private static String buildExtraSchema(String level) {
        return PromptModules.topLevelExtraSchema(level);
    }

    /** 每个 session 的额外字段 */
    private static String buildSessionExtraSchema(String level) {
        return PromptModules.sessionExtraSchema(level);
    }

    /** 强化等级特殊要求 */
    private static String buildExtraRequirements(String level) {
        return PromptModules.lessonExtraRequirements(level);
    }

    private static String contentLevelName(String level) {
        return PromptModules.contentLevelName(level);
    }

    private static String contentLevelInstruction(String level) {
        return PromptModules.lessonLevelInstruction(level);
    }

    private static String levelWordSpec(String level) {
        return PromptModules.lessonWordSpec(level);
    }

    private static String nullSafe(String s) { return s == null ? "standard" : s; }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n...(已截断)";
    }
}
