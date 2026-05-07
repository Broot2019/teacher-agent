package com.teacheragent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LessonPlanGenerateRequest {

    /** 章节标题（单章节模式必填，按周次范围模式可空） */
    private String chapter;

    /** 单周次或周次列表，例 "2" 或 "2,3,5"（向后兼容） */
    private String weekNo;

    /** 起始周（按范围模式必填） */
    private Integer weekStart;

    /** 结束周（按范围模式必填） */
    private Integer weekEnd;

    /** 模式: single（单章节单次） / range（按周次范围批量） / per_file（按文件配置） */
    private String mode = "single";

    /** 每周学时 4 或 2 */
    private Integer hoursPerWeek = 4;

    /** 打包模式：single / weekly / full */
    private String packageMode = "weekly";

    /** 班级 */
    private String className = "2024级大数据技术01-05班";

    /** 教师 */
    private String teacher = "";

    /** 学年 */
    private String academicYear = "2025-2026";

    /** 学期 */
    private String semester = "1";

    /** 编号 */
    private String planNo = "";

    /** 内容等级: basic基础 / standard标准 / detailed详尽 / comprehensive特详 */
    private String contentLevel = "standard";

    /** 指定 provider */
    private String provider;

    /** 指定课程配置 ID（不传则使用当前激活的课程配置） */
    private Long courseConfigId;

    /** 手动周-章节映射（自动解析失败时使用，仅 range 模式） */
    private List<WeekChapterMapping> manualMapping = new ArrayList<>();

    /** per_file 模式下，每个文件的生成配置；与 pptFiles 顺序一一对应（fileIndex 0-based） */
    private List<FileGenerationConfig> fileConfigs = new ArrayList<>();

    /** 是否将多份教案合并到一份 docx 输出（per_file 默认 true） */
    private Boolean mergeIntoOne = true;

    @Data
    public static class WeekChapterMapping {
        private Integer week;
        private String chapter;
        private String topics;
    }

    /** 单文件生成配置 */
    @Data
    public static class FileGenerationConfig {
        /** 上传文件列表中的索引（0-based） */
        private Integer fileIndex;
        /** 该文件对应的章节标题（默认从文件名推断） */
        private String chapter;
        /** 该文件覆盖的起始周 */
        private Integer weekStart;
        /** 该文件覆盖的结束周 */
        private Integer weekEnd;
        /** 该文件需要生成的教案总数（每份 80 分钟，由后端均分到 weekStart..weekEnd 区间） */
        private Integer sessionCount = 1;
    }
}
