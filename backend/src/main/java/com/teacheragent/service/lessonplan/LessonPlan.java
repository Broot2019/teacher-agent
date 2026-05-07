package com.teacheragent.service.lessonplan;

import java.util.ArrayList;
import java.util.List;

/**
 * 教案规划阶段 LLM 输出的整体结构（per_file 模式知识点规划）。
 * 由 LessonPlanService 内部类 LessonPlan 提取出来，便于 JSON 工具与质量助手共享。
 */
public class LessonPlan {
    public String overview;
    public List<PlanItem> plans = new ArrayList<>();
}
