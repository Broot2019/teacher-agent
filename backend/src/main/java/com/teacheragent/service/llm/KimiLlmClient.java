package com.teacheragent.service.llm;

import com.teacheragent.entity.LlmConfig;
import org.springframework.web.reactive.function.client.WebClient;

public class KimiLlmClient extends OpenAiCompatibleLlmClient {

    public static final String DEFAULT_BASE_URL = "https://api.moonshot.cn/v1";

    public KimiLlmClient(LlmConfig config, WebClient.Builder builder) {
        super(ensureBaseUrl(config), builder);
    }

    private static LlmConfig ensureBaseUrl(LlmConfig config) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            config.setBaseUrl(DEFAULT_BASE_URL);
        }
        return config;
    }
}
