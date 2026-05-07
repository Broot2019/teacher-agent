package com.teacheragent.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** 教案/题库任务编排线程池：每个任务跑一个 executeAsync */
    @Override
    @Bean(name = "generationExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        // 教案/题库都是分钟级长任务；扩容后单机并发上限明显提升
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("generation-");
        exec.setKeepAliveSeconds(60);
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(60);
        // 队列满时降级到调用线程慢慢做，避免静默拒绝触发 refund
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    /**
     * LLM 调用专用并行池：单任务内部把 N 周教案 / 4 题型并行化时使用。
     *
     * <p>与 generationExecutor 区分：
     * <ul>
     *     <li>generationExecutor 跑任务编排（生命周期分钟级，少量但每个任务体大）</li>
     *     <li>llmCallExecutor 跑底层 LLM 网络调用（每次秒级到分钟级，数量随并行需求弹性放大）</li>
     * </ul>
     *
     * <p>受 {@code LlmRateLimiter} 节流保护，不会真的把厂商打爆——拿不到 permit 的线程会
     * 阻塞在 acquire 上，确保按 maxConcurrent 节流。
     */
    @Bean(name = "llmCallExecutor")
    public Executor llmCallExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(16);
        exec.setMaxPoolSize(32);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("llm-call-");
        exec.setKeepAliveSeconds(60);
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(120);
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            org.slf4j.LoggerFactory.getLogger("AsyncUncaught")
                    .error("异步任务异常 method={}", method.getName(), ex);
        };
    }
}
