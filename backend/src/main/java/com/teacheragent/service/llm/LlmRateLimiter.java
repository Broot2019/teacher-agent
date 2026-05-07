package com.teacheragent.service.llm;

import com.teacheragent.entity.LlmConfig;
import com.teacheragent.mapper.LlmConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * LLM 调用限流器（Phase 1 仅按 provider 维护"最大并发"信号量）。
 *
 * <p>设计目标：当上层并行调度（GenerationParallelizer，Phase 2 引入）一次提交多份生成任务时，
 * 避免单一 provider 触发厂商 RPM/QPM 限流（典型 429）。每个 provider 有独立的 Semaphore，
 * permits = LlmConfig.maxConcurrent（默认 4），按 acquire/release 粒度节流。
 *
 * <p>用法：
 * <pre>{@code
 *   if (limiter != null && limiter.acquire(provider)) {
 *       try { /* LLM 调用 *\/ } finally { limiter.release(provider); }
 *   }
 * }</pre>
 *
 * <p>RPM（每分钟调用数）粒度的滑动窗口在 Phase 2/3 视实际限流情况再决定是否引入；
 * 当前并发上限近似可控制 RPM（每次调用平均 30 秒，4 并发 ≈ 8 RPM/provider）。
 */
@Slf4j
@Component
public class LlmRateLimiter {

    private static final int DEFAULT_MAX_CONCURRENT = 4;
    private static final long DEFAULT_ACQUIRE_TIMEOUT_SECONDS = 60L;

    private final LlmConfigMapper llmConfigMapper;

    /** provider → Semaphore 缓存；首次 acquire 时按 LlmConfig.maxConcurrent 初始化 */
    private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    public LlmRateLimiter(LlmConfigMapper llmConfigMapper) {
        this.llmConfigMapper = llmConfigMapper;
    }

    /**
     * 阻塞获取一个 permit（最长等 60 秒）；获取失败返回 false 但不抛异常，
     * 由调用方决定是直接放行还是抛错。
     */
    public boolean acquire(String provider) {
        if (provider == null || provider.isBlank()) return true;
        Semaphore sem = semaphores.computeIfAbsent(provider.toLowerCase(), this::buildSemaphore);
        try {
            boolean ok = sem.tryAcquire(DEFAULT_ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!ok) {
                log.warn("[{}] 限流等待超时（{}s），放行调用避免阻塞业务", provider, DEFAULT_ACQUIRE_TIMEOUT_SECONDS);
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release(String provider) {
        if (provider == null || provider.isBlank()) return;
        Semaphore sem = semaphores.get(provider.toLowerCase());
        if (sem != null) sem.release();
    }

    /** 配置变更后调用，重置信号量（Phase 2 LlmConfigService 保存后触发） */
    public void reset(String provider) {
        if (provider == null || provider.isBlank()) return;
        semaphores.remove(provider.toLowerCase());
    }

    private Semaphore buildSemaphore(String provider) {
        int permits = DEFAULT_MAX_CONCURRENT;
        try {
            LlmConfig cfg = llmConfigMapper.selectOne(
                    new LambdaQueryWrapper<LlmConfig>().eq(LlmConfig::getProvider, provider).last("LIMIT 1"));
            if (cfg != null && cfg.getMaxConcurrent() != null && cfg.getMaxConcurrent() > 0) {
                permits = cfg.getMaxConcurrent();
            }
        } catch (Exception e) {
            log.warn("读取 [{}] 限流配置失败，使用默认 {}: {}", provider, DEFAULT_MAX_CONCURRENT, e.getMessage());
        }
        log.info("[{}] LLM 限流信号量初始化 permits={}", provider, permits);
        return new Semaphore(permits, true);
    }
}
