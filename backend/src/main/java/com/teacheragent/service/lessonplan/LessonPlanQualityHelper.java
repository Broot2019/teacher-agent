package com.teacheragent.service.lessonplan;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.teacheragent.service.llm.ChatOptions;
import com.teacheragent.service.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教案质量保障：合规校验 + 二次 LLM 自检。
 * 从 LessonPlanService 提取，便于独立复用与测试。
 */
@Slf4j
public final class LessonPlanQualityHelper {

    private LessonPlanQualityHelper() {}

    /** 教案合规校验：返回不合规 issue 列表（空 = 通过） */
    public static List<String> validateLessonPlanData(LessonPlanData data, String contentLevel) {
        List<String> issues = new ArrayList<>();
        if (data == null || data.getSessions() == null || data.getSessions().isEmpty()) {
            issues.add("缺少 session");
            return issues;
        }
        LessonPlanSession s = data.getSessions().get(0);
        if (isBlank(s.getTitle())) issues.add("缺少课题 title");
        if (isBlank(s.getKnowledgeGoal())) issues.add("缺少知识目标");
        if (isBlank(s.getAbilityGoal())) issues.add("缺少能力目标");
        if (isBlank(s.getLiteracyGoal())) issues.add("缺少素养目标");
        if (isBlank(s.getKeyPoints())) issues.add("缺少教学重点");
        if (isBlank(s.getDifficultPoints())) issues.add("缺少教学难点");
        if (isBlank(s.getStudentSituation())) issues.add("缺少学情分析");
        if (isBlank(s.getReflection())) issues.add("缺少教学反思");
        if (isBlank(s.getImprovement())) issues.add("缺少教学诊改");

        int expected = LessonPlanPrompts.getTimelineSegments(contentLevel);
        int actual = s.getTimeline() == null ? 0 : s.getTimeline().size();
        // 容忍 ±1 段
        if (Math.abs(actual - expected) > 1) {
            issues.add(String.format("教学过程段数 %d 与等级要求 %d 偏差过大", actual, expected));
        }

        // 等级附加要求
        if ("detailed".equals(contentLevel) || "comprehensive".equals(contentLevel)) {
            if (data.getLayeredTask() == null || data.getLayeredTask().isBlank()) {
                issues.add("详尽版/特详版必须包含 layeredTask（分层任务）");
            }
        }
        if ("comprehensive".equals(contentLevel)) {
            if (data.getEvaluation() == null || data.getEvaluation().isBlank()) {
                issues.add("特详版必须包含 evaluation（评价量规）");
            }
            if (s.getEvaluationCriteria() == null || s.getEvaluationCriteria().isBlank()) {
                issues.add("特详版必须包含 evaluationCriteria（评价细则）");
            }
        }

        // 字数粗校验
        if (s.getKeyPoints() != null && s.getKeyPoints().length() < 10) issues.add("教学重点字数过少");
        if (s.getReflection() != null && s.getReflection().length() < 10) issues.add("教学反思字数过少");

        return issues;
    }

