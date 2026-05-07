package com.teacheragent.service.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.teacheragent.common.BusinessException;
import com.teacheragent.entity.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * OpenAI 兼容协议的通用基类（智谱/Kimi/千问/DeepSeek/MiniMax 均通过此协议）
 */
@Slf4j
public abstract class OpenAiCompatibleLlmClient implements LlmClient {

    /** 单次调用网络超时默认值（教案/题库长 prompt 容易擦边 120 秒，调宽到 180 秒） */
    private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(180);
    /** 默认温度 */
    private static final double DEFAULT_TEMPERATURE = 0.7;
    /** 默认 5xx 重试次数 */
    private static final int DEFAULT_RETRY = 1;

    protected final LlmConfig config;
    protected final WebClient webClient;

    /** 由 {@link LlmClientFactory} 注入；未注入时调用直接放行（不限流，向后兼容） */
    private LlmRateLimiter rateLimiter;

    protected OpenAiCompatibleLlmClient(LlmConfig config, WebClient.Builder builder) {
        this.config = config;
        this.webClient = builder.baseUrl(config.getBaseUrl()).build();
    }

    /** 由 LlmClientFactory 在 create() 后调用，可选 */
    public void setRateLimiter(LlmRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /** 子类指定 chat completions 路径，默认 /chat/completions */
    protected String chatCompletionsPath() {
        return "/chat/completions";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, ChatOptions.defaults());
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        return chatJson(systemPrompt, userPrompt, ChatOptions.json());
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt, ChatOptions opts) {
        if (opts == null) opts = ChatOptions.json();
        // 若调用方未声明 response_format，则默认强制 json_object
        if (opts.responseFormat() == null) {
            opts = new ChatOptions(opts.temperature(), opts.maxTokens(), "json_object",
                    opts.timeout(), opts.retryAttempts());
        }
        String enhancedSystem = (systemPrompt == null ? "" : systemPrompt)
                + "\n请严格输出合法 JSON，不要包含任何解释或 markdown 代码块标记。";
        return doChat(enhancedSystem, userPrompt, opts);
    }

