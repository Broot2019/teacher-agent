package com.teacheragent.service.llm;

import java.time.Duration;

/**
 * LLM 调用参数集（不可变 record）。
 * <p>所有字段均可为 null，表示走调用方默认值（temperature=0.7、不限 max_tokens、180s 超时、重试 1 次）。
 * <p>提供 5 个开箱场景工厂：
 * <ul>
 *     <li>{@link #defaults()} 通用默认（普通对话）</li>
 *     <li>{@link #json()} 强制 JSON 输出（response_format=json_object）</li>
 *     <li>{@link #creative()} 教案生成（temperature=0.7，max_tokens=12000）</li>
 *     <li>{@link #deterministic()} 客观题生成（temperature=0.3）</li>
 *     <li>{@link #balanced()} 编程题生成（temperature=0.5，max_tokens=8000）</li>
 *     <li>{@link #reviewer()} 评审/校验（temperature=0.1，max_tokens=800）</li>
 * </ul>
 */
public record ChatOptions(
        Double temperature,
        Integer maxTokens,
        String responseFormat,
        Duration timeout,
        Integer retryAttempts
) {

    public static ChatOptions defaults() {
        return new ChatOptions(null, null, null, null, null);
    }

    public static ChatOptions json() {
        return new ChatOptions(null, null, "json_object", null, null);
    }

    /** 教案生成：温度偏高鼓励创造性，max_tokens 控制在多数厂商安全区间 */
    public static ChatOptions creative() {
        return new ChatOptions(0.7, 6000, "json_object", null, null);
    }

    /** 客观题（单选/多选/判断）生成：低温度保证答案确定 */
    public static ChatOptions deterministic() {
        return new ChatOptions(0.3, null, "json_object", null, null);
    }

    /** 编程题生成：中温度兼顾代码灵活性与正确性 */
    public static ChatOptions balanced() {
        return new ChatOptions(0.5, 4000, "json_object", null, null);
    }

    /** 评审/校验：极低温度保证打分稳定，max_tokens 极小避免冗长输出 */
    public static ChatOptions reviewer() {
        return new ChatOptions(0.1, 800, "json_object", null, null);
    }

    public ChatOptions withMaxTokens(int n) {
        return new ChatOptions(temperature, n, responseFormat, timeout, retryAttempts);
    }

    public ChatOptions withTemperature(double t) {
        return new ChatOptions(t, maxTokens, responseFormat, timeout, retryAttempts);
    }
}
