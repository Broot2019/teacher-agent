package com.teacheragent.service.sse;

import com.alibaba.fastjson2.JSONObject;
import com.teacheragent.entity.GenerationTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务进度 SSE 推送总线。
 *
 * <p>替代前端轮询：客户端通过 {@code GET /api/task/sse/{taskId}} 建立 SSE 连接，
 * 后端在 TaskService.updateProgress / markSuccess / markFailed 时调用本组件推送进度事件。
 *
 * <p>设计要点：
 * <ul>
 *     <li>按 taskId 维护订阅者集合（一个任务可能被多个浏览器标签订阅）</li>
 *     <li>SseEmitter 30 分钟超时（足够覆盖最长任务）</li>
 *     <li>send 失败自动从订阅集合移除（避免下次推送继续重试）</li>
 *     <li>terminal 状态推送后自动 complete 并清理订阅集合</li>
 * </ul>
 *
 * <p>不引入 Redis 等外部依赖：当前为单机部署，进程内 Map 足够。
 * 多实例部署时可换成 Redis Pub/Sub。
 */
@Slf4j
@Service
public class TaskProgressBus {

    /** 30 分钟超时；超过自动断开 */
    private static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /** 客户端订阅：返回 SseEmitter 给 controller 直接 return */
    public SseEmitter subscribe(String taskId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list = subscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(e -> remove(taskId, emitter));
        // 立刻发一个 init 事件让前端知道连接成功
        try {
            emitter.send(SseEmitter.event().name("init").data("connected"));
        } catch (IOException e) {
            remove(taskId, emitter);
        }
        return emitter;
    }

    /** 推送进度事件 */
    public void publishProgress(String taskId, int progress, String stage) {
        publish(taskId, "progress", buildProgressPayload(taskId, progress, stage, null, null), false);
    }

    /** 推送任务完成 */
    public void publishSuccess(GenerationTask task) {
        publish(task.getTaskId(), "done",
                buildProgressPayload(task.getTaskId(), 100, task.getStageText(), "success", task.getResultHistoryId()),
                true);
    }

    /** 推送任务失败 */
    public void publishFailed(String taskId, String errorMsg) {
        publish(taskId, "done",
                buildProgressPayload(taskId, -1, "失败", "failed", null)
                        .fluentPut("error", errorMsg == null ? "" : errorMsg),
                true);
    }

    private void publish(String taskId, String eventName, JSONObject payload, boolean terminal) {
        List<SseEmitter> list = subscribers.get(taskId);
        if (list == null || list.isEmpty()) return;
        Iterator<SseEmitter> it = list.iterator();
        while (it.hasNext()) {
            SseEmitter em = it.next();
            try {
                em.send(SseEmitter.event().name(eventName).data(payload.toJSONString()));
                if (terminal) {
                    em.complete();
                }
            } catch (Exception e) {
                // send 失败说明客户端已断开
                em.completeWithError(e);
                list.remove(em);
            }
        }
        if (terminal) {
            subscribers.remove(taskId);
        }
    }

    private JSONObject buildProgressPayload(String taskId, int progress, String stage,
                                            String status, Long historyId) {
        JSONObject o = new JSONObject();
        o.put("taskId", taskId);
        if (progress >= 0) o.put("progress", progress);
        if (stage != null) o.put("stage", stage);
        if (status != null) o.put("status", status);
        if (historyId != null) o.put("historyId", historyId);
        o.put("ts", System.currentTimeMillis());
        return o;
    }

    private void remove(String taskId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(taskId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) subscribers.remove(taskId);
        }
    }

    public int activeSubscriberCount() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }
}