    private String doChat(String systemPrompt, String userPrompt, ChatOptions opts) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BusinessException("当前 [" + getProvider() + "] 未配置 API Key");
        }
        if (config.getApiKey().startsWith("enc:")) {
            throw new BusinessException("当前 [" + getProvider() + "] 的 API Key 为历史加密残留（enc: 前缀），请进入「模型配置」清空后重新填写明文 Key");
        }

        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(JSONObject.of("role", "system", "content", systemPrompt));
        }
        messages.add(JSONObject.of("role", "user", "content", userPrompt));

        double temperature = opts.temperature() != null ? opts.temperature() : DEFAULT_TEMPERATURE;
        JSONObject body = JSONObject.of(
                "model", config.getModelName(),
                "messages", messages,
                "temperature", temperature,
                "stream", false
        );
        // max_tokens 双向裁剪：opts 给值优先，但 LlmConfig.defaultMaxTokens 作为上限二次裁剪
        // 这样用户可以在 UI 给 kimi/qwen 等敏感厂商配置较小上限，覆盖 ChatOptions 全局默认
        Integer maxTokens = opts.maxTokens();
        Integer cap = config.getDefaultMaxTokens();
        if (cap != null && cap > 0) {
            if (maxTokens == null || maxTokens > cap) {
                maxTokens = cap;
            }
        }
        if (maxTokens != null && maxTokens > 0) {
            body.put("max_tokens", maxTokens);
        }
        if (opts.responseFormat() != null) {
            body.put("response_format", JSONObject.of("type", opts.responseFormat()));
        }
        addExtraParams(body);

        Duration timeout = opts.timeout() != null ? opts.timeout() : DEFAULT_CALL_TIMEOUT;
        int retry = opts.retryAttempts() != null ? Math.max(0, opts.retryAttempts()) : DEFAULT_RETRY;

        // 限流：仅当注入了 LlmRateLimiter 时启用，未注入则透明放行
        boolean acquired = false;
        if (rateLimiter != null) {
            acquired = rateLimiter.acquire(getProvider());
        }
        try {
            String resp = webClient.post()
                    .uri(chatCompletionsPath())
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(retry, Duration.ofMillis(500))
                            .filter(OpenAiCompatibleLlmClient::shouldRetry)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
            return extractContent(resp);
        } catch (BusinessException be) {
            throw be;
        } catch (WebClientResponseException e) {
            String msg = readableErrorFromBody(e.getResponseBodyAsString(), e.getStatusCode().value());
            // 4xx/5xx 失败时附带请求体关键参数到日志，方便定位 max_tokens / response_format / model 适配问题
            log.error("[{}] HTTP {} model={} max_tokens={} response_format={} resp={}",
                    getProvider(), e.getStatusCode().value(),
                    config.getModelName(),
                    body.get("max_tokens"),
                    body.get("response_format"),
                    msg);
            throw new BusinessException("调用 " + getProvider() + " 失败 [" + e.getStatusCode().value() + "]: " + msg);
        } catch (Exception e) {
            log.error("[{}] 调用失败: {}", getProvider(), e.getMessage());
            throw new BusinessException("调用 " + getProvider() + " 失败: " + e.getMessage());
        } finally {
            if (acquired && rateLimiter != null) {
                rateLimiter.release(getProvider());
            }
        }
    }

    /** 仅对网络异常和 5xx 重试 */
    private static boolean shouldRetry(Throwable t) {
        if (t instanceof WebClientResponseException w) {
            int code = w.getStatusCode().value();
            return code >= 500 && code < 600;
        }
        return t instanceof java.io.IOException
                || t instanceof java.util.concurrent.TimeoutException
                || (t.getCause() != null && t.getCause() instanceof java.io.IOException);
    }

    /** 子类可附加额外参数 */
    protected void addExtraParams(JSONObject body) {
    }

    /** 从响应 JSON 中抽取 content */
    protected String extractContent(String responseJson) {
        if (responseJson == null) {
            throw new BusinessException(getProvider() + " 返回空响应");
        }
        try {
            JSONObject obj = JSON.parseObject(responseJson);
            JSONArray choices = obj.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
                if (msg != null) {
                    String c = msg.getString("content");
                    if (c != null) return c;
                }
            }
            if (obj.containsKey("error")) {
                throw new BusinessException(getProvider() + " 错误: " + readableError(obj.get("error")));
            }
            throw new BusinessException(getProvider() + " 响应格式异常: " + responseJson);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(getProvider() + " 解析响应失败: " + e.getMessage());
        }
    }

    private static String readableError(Object error) {
        if (error == null) return "未知错误";
        if (error instanceof JSONObject eo) {
            String message = eo.getString("message");
            if (message != null && !message.isBlank()) {
                String code = eo.getString("code");
                return code != null && !code.isBlank() ? "[" + code + "] " + message : message;
            }
            return eo.toJSONString();
        }
        return String.valueOf(error);
    }

    private static String readableErrorFromBody(String body, int status) {
        if (body == null || body.isBlank()) return "HTTP " + status;
        try {
            JSONObject obj = JSON.parseObject(body);
            if (obj.containsKey("error")) {
                return readableError(obj.get("error"));
            }
            String msg = obj.getString("message");
            if (msg != null && !msg.isBlank()) return msg;
        } catch (Exception ignored) {
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    @Override
    public TestResult test() {
        long start = System.currentTimeMillis();
        try {
            String reply = chat("你是一个简洁的助手，回答必须不超过10个字。", "请回答：你好");
            long elapsed = System.currentTimeMillis() - start;
            if (reply == null || reply.isBlank()) {
                return TestResult.fail("响应内容为空");
            }
            return TestResult.ok("连通成功，响应: " + reply.trim().substring(0, Math.min(reply.length(), 30)), elapsed);
        } catch (Exception e) {
            return TestResult.fail(e.getMessage());
        }
    }

    @Override
    public String getProvider() {
        return config.getProvider();
    }

    @Override
    public String getModelName() {
        return config.getModelName();
    }
}
