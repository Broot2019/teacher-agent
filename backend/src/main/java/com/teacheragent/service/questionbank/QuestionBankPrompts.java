package com.teacheragent.service.questionbank;

import com.teacheragent.service.lessonplan.PromptModules;

/**
 * 题库生成提示词 — 课程信息通过 CourseContext 动态注入
 */
public class QuestionBankPrompts {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一位资深的%s课程出题专家，擅长根据课件内容设计有梯度、有区分度的练习题。

            出题原则：
            1. 题目必须紧扣给定的章节内容，不出超纲题
            2. 题干清晰、表述规范，避免歧义
            3. 答案唯一且正确（多选题需明确选哪几项）
            4. 解析简洁、有教学价值，不只是说答案是什么，而要解释为什么
            5. 难度分布合理：简单题考查记忆和基础理解，一般题考查应用，困难题考查综合分析
            6. 选项设计：错误选项要有迷惑性但确实是错的，避免明显荒谬选项

            ⭐ 知识点拓展授权（重要）：
            - 题目不必拘泥于素材原文出现的句子，可以基于素材所涉及的【核心知识点】进行合理拓展：
              · 由素材涉及的语法点 → 派生新场景、新应用、新案例
              · 由素材中的代码片段 → 演化为变种代码（改类型、改循环、改异常）
              · 引入企业实战常见用法、行业典型坑点
              · 同一知识点设计不同表述、不同角度的题目，避免与素材原例题完全雷同
            - 拓展必须紧扣本章节核心知识点，绝不允许跨章节超纲
            - 拓展题与素材直接题应混搭，保证既覆盖原文又有思维迁移训练

            输出要求：严格 JSON 数组，不要 markdown 代码块、不要任何额外说明。

            ⚠️ JSON 合法性硬约束（违反将导致解析失败）：
            - 字符串值内禁止使用 ASCII 双引号 " ；如需引用代码或术语，请用中文「」或全角引号""
            - 字符串值内若必须使用 ASCII 双引号，必须用反斜杠转义：\\"
            - 多行内容（如代码）请用 \\n 表示换行，不允许出现未转义的真实换行
            - 严格使用英文逗号、英文冒号、英文方括号/花括号
            """;

    public static final String DEFAULT_COURSE_NAME = "程序设计";

    public static String getSystemPrompt() {
        return String.format(SYSTEM_PROMPT_TEMPLATE, DEFAULT_COURSE_NAME);
    }

    public static String getSystemPrompt(String courseName) {
        return String.format(SYSTEM_PROMPT_TEMPLATE,
                courseName != null && !courseName.isBlank() ? courseName : DEFAULT_COURSE_NAME);
    }

    // 保持旧常量兼容
    public static final String SYSTEM_PROMPT = String.format(SYSTEM_PROMPT_TEMPLATE, DEFAULT_COURSE_NAME);

    public static String buildSinglePrompt(String chapter, String sourceText, int count, String difficulty, String contentLevel) {
        return String.format("""
                请基于以下章节素材，生成 %d 道【单选题】，难度倾向：%s。

                【内容等级】%s
                %s

                【字数规范（请严格遵循）】
                %s

                【章节】%s
                【素材】
                ```
                %s
                ```

                【输出 JSON Schema】
                [
                  {
                    "knowledge": "知识点（10字以内）",
                    "stem": "题干，包含必要的代码或场景描述",
                    "difficulty": "简单/一般/困难（其一）",
                    "answer": "正确选项字母，如 A",
                    "explanation": "解析，说明正确原因",
                    "options": ["选项A正文", "选项B正文", "选项C正文", "选项D正文"]
                  },
                  ...共 %d 道
                ]
                """, count, difficulty, contentLevelName(contentLevel), contentLevelInstruction(contentLevel),
                levelExplanationSpec(contentLevel), chapter, truncate(sourceText, 8000), count);
    }

    public static String buildMultiPrompt(String chapter, String sourceText, int count, String difficulty, String contentLevel) {
        return String.format("""
                请基于以下章节素材，生成 %d 道【多选题】（每题 2-4 个正确答案），难度倾向：%s。

