package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("llm_config")
public class LlmConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** zhipu / kimi / minimax / qwen */
    private String provider;

    private String apiKey;

    private String modelName;

    private String baseUrl;

    /** 0 否 / 1 是 */
    private Integer isActive;

    /** success / failed / untested */
    private String lastTestStatus;

    private LocalDateTime lastTestTime;

    private String lastTestMessage;

    /** 最大并发数（限流器按 provider 维护 Semaphore；默认 4） */
    private Integer maxConcurrent;

    /** 每分钟最大请求数（保留字段，Phase 2/3 视实际限流情况启用滑动窗口；默认 60） */
    private Integer rpmLimit;

    /** LLM 调用默认 max_tokens（每次调用未显式指定时使用；为空则不传） */
    private Integer defaultMaxTokens;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
