package com.teacheragent.service.llm;

import com.teacheragent.entity.LlmConfig;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MiniMax 客户端 - 走 OpenAI 兼容路径 /chat/completions（与新版模型 MiniMax-M2.5 系列、Text-01 等兼容）
 * 旧专属接口 /text/chatcompletion_v2 已不推荐用于新模型，故统一改为继承 OpenAiCompatibleLlmClient。
 */
public class MinimaxLlmClient extends OpenAiCompatibleLlmClient {

    public static final String DEFAULT_BASE_URL = "https://api.minimax.chat/v1";

    public MinimaxLlmClient(LlmConfig config, WebClient.Builder builder) {
        super(ensureBaseUrl(config), builder);
    }

    private static LlmConfig ensureBaseUrl(LlmConfig config) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            config.setBaseUrl(DEFAULT_BASE_URL);
        }
        return config;
    }
}
