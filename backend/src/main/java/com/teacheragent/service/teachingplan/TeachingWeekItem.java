package com.teacheragent.service.teachingplan;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingWeekItem {
    /** 周次 */
    private Integer week;
    /** 章节 (例: "第一章" 或 "国庆放假") */
    private String chapter;
    /** 章节标题（含具体小节） */
    private String chapterTitle = "";
    /** 内容要点列表 */
    private List<String> topics = new ArrayList<>();
}
