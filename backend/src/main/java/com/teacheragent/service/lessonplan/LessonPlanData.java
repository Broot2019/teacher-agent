package com.teacheragent.service.lessonplan;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 教案文档数据（一份 docx 含 1-3 次 session）
 */
@Data
public class LessonPlanData {

    /** 学年（如 "2025-2026"） */
    private String academicYear = "";

    /** 学期（如 "1"） */
    private String semester = "";

    /** 任课老师 */
    private String teacher = "";

    /** 编号 */
    private String planNo = "";

    /** 教学资源（共享） */
    private String teachingResource = "";

    /** 课外作业（共享） */
    private String homework = "";

    /** 分层任务（detailed/comprehensive 等级使用，可空） */
    private String layeredTask = "";

    /** 评价量规（comprehensive 等级使用，可空） */
    private String evaluation = "";

    /** 课程思政融合点（各等级均使用） */
    private String ideologicalPoints = "";

    /** 1-3 次 session（对应模板的 c1/c2/c3 列） */
    private List<LessonPlanSession> sessions = new ArrayList<>();
}
