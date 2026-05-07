package com.teacheragent.service.lessonplan;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次教案数据（对应模板表格中的一列 c1/c2/c3）
 */
@Data
public class LessonPlanSession {

    /** 班别 */
    private String className = "";

    /** 第几周（如 "2"） */
    private String week = "";

    /** 课题 */
    private String title = "";

    /** 知识目标 */
    private String knowledgeGoal = "";

    /** 能力目标 */
    private String abilityGoal = "";

    /** 素养目标 */
    private String literacyGoal = "";

    /** 教学重点 */
    private String keyPoints = "";

    /** 教学难点 */
    private String difficultPoints = "";

    /** 组织形式 (例如 "课堂教学（√）、上机操作（√）、模拟实验（  ）、外出参观（  ）、其他（  ）") */
    private String organizationForm = "课堂教学（ √ ）、上机操作（ √ ）、模拟实验（  ）、外出参观（  ）、其他（  ）";

    /** 教学方法 */
    private String teachingMethod = "理论讲授（ √ ）、实操演练（ √ ）、情境教学（  ）、案例教学（ √ ）、问题导向（ √ ）、合作探究（  ）、任务驱动（ √ ）、翻转课堂（  ）、其他（  ）";

    /** 学情分析 */
    private String studentSituation = "";

    /** 时间分配（教学过程，basic 4 段 / standard 6 段 / detailed 8 段 / comprehensive 10 段） */
    private List<TimeSlot> timeline = new ArrayList<>();

    /** 评价量规（comprehensive 等级使用，可空） */
    private String evaluationCriteria = "";

    /** 教学反思 */
    private String reflection = "";

    /** 教学诊改 */
    private String improvement = "";

    /** 思政目标 */
    private String ideologicalGoal = "";
}