                【内容等级】%s
                %s

                【字数规范（请严格遵循）】
                %s

                【章节】%s
                【素材】
                ```
                %s
                ```

                【输出 JSON Schema】
                [
                  {
                    "knowledge": "知识点",
                    "stem": "题干",
                    "difficulty": "简单/一般/困难",
                    "answer": "正确选项字母逗号分隔，如 A,C,D（必须英文逗号）",
                    "explanation": "解析",
                    "options": ["A选项正文", "B选项正文", "C选项正文", "D选项正文"]
                  }
                ]
                """, count, difficulty, contentLevelName(contentLevel), contentLevelInstruction(contentLevel),
                levelExplanationSpec(contentLevel), chapter, truncate(sourceText, 8000));
    }

    public static String buildJudgePrompt(String chapter, String sourceText, int count, String difficulty, String contentLevel) {
        return String.format("""
                请基于以下章节素材，生成 %d 道【判断题】，难度倾向：%s。

                【内容等级】%s
                %s

                【字数规范（请严格遵循）】
                %s

                【章节】%s
                【素材】
                ```
                %s
                ```

                【输出 JSON Schema】
                [
                  {
                    "knowledge": "知识点",
                    "stem": "判断题题干（陈述句）",
                    "difficulty": "简单/一般/困难",
                    "answer": "正确 或 错误（二选一）",
                    "explanation": "解析，说明对错原因",
                    "options": []
                  }
                ]
                """, count, difficulty, contentLevelName(contentLevel), contentLevelInstruction(contentLevel),
                levelExplanationSpec(contentLevel), chapter, truncate(sourceText, 8000));
    }

    public static String buildProgramPrompt(String chapter, String sourceText, int count, String difficulty, String contentLevel) {
        return buildProgramPrompt(chapter, sourceText, count, difficulty, contentLevel, "java");
    }

    /**
     * 编程题 prompt（多语言支持）。
     *
     * @param language 编程语言 key（java / python / cpp / c / csharp / go / javascript / typescript / php / sql 等）
     */
    public static String buildProgramPrompt(String chapter, String sourceText, int count, String difficulty,
                                            String contentLevel, String language) {
        String langName = languageDisplayName(language);
        String entryHint = languageEntryHint(language);
        return String.format("""
                请基于以下章节素材，生成 %d 道【编程题】，难度倾向：%s。
                每道题题干必须详细，包含输入输出说明、样例。答案必须是完整可运行的 %s 代码%s。

                ⚠️ 编程语言硬约束（违反将导致题目作废）：
                - 答案必须使用 %s 语言，不允许使用其他任何编程语言
                - 即使章节素材展示的是其他语言示例（如 Java/C/JavaScript 等），也必须将逻辑改写为 %s
                - 题干描述与样例输入输出可以保持语言无关（仅描述算法），但答案代码必须严格是 %s
                - 注释必须使用中文，不允许出现英文以外语言的代码标识符冒充

                【内容等级】%s
                %s

                【字数规范（请严格遵循）】
                %s

                【章节】%s
                【素材】
                ```
                %s
                ```

