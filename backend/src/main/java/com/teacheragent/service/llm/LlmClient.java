package com.teacheragent.service.llm;

/**
 * 大模型客户端统一接口
 */
public interface LlmClient {

    /** 普通对话 */
    String chat(String systemPrompt, String userPrompt);

    /** 强制 JSON 输出（在 system prompt 中加入 JSON 约束） */
    String chatJson(String systemPrompt, String userPrompt);

    /**
     * 强制 JSON 输出（带可调参数：温度 / max_tokens / 超时 / 重试 等）。
     * 默认转发到旧的两参数 {@link #chatJson(String, String)} 以保证向后兼容；
     * {@link OpenAiCompatibleLlmClient} 已重写本方法支持完整参数。
     */
    default String chatJson(String systemPrompt, String userPrompt, ChatOptions opts) {
        return chatJson(systemPrompt, userPrompt);
    }

    /** 连接测试 */
    TestResult test();

    String getProvider();

    String getModelName();
}
