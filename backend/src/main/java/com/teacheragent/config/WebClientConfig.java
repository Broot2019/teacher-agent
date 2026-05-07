package com.teacheragent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /** WebClient 默认 codec 缓冲上限。LLM 长返回（教案 + 题库批量）容易超过 20MB，提到 100MB 留充分余量。 */
    private static final int MAX_IN_MEMORY_BYTES = 100 * 1024 * 1024;

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES));
    }
}