                【输出 JSON Schema】
                [
                  {
                    "knowledge": "知识点",
                    "stem": "完整题干，包含：1) 题目描述 2) 输入说明 3) 输出说明 4) 样例输入输出",
                    "difficulty": "简单/一般/困难",
                    "answer": "完整 %s 代码（含必要的 import / include / using / package 等），代码用 \\n 转义换行，注释用中文",
                    "explanation": "解题思路和关键步骤",
                    "options": []
                  }
                ]
                """, count, difficulty, langName, entryHint,
                langName, langName, langName,
                contentLevelName(contentLevel), contentLevelInstruction(contentLevel),
                levelExplanationSpec(contentLevel), chapter, truncate(sourceText, 8000), langName);
    }

    /** 编程语言显示名 */
    public static String languageDisplayName(String language) {
        if (language == null) return "Java";
        return switch (language.toLowerCase()) {
            case "java" -> "Java";
            case "python", "py" -> "Python";
            case "cpp", "c++" -> "C++";
            case "c" -> "C";
            case "csharp", "c#" -> "C#";
            case "go", "golang" -> "Go";
            case "javascript", "js" -> "JavaScript";
            case "typescript", "ts" -> "TypeScript";
            case "php" -> "PHP";
            case "sql" -> "SQL";
            case "kotlin" -> "Kotlin";
            case "swift" -> "Swift";
            case "rust" -> "Rust";
            case "ruby" -> "Ruby";
            default -> language;
        };
    }

    /** 编程语言入口点提示（如"带 main 方法"对 Java/C/C++/Go/Rust 适用，Python/JS 等无需） */
    public static String languageEntryHint(String language) {
        if (language == null) return "（带 main 方法）";
        return switch (language.toLowerCase()) {
            case "java", "c", "cpp", "c++", "csharp", "c#", "go", "golang", "rust", "kotlin", "swift" -> "（带 main 方法或入口函数）";
            case "python", "py", "javascript", "js", "typescript", "ts", "ruby", "php" -> "（脚本风格即可，可用 if __name__ == '__main__' 等惯用入口）";
            case "sql" -> "（含表创建、查询语句等完整可执行片段）";
            default -> "";
        };
    }

    private static String contentLevelName(String level) {
        return PromptModules.contentLevelName(level);
    }

    /** 评审专家 system prompt — 用于题库二次自检 */
    public static final String SYSTEM_PROMPT_REVIEW = """
            你是一位课程出题评审专家。任务：对一批题目从 5 个维度评分（0-10 整数）并给出建议。
            评审维度：
            1. relevance    — 题目是否紧扣章节核心知识点
            2. difficulty   — 难度梯度是否合理（不全是简单题或全是困难题）
            3. clarity      — 题干表述是否清晰无歧义
            4. correctness  — 答案/解析是否准确无误
            5. diversity    — 题目知识点是否多样、不重复

            硬约束：
            - 严格输出 JSON，不要 markdown、不要解释
            - overall 是 5 维平均（保留一位小数）
            - action 取值：pass（≥7）或 regen（<7）
            - 字符串值禁止使用未转义的 ASCII 双引号
            """;

    public static String buildReviewPrompt(String chapter, String type, int expectedCount, String questionsBriefJson) {
        return String.format("""
                请对以下【%s · %s】共 %d 道题进行评审。

                【题目摘要】
                ```json
                %s
                ```

                【输出 JSON Schema】
                {
                  "scores": {
                    "relevance": 0-10,
                    "difficulty": 0-10,
                    "clarity": 0-10,
                    "correctness": 0-10,
                    "diversity": 0-10
                  },
                  "overall": 0.0-10.0,
                  "action": "pass" 或 "regen",
                  "advice": "若 regen 给 1-3 条改进建议；pass 可写 \\"无\\""
                }
                """, chapter, typeFullName(type), expectedCount, questionsBriefJson);
    }

    private static String typeFullName(String type) {
        return switch (type == null ? "" : type) {
            case "single" -> "单选题";
            case "multi" -> "多选题";
            case "judge" -> "判断题";
            case "program" -> "编程题";
            default -> type;
        };
    }

    private static String contentLevelInstruction(String level) {
        return PromptModules.questionLevelInstruction(level);
    }

    private static String levelExplanationSpec(String level) {
        return PromptModules.questionExplanationSpec(level);
    }

    private static String truncate(String s, int max) {
        return PromptModules.truncate(s, max);
    }
}
