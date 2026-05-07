package com.teacheragent.service.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 教案/题库生成的并行调度器。
 *
 * <p>当单任务内部需要并行调用多次 LLM（N 周教案 / 4 题型 / N 题批量评审）时，使用本组件
 * 把 List&lt;input&gt; 映射成 List&lt;result&gt;，每次调用走 llmCallExecutor 线程池，受
 * {@code LlmRateLimiter} 节流。
 *
 * <p>失败隔离：单项失败不抛断整批，记录 null 由调用方过滤；同时记录 warn 日志便于排障。
 *
 * <p>提供 {@code onProgress(done, total)} 回调，每完成一项立即触发，让上层 service 能
 * 把任务进度从"启动 → 完成"更新为"启动 → 1/N → 2/N → ... → N/N → 完成"，用户看到
 * 的进度条匀速增长而不是跳变。
 */
@Slf4j
@Component
public class GenerationParallelizer {

    private final Executor llmCallExecutor;

    public GenerationParallelizer(@Qualifier("llmCallExecutor") Executor llmCallExecutor) {
        this.llmCallExecutor = llmCallExecutor;
    }

    /** 三参版本：无进度回调 */
    public <T, R> List<R> mapParallel(List<T> inputs, Function<T, R> fn, String diagTag) {
        return mapParallel(inputs, fn, diagTag, null);
    }

    /**
     * 并行映射 + 进度回调。
     *
     * @param inputs     输入列表
     * @param fn         单项处理函数
     * @param diagTag    日志诊断标签
     * @param onProgress 每完成一项触发，参数为 (已完成数, 总数)；可为 null 表示不需要进度
     */
    public <T, R> List<R> mapParallel(List<T> inputs, Function<T, R> fn, String diagTag,
                                      BiConsumer<Integer, Integer> onProgress) {
        if (inputs == null || inputs.isEmpty()) return new ArrayList<>();
        int total = inputs.size();
        AtomicInteger completed = new AtomicInteger(0);
        List<CompletableFuture<R>> futures = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            T item = inputs.get(i);
            int idx = i;
            CompletableFuture<R> fut = CompletableFuture.supplyAsync(() -> {
                R r = null;
                try {
                    r = fn.apply(item);
                } catch (Exception e) {
                    log.warn("[{}] 并行任务第 {} 项失败: {}", diagTag, idx, e.getMessage());
                }
                int done = completed.incrementAndGet();
                if (onProgress != null) {
                    try {
                        onProgress.accept(done, total);
                    } catch (Exception e) {
                        log.warn("[{}] 进度回调异常: {}", diagTag, e.getMessage());
                    }
                }
                return r;
            }, llmCallExecutor);
            futures.add(fut);
        }
        // 等待全部完成（异常已被吞）
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<R> out = new ArrayList<>(futures.size());
        for (CompletableFuture<R> f : futures) {
            try {
                out.add(f.getNow(null));
            } catch (Exception e) {
                out.add(null);
            }
        }
        return out;
    }
}
