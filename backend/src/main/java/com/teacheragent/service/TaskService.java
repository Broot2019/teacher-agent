package com.teacheragent.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.entity.GenerationTask;
import com.teacheragent.mapper.GenerationTaskMapper;
import com.teacheragent.service.sse.TaskProgressBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final GenerationTaskMapper taskMapper;

    /** 可选注入：早期未引入 SSE 时为空，引入后由 Spring 注入 */
    @Autowired(required = false)
    private TaskProgressBus progressBus;

    public GenerationTask create(String type, Long ownerId, Object params) {
        GenerationTask t = new GenerationTask();
        t.setTaskId(UUID.randomUUID().toString());
        t.setType(type);
        t.setOwnerId(ownerId);
        t.setStatus("pending");
        t.setProgress(0);
        t.setStageText("已提交，等待执行...");
        if (params != null) {
            try { t.setParamsJson(JSON.toJSONString(params)); } catch (Exception ignored) {}
        }
        taskMapper.insert(t);
        return t;
    }

    public void markRunning(String taskId) {
        GenerationTask t = getRequired(taskId);
        t.setStatus("running");
        t.setStartTime(LocalDateTime.now());
        t.setProgress(1);
        t.setStageText("已开始执行...");
        taskMapper.updateById(t);
        safePublish(() -> { if (progressBus != null) progressBus.publishProgress(taskId, 1, "已开始执行..."); });
    }

    public void updateProgress(String taskId, int progress, String stage) {
        GenerationTask t = getOrNull(taskId);
        if (t == null) return;
        int actual = Math.min(99, Math.max(t.getProgress() == null ? 0 : t.getProgress(), progress));
        t.setProgress(actual);
        if (stage != null) t.setStageText(stage);
        taskMapper.updateById(t);
        safePublish(() -> { if (progressBus != null) progressBus.publishProgress(taskId, actual, stage); });
    }

    @Transactional
    public void markSuccess(String taskId, Long historyId, String stage) {
        GenerationTask t = getRequired(taskId);
        t.setStatus("success");
        t.setProgress(100);
        t.setStageText(stage == null ? "完成" : stage);
        t.setResultHistoryId(historyId);
        t.setFinishTime(LocalDateTime.now());
        taskMapper.updateById(t);
        // SSE 推送独立 try-catch，绝不影响事务提交
        safePublish(() -> { if (progressBus != null) progressBus.publishSuccess(t); });
    }

    @Transactional
    public void markFailed(String taskId, String errorMsg) {
        GenerationTask t = getOrNull(taskId);
        if (t == null) return;
        t.setStatus("failed");
        t.setErrorMsg(errorMsg);
        t.setStageText("失败");
        t.setFinishTime(LocalDateTime.now());
        taskMapper.updateById(t);
        safePublish(() -> { if (progressBus != null) progressBus.publishFailed(taskId, errorMsg); });
    }

    /** SSE 推送外层兜底 try-catch，杜绝推送异常回滚 markSuccess/markFailed 事务 */
    private void safePublish(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("SSE 推送异常（不影响任务状态）: {}", e.getMessage());
        }
    }

    /** 保存原始上传文件路径（用于重跑） */
    public void saveUploadedFiles(String taskId, List<String> paths) {
        GenerationTask t = getOrNull(taskId);
        if (t == null) return;
        t.setUploadedFiles(JSON.toJSONString(paths));
        taskMapper.updateById(t);
    }

    public void saveUploadedFilesRaw(String taskId, String json) {
        GenerationTask t = getOrNull(taskId);
        if (t == null) return;
        t.setUploadedFiles(json);
        taskMapper.updateById(t);
    }

    public GenerationTask get(String taskId) {
        return getOrNull(taskId);
    }

    public List<GenerationTask> listByCurrent(int limit) {
        Long uid = CurrentUserHolder.currentId();
        boolean isAdmin = CurrentUserHolder.isAdmin();
        LambdaQueryWrapper<GenerationTask> q = new LambdaQueryWrapper<>();
        if (!isAdmin && uid != null) q.eq(GenerationTask::getOwnerId, uid);
        q.orderByDesc(GenerationTask::getCreateTime).last("LIMIT " + Math.min(limit, 200));
        return taskMapper.selectList(q);
    }

    public List<GenerationTask> listRunning() {
        Long uid = CurrentUserHolder.currentId();
        boolean isAdmin = CurrentUserHolder.isAdmin();
        LambdaQueryWrapper<GenerationTask> q = new LambdaQueryWrapper<>();
        if (!isAdmin && uid != null) q.eq(GenerationTask::getOwnerId, uid);
        q.in(GenerationTask::getStatus, "pending", "running")
                .orderByDesc(GenerationTask::getCreateTime);
        return taskMapper.selectList(q);
    }

    private GenerationTask getRequired(String taskId) {
        GenerationTask t = getOrNull(taskId);
        if (t == null) throw new BusinessException("任务不存在: " + taskId);
        return t;
    }

    private GenerationTask getOrNull(String taskId) {
        return taskMapper.selectOne(
                new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getTaskId, taskId).last("LIMIT 1"));
    }
}
