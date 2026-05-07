package com.teacheragent.service.lessonplan;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.teacheragent.common.BusinessException;

/**
 * 教案 JSON 解析与修复的纯工具类。
 * 从 LessonPlanService 提取，便于复用与单元测试。
 */
public final class LessonPlanJsonUtil {

    private LessonPlanJsonUtil() {}

    /** 把 ```json ... ``` 这类 markdown 代码围栏剥离 */
    public static String stripCodeBlock(String s) {
        if (s == null) return "";
        if (s.startsWith("```")) {
            int firstLineEnd = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                return s.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return s;
    }

    /**
     * 修复 LLM 输出 JSON 中字符串值内未转义的 ASCII 双引号。
     * 状态机扫描：遇到 `"` 时前瞻判断它是否为字符串结束（后跟空白后接 , } ] : 换行）；
     * 若不是，则视为嵌套引号自动转义为 \"。
     */
    public static String repairJsonQuotes(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length() + 32);
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!inString) {
                out.append(c);
                if (c == '"') {
                    inString = true;
                    escape = false;
                }
                continue;
            }
            if (escape) {
                out.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                out.append(c);
                escape = true;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                while (j < s.length() && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j++;
                if (j >= s.length()) {
                    out.append(c);
                    inString = false;
                    continue;
                }
                char nx = s.charAt(j);
                if (nx == ',' || nx == '}' || nx == ']' || nx == ':' || nx == '\n' || nx == '\r') {
                    out.append(c);
                    inString = false;
                } else {
                    // 嵌套引号：转义
                    out.append('\\').append('"');
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * 解析教案数据 JSON。失败时尝试三层修复：直接解析 → 提取 {...} 子串 → 转义嵌套引号。
     */
    public static <T> T parseLessonPlanJson(String json, Class<T> clazz) {
        if (json == null) throw new BusinessException("LLM 返回空内容");
        String cleaned = stripCodeBlock(json.trim());
        try {
            return JSON.parseObject(cleaned, clazz);
        } catch (Exception e) {
            int s = cleaned.indexOf('{');
            int e2 = cleaned.lastIndexOf('}');
            String trimmed = (s >= 0 && e2 > s) ? cleaned.substring(s, e2 + 1) : cleaned;
            try {
                return JSON.parseObject(trimmed, clazz);
            } catch (Exception ignored) { }
            try {
                String repaired = repairJsonQuotes(trimmed);
                return JSON.parseObject(repaired, clazz);
            } catch (Exception ee) {
                // 仍失败，向上抛
            }
            throw new BusinessException("LLM 返回的不是合法 JSON: " + e.getMessage());
        }
    }

    /**
     * 解析规划阶段 LLM 返回的 JSON；失败时抛 BusinessException。
     */
    public static LessonPlan parsePlanJson(String json, int expectedCount, int[] weekAssignment, String chapter) {
        if (json == null || json.isBlank()) throw new BusinessException("规划 LLM 返回空");
        String cleaned = stripCodeBlock(json.trim());
        JSONObject obj;
        try {
            obj = JSON.parseObject(cleaned);
        } catch (Exception e) {
            try {
                obj = JSON.parseObject(repairJsonQuotes(cleaned));
            } catch (Exception ee) {
                throw new BusinessException("规划 JSON 解析失败: " + e.getMessage());
            }
        }
        LessonPlan p = new LessonPlan();
        p.overview = obj.getString("overview");
        JSONArray arr = obj.getJSONArray("plans");
        if (arr == null || arr.isEmpty()) throw new BusinessException("规划 plans 字段为空");
        for (int i = 0; i < arr.size(); i++) {
            JSONObject po = arr.getJSONObject(i);
            PlanItem pi = new PlanItem();
            pi.index = po.getIntValue("index", i + 1);
            pi.week = po.getIntValue("week", weekAssignment.length > i ? weekAssignment[i] : weekAssignment[0]);
            pi.subTitle = po.getString("subTitle");
            pi.focus = po.getString("focus");
            pi.sourceRange = po.getString("sourceRange");
            JSONArray kps = po.getJSONArray("knowledgePoints");
            if (kps != null) {
                for (int j = 0; j < kps.size(); j++) pi.knowledgePoints.add(kps.getString(j));
            }
            if (pi.subTitle == null || pi.subTitle.isBlank()) {
                pi.subTitle = chapter + " 第" + pi.index + "课时";
            }
            p.plans.add(pi);
        }
        // 补齐到 expectedCount
        while (p.plans.size() < expectedCount) {
            int idx = p.plans.size();
            PlanItem pi = new PlanItem();
            pi.index = idx + 1;
            pi.week = weekAssignment[idx];
            pi.subTitle = chapter + " 第" + (idx + 1) + "课时";
            pi.focus = "";
            p.plans.add(pi);
        }
        // 截断超出
        if (p.plans.size() > expectedCount) {
            p.plans = p.plans.subList(0, expectedCount);
        }
        return p;
    }

    /** 兜底规划：当 LLM 规划失败时按章节顺序生成占位 */
    public static LessonPlan buildFallbackPlan(int sessions, int[] weekAssignment, String chapter) {
        LessonPlan p = new LessonPlan();
        p.overview = chapter + " 整章节顺序展开";
        for (int i = 0; i < sessions; i++) {
            PlanItem pi = new PlanItem();
            pi.index = i + 1;
            pi.week = weekAssignment[i];
            pi.subTitle = chapter + " 第" + (i + 1) + "课时";
            pi.focus = "本课时核心知识点应用";
            p.plans.add(pi);
        }
        return p;
    }
}
