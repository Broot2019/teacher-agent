package com.teacheragent.service;

import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.common.UploadedFile;
import com.teacheragent.config.AppProperties;
import com.teacheragent.entity.CourseConfig;
import com.teacheragent.mapper.LlmConfigMapper;
import com.teacheragent.service.generation.GenerationSupport;
import com.teacheragent.service.llm.LlmClient;
import com.teacheragent.service.llm.LlmClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 教案 / 题库生成服务的共享基类。
 *
 * <p>承担横切公共职责：
 * <ul>
 *     <li>登录检查 + 配额检查 + 当前用户上下文</li>
 *     <li>任务目录创建 + 上传文件持久化</li>
 *     <li>{@link #runWithRefund} 包裹任务体，失败时 markFailed 与 refund 各自隔离</li>
 *     <li>课程上下文解析 + LLM 客户端选择</li>
 * </ul>
 *
 * <p>子类（{@code LessonPlanService}、{@code QuestionBankService}）保留各自 submit/retry
 * 公开方法签名（Controller 直接绑定参数），内部仅调用本基类的 protected 方法完成共享流程。
 *
 * <p>设计原则：基类用 {@code @Autowired} 字段注入避免侵入子类的 RequiredArgsConstructor 风格；
 * 子类的 final 依赖（{@code historyMapper}、{@code filler} 等）继续走 lombok 构造注入。
 *
 * @param <TReq> 子类的请求 DTO 类型（仅作类型标记，便于未来 Phase 加 doExecute 钩子）
 */
@Slf4j
public abstract class AbstractGenerationService<TReq> {

    @Autowired protected QuotaService quotaService;
    @Autowired protected PointService pointService;
    @Autowired protected TaskService taskService;
    @Autowired protected AppProperties props;
    @Autowired protected LlmClientFactory llmClientFactory;
    @Autowired protected LlmConfigMapper llmConfigMapper;
    @Autowired protected SystemConfigService systemConfigService;
    @Autowired protected CourseConfigService courseConfigService;

    /** "lesson_plan" / "question_bank" — 用于 task 创建与积分明细的 ref_type */
    protected abstract String taskType();

    /** 检查登录与配额，返回 ownerId */
    protected final Long requireLoggedInWithQuota() {
        Long ownerId = CurrentUserHolder.currentId();
        if (ownerId == null) throw new BusinessException(401, "未登录");
        quotaService.checkQuota(ownerId);
        return ownerId;
    }

    /** 创建任务目录，返回路径 */
    protected final String createTaskDir(String taskId) {
        String taskDir = props.getUploadDir() + "/" + taskId;
        try {
            GenerationSupport.ensureDir(taskDir);
        } catch (IOException e) {
            throw new BusinessException("创建任务目录失败: " + e.getMessage());
        }
        return taskDir;
    }

    /**
     * 持久化单个 MultipartFile 到 taskDir，按用途加前缀（ppt_/plan_/tpl_）。
     * @return 持久化后的 UploadedFile（path 为绝对路径，bytes 为空数组以节约内存）；file 为空时返回 null
     */
    protected final UploadedFile persistFile(String taskDir, MultipartFile file, String prefix,
                                             List<String> persistedPaths) {
        if (file == null || file.isEmpty()) return null;
        String safe = GenerationSupport.sanitize(file.getOriginalFilename());
        Path p = Paths.get(taskDir, prefix + safe);
        try {
            file.transferTo(p);
        } catch (IOException e) {
            throw new BusinessException("保存文件失败: " + e.getMessage());
        }
        persistedPaths.add(p.toAbsolutePath().toString());
        return new UploadedFile(file.getOriginalFilename(), new byte[0], p.toAbsolutePath().toString());
    }

    /**
     * 包裹任务执行体：捕获任意异常，先 markFailed 再 refund，两者隔离避免一方失败吞掉另一方。
     */
    protected final void runWithRefund(String taskId, Long ownerId, int cost, String refundReason,
                                       ThrowingRunnable body) {
        try {
            body.run();
        } catch (Exception e) {
            log.error("[{}] 任务执行失败 taskId={}", taskType(), taskId, e);
            try {
                taskService.markFailed(taskId, e.getMessage());
            } catch (Exception markEx) {
                log.error("[{}] markFailed 失败 taskId={}", taskType(), taskId, markEx);
            }
            try {
                pointService.refund(ownerId, cost, refundReason, taskType() + "_refund", taskId);
            } catch (Exception rfEx) {
                log.error("[{}] refund 失败 taskId={} userId={} amount={}",
                        taskType(), taskId, ownerId, cost, rfEx);
            }
        }
    }

    /** 解析课程配置（指定 ID 或当前激活） */
    protected final CourseConfig resolveCourseConfig(Long courseConfigId) {
        try {
            return courseConfigId != null
                    ? courseConfigService.getById(courseConfigId)
                    : courseConfigService.getActive();
        } catch (Exception e) {
            return null;
        }
    }

    /** 按 provider 选客户端（provider 为空走激活的） */
    protected final LlmClient pickClient(String provider) {
        return GenerationSupport.pickClient(llmClientFactory, llmConfigMapper, provider);
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
