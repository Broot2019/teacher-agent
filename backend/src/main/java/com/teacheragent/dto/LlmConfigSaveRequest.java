package com.teacheragent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LlmConfigSaveRequest {

    private Long id;

    @NotBlank(message = "provider 不能为空")
    private String provider;

    private String apiKey;

    private String modelName;

    private String baseUrl;

    /** 最大并发数（默认 4） */
    private Integer maxConcurrent;

    /** 每分钟最大请求数（保留字段；默认 60） */
    private Integer rpmLimit;

    /** LLM 调用默认 max_tokens（为空表示不传） */
    private Integer defaultMaxTokens;
}
