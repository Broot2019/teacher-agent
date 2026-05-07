package com.teacheragent.controller;

import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.common.R;
import com.teacheragent.entity.GenerationTask;
import com.teacheragent.service.TaskService;
import com.teacheragent.service.sse.TaskProgressBus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskProgressBus progressBus;

    @GetMapping("/{taskId}")
    public R<GenerationTask> get(@PathVariable String taskId) {
        GenerationTask t = taskService.get(taskId);
        if (t == null) throw new BusinessException("任务不存在");
        // 数据隔离：教师只能看自己的
        if (!CurrentUserHolder.isAdmin() && !t.getOwnerId().equals(CurrentUserHolder.currentId())) {
            throw new BusinessException(403, "无权访问该任务");
        }
        return R.ok(t);
    }

    @GetMapping("/list")
    public R<List<GenerationTask>> list(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(taskService.listByCurrent(limit));
    }

    @GetMapping("/running")
    public R<List<GenerationTask>> running() {
        return R.ok(taskService.listRunning());
    }

    /**
     * SSE 实时进度推送（替代前端轮询）。
     *
     * <p>前端用法：
     * <pre>{@code
     *   const es = new EventSource('/api/task/sse/' + taskId);
     *   es.addEventListener('progress', e => { ... });
     *   es.addEventListener('done', e => { es.close(); });
     * }</pre>
     *
     * <p>事件类型：
     * <ul>
     *     <li>{@code init} 连接建立</li>
     *     <li>{@code progress} 进度更新（payload: progress / stage / ts）</li>
     *     <li>{@code done} 任务结束（payload: status=success/failed, historyId, error）</li>
     * </ul>
     */
    @GetMapping(value = "/sse/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@PathVariable String taskId) {
        GenerationTask t = taskService.get(taskId);
        if (t == null) throw new BusinessException("任务不存在");
        if (!CurrentUserHolder.isAdmin() && !t.getOwnerId().equals(CurrentUserHolder.currentId())) {
            throw new BusinessException(403, "无权订阅该任务");
        }
        return progressBus.subscribe(taskId);
    }
}