    /** 调 LLM 对生成的教案做评审，返回评分与建议 */
    public static ReviewResult reviewLessonPlanData(LlmClient client, LessonPlanData data, String contentLevel,
                                                    String currentSubTitle, List<String> currentKp) throws Exception {
        ReviewResult r = new ReviewResult();
        // 提取关键字段做精简 JSON，控制 token
        JSONObject brief = new JSONObject();
        if (data.getSessions() != null && !data.getSessions().isEmpty()) {
            LessonPlanSession s = data.getSessions().get(0);
            brief.put("title", s.getTitle());
            brief.put("week", s.getWeek());
            brief.put("knowledgeGoal", s.getKnowledgeGoal());
            brief.put("abilityGoal", s.getAbilityGoal());
            brief.put("literacyGoal", s.getLiteracyGoal());
            brief.put("keyPoints", s.getKeyPoints());
            brief.put("difficultPoints", s.getDifficultPoints());
            brief.put("studentSituation", s.getStudentSituation());
            brief.put("timelineSize", s.getTimeline() == null ? 0 : s.getTimeline().size());
            brief.put("reflection", s.getReflection());
            brief.put("improvement", s.getImprovement());
            if (s.getEvaluationCriteria() != null && !s.getEvaluationCriteria().isBlank())
                brief.put("evaluationCriteria", s.getEvaluationCriteria());
        }
        if (data.getLayeredTask() != null && !data.getLayeredTask().isBlank()) brief.put("layeredTask", data.getLayeredTask());
        if (data.getEvaluation() != null && !data.getEvaluation().isBlank()) brief.put("evaluation", data.getEvaluation());

        int expectedSegs = LessonPlanPrompts.getTimelineSegments(contentLevel);
        String prompt = LessonPlanPrompts.buildReviewPrompt(contentLevel, currentSubTitle, currentKp,
                expectedSegs, brief.toJSONString());
        String json = client.chatJson(LessonPlanPrompts.SYSTEM_PROMPT_REVIEW, prompt);
        if (json == null || json.isBlank()) return r;
        String cleaned = LessonPlanJsonUtil.stripCodeBlock(json.trim());
        JSONObject obj;
        try {
            obj = JSON.parseObject(cleaned);
        } catch (Exception e) {
            obj = JSON.parseObject(LessonPlanJsonUtil.repairJsonQuotes(cleaned));
        }
        r.overall = obj.containsKey("overall") ? obj.getDoubleValue("overall") : 10.0;
        r.action = obj.getString("action") == null ? "pass" : obj.getString("action");
        r.advice = obj.getString("advice") == null ? "" : obj.getString("advice");
        return r;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 批量评审 N 份教案（一次 LLM 调用拿到所有结果）。
     *
     * <p>把每份的精简 brief 打包成数组发给评审 LLM，要求按顺序返回每份的 scores/overall/action/advice。
     * 与逐份评审相比，调用次数从 N 降至 1，但单次 prompt token 增长——经验上 5-10 份/批最佳。
     * 当 dataList.size() &gt; 10 时，会自动按 10 份/批切分调用。
     *
     * @param client          LLM 客户端
     * @param dataList        教案数据列表（与 subTitles/kpsList 顺序对齐，长度相等）
     * @param contentLevel    内容等级（决定字数与字段要求）
     * @param subTitles       每份的小节标题（与 dataList 长度相等）
     * @param kpsList         每份的核心知识点列表（与 dataList 长度相等）
     * @return 长度等于 dataList.size() 的 ReviewResult 列表；评审失败的位置返回默认通过结果（pass/10.0）
     */
    public static List<ReviewResult> reviewBatch(LlmClient client, List<LessonPlanData> dataList,
                                                 String contentLevel, List<String> subTitles, List<List<String>> kpsList) {
        List<ReviewResult> out = new ArrayList<>();
        if (dataList == null || dataList.isEmpty()) return out;
        int total = dataList.size();
        for (int i = 0; i < total; i++) out.add(defaultPass());

        int batchSize = 10;
        int expectedSegs = LessonPlanPrompts.getTimelineSegments(contentLevel);

        for (int batchStart = 0; batchStart < total; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, total);
            JSONArray briefArr = new JSONArray();
            for (int i = batchStart; i < batchEnd; i++) {
                LessonPlanData data = dataList.get(i);
                String subTitle = subTitles == null || i >= subTitles.size() ? "" : subTitles.get(i);
                List<String> kps = kpsList == null || i >= kpsList.size() ? List.of() : kpsList.get(i);
                briefArr.add(buildBrief(i - batchStart, data, subTitle, kps));
            }

            String prompt = String.format("""
                    请按【顺序】对以下 %d 份教案逐份评审，返回 JSON 数组。

                    【评审依据】
                    - 内容等级: %s（决定字数与字段要求）
                    - 期望教学过程段数: %d

                    【输出 JSON Schema】严格按数组返回，长度必须等于 %d，每份对应输入数组的同 index：
                    [
                      {
                        "index": 0-based 索引,
                        "overall": 0.0-10.0 浮点数（保留一位小数）,
                        "action": "pass" 或 "regen"（overall<7 必须 regen）,
                        "advice": "若 regen 给 1-3 条具体建议（每条 30 字内）；pass 可写「无」"
                      }
                    ]

                    【教案数据】
                    %s
                    """, batchEnd - batchStart, LessonPlanPrompts.getTimelineSegments(contentLevel) > 0
                            ? contentLevel : "standard",
                    expectedSegs, batchEnd - batchStart, briefArr.toJSONString());

            try {
                String json = client.chatJson(LessonPlanPrompts.SYSTEM_PROMPT_REVIEW, prompt, ChatOptions.reviewer());
                if (json == null || json.isBlank()) continue;
                String cleaned = LessonPlanJsonUtil.stripCodeBlock(json.trim());
                JSONArray arr;
                try {
                    arr = JSON.parseArray(cleaned);
                } catch (Exception e) {
                    arr = JSON.parseArray(LessonPlanJsonUtil.repairJsonQuotes(cleaned));
                }
                if (arr == null) continue;
                for (int j = 0; j < arr.size(); j++) {
                    JSONObject obj = arr.getJSONObject(j);
                    int relIdx = obj.containsKey("index") ? obj.getIntValue("index") : j;
                    int absIdx = batchStart + relIdx;
                    if (absIdx < 0 || absIdx >= total) continue;
                    ReviewResult r = new ReviewResult();
                    r.overall = obj.containsKey("overall") ? obj.getDoubleValue("overall") : 10.0;
                    r.action = obj.getString("action") == null ? "pass" : obj.getString("action");
                    r.advice = obj.getString("advice") == null ? "" : obj.getString("advice");
                    out.set(absIdx, r);
                }
            } catch (Exception e) {
                log.warn("批量评审第 {}-{} 份失败，对应区间默认 pass: {}", batchStart, batchEnd - 1, e.getMessage());
            }
        }
        return out;
    }

