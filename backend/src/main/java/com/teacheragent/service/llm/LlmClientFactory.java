package com.teacheragent.service.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.entity.LlmConfig;
import com.teacheragent.mapper.LlmConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class LlmClientFactory {

    @Autowired
    private LlmConfigMapper llmConfigMapper;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private LlmRateLimiter rateLimiter;

    /** 根据 provider + 配置创建 client */
    public LlmClient create(LlmConfig config) {
        String provider = config.getProvider();
        if (provider == null) throw new BusinessException("provider 不能为空");
        OpenAiCompatibleLlmClient client = switch (provider.toLowerCase()) {
            case "zhipu"    -> new ZhipuLlmClient(config, webClientBuilder);
            case "kimi"     -> new KimiLlmClient(config, webClientBuilder);
            case "qwen"     -> new QwenLlmClient(config, webClientBuilder);
            case "minimax"  -> new MinimaxLlmClient(config, webClientBuilder);
            case "deepseek" -> new DeepSeekLlmClient(config, webClientBuilder);
            default -> throw new BusinessException("不支持的 LLM 厂商: " + provider);
        };
        // 注入限流器（按 provider 维护信号量，避免并行调用触发厂商 RPM 限流）
        client.setRateLimiter(rateLimiter);
        return client;
    }

    /** 获取当前激活的 client */
    public LlmClient getActive() {
        LlmConfig active = llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getIsActive, 1).last("LIMIT 1")
        );
        if (active == null) {
            throw new BusinessException("尚未激活任何大模型，请先在「模型配置」页设置激活的模型");
        }
        if (active.getApiKey() == null || active.getApiKey().isBlank()) {
            throw new BusinessException("激活的模型 [" + active.getProvider() + "] 未配置 API Key");
        }
        return create(active);
    }
}
