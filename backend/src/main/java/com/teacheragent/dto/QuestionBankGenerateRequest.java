package com.teacheragent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class QuestionBankGenerateRequest {

    /** 章节标题（single 模式必填，per_file 模式由每个文件配置覆盖） */
    private String chapter;

    /** 题型数量 map: single/multi/judge/program → 数量（single 模式使用） */
    private Map<String, Integer> typeCount = new HashMap<>();

    /** 难度倾向: 简单/一般/困难/混合 */
    private String difficulty = "混合";

    /** 内容等级: basic / standard / detailed / comprehensive */
    private String contentLevel = "standard";

    /** 指定使用的 provider（不传则用激活的） */
    private String provider;

    /** 指定课程配置 ID（不传则使用当前激活的课程配置） */
    private Long courseConfigId;

    /**
     * 编程题语言（仅当 typeCount.program > 0 时生效）。优先级：本字段 &gt; 课程配置 programmingLanguage &gt;
     * 从素材自动检测 &gt; 默认 java。空字符串视为不指定。
     */
    private String programmingLanguage;

    /** 模式: single（合并所有文件统一出题，旧逻辑） / per_file（每个文件单独配置） */
    private String mode = "single";

    /** per_file 模式下，每个文件的生成配置（fileIndex 0-based 对应 pptFiles 顺序） */
    private List<FileQuestionConfig> fileConfigs = new ArrayList<>();

    @Data
    public static class FileQuestionConfig {
        private Integer fileIndex;
        private String chapter;
        /** 题型数量 map: single/multi/judge/program → 数量 */
        private Map<String, Integer> typeCount = new HashMap<>();
    }
}