    /** 构造单份精简摘要（与原 reviewLessonPlanData 一致，多加一个 index 标识） */
    private static JSONObject buildBrief(int index, LessonPlanData data, String subTitle, List<String> kps) {
        JSONObject brief = new JSONObject();
        brief.put("index", index);
        brief.put("expectedSubTitle", subTitle == null ? "" : subTitle);
        brief.put("expectedKnowledgePoints", kps == null ? List.of() : kps);
        if (data.getSessions() != null && !data.getSessions().isEmpty()) {
            LessonPlanSession s = data.getSessions().get(0);
            brief.put("title", s.getTitle());
            brief.put("week", s.getWeek());
            brief.put("knowledgeGoal", s.getKnowledgeGoal());
            brief.put("abilityGoal", s.getAbilityGoal());
            brief.put("literacyGoal", s.getLiteracyGoal());
            brief.put("keyPoints", s.getKeyPoints());
            brief.put("difficultPoints", s.getDifficultPoints());
            brief.put("studentSituation", s.getStudentSituation());
            brief.put("timelineSize", s.getTimeline() == null ? 0 : s.getTimeline().size());
            brief.put("reflection", s.getReflection());
            brief.put("improvement", s.getImprovement());
            if (s.getEvaluationCriteria() != null && !s.getEvaluationCriteria().isBlank())
                brief.put("evaluationCriteria", s.getEvaluationCriteria());
        }
        if (data.getLayeredTask() != null && !data.getLayeredTask().isBlank()) brief.put("layeredTask", data.getLayeredTask());
        if (data.getEvaluation() != null && !data.getEvaluation().isBlank()) brief.put("evaluation", data.getEvaluation());
        return brief;
    }

    private static ReviewResult defaultPass() {
        ReviewResult r = new ReviewResult();
        r.overall = 10.0;
        r.action = "pass";
        r.advice = "";
        return r;
    }
}
