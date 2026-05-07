package com.teacheragent.service.lessonplan;

import java.util.ArrayList;
import java.util.List;

/**
 * 教案规划阶段每一份的描述（一份教案 = 一节课时）。
 */
public class PlanItem {
    public int index;
    public int week;
    public String subTitle;
    public List<String> knowledgePoints = new ArrayList<>();
    public String focus;
    public String sourceRange;
}
