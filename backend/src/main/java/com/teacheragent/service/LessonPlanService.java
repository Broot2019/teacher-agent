package com.teacheragent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.common.UploadedFile;
import com.teacheragent.config.AppProperties;
import com.teacheragent.dto.LessonPlanGenerateRequest;
import com.teacheragent.entity.GenerationTask;
import com.teacheragent.entity.LessonPlanHistory;
import com.teacheragent.entity.LlmConfig;
import com.teacheragent.mapper.LessonPlanHistoryMapper;
import com.teacheragent.mapper.LlmConfigMapper;
import com.teacheragent.service.generation.GenerationParallelizer;
import com.teacheragent.service.generation.GenerationSupport;
import com.teacheragent.service.lessonplan.LessonPlan;
import com.teacheragent.service.lessonplan.LessonPlanData;
import com.teacheragent.service.lessonplan.LessonPlanDocFiller;
import com.teacheragent.service.lessonplan.LessonPlanJsonUtil;
import com.teacheragent.service.lessonplan.LessonPlanPrompts;
import com.teacheragent.service.lessonplan.LessonPlanQualityHelper;
import com.teacheragent.service.lessonplan.LessonPlanSession;
import com.teacheragent.service.lessonplan.PlanItem;
import com.teacheragent.service.lessonplan.ReviewResult;
import com.teacheragent.service.lessonplan.WeekChapter;
import com.teacheragent.service.llm.ChatOptions;
import com.teacheragent.service.llm.LlmClient;
import com.teacheragent.service.llm.LlmClientFactory;
import com.teacheragent.service.retrieval.SourceRetrievalService;
import com.teacheragent.service.teachingplan.TeachingPlanParser;
import com.teacheragent.service.teachingplan.TeachingWeekItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonPlanService extends AbstractGenerationService<LessonPlanGenerateRequest> {

    private final FileParseService fileParseService;
    private final LlmClientFactory llmClientFactory;
    private final LlmConfigMapper llmConfigMapper;
    private final LessonPlanHistoryMapper historyMapper;
    private final LessonPlanDocFiller filler;
    private final TaskService taskService;
    private final TeachingPlanParser teachingPlanParser;
    private final AppProperties props;
    private final ApplicationContext applicationContext;
    private final QuotaService quotaService;
    private final PointService pointService;
    private final SystemConfigService systemConfigService;
    private final CourseConfigService courseConfigService;
    private final GenerationParallelizer parallelizer;
    private final SourceRetrievalService sourceRetrievalService;

    @Override
    protected String taskType() {
        return "lesson_plan";
    }

    /** 通过容器获取自身代理（@Async 自调用必须走代理） */
    private LessonPlanService self() {
        return applicationContext.getBean(LessonPlanService.class);
    }

    /** 根据请求中的 courseConfigId 或当前激活配置构建 CourseContext */
    private LessonPlanPrompts.CourseContext resolveCourseContext(Long courseConfigId) {
        try {
            com.teacheragent.entity.CourseConfig cfg = courseConfigId != null
                    ? courseConfigService.getById(courseConfigId)
                    : courseConfigService.getActive();
            if (cfg == null) return LessonPlanPrompts.CourseContext.defaults();
            LessonPlanPrompts.CourseContext ctx = new LessonPlanPrompts.CourseContext();
            if (cfg.getEducationLevel() != null && !cfg.getEducationLevel().isBlank()) ctx.educationLevel = cfg.getEducationLevel();
            if (cfg.getCourseName() != null && !cfg.getCourseName().isBlank()) ctx.courseName = cfg.getCourseName();
            if (cfg.getTeachingMode() != null && !cfg.getTeachingMode().isBlank()) ctx.teachingMode = cfg.getTeachingMode();
            if (cfg.getStudentDescription() != null && !cfg.getStudentDescription().isBlank()) ctx.studentDescription = cfg.getStudentDescription();
            return ctx;
        } catch (Exception e) {
            return LessonPlanPrompts.CourseContext.defaults();
        }
    }

    /**
     * 提交生成任务（同步快速）
     * 立即创建 task 并把上传文件读入内存，返回 taskId。后台异步执行。
     */
    public GenerationTask submit(List<MultipartFile> pptFiles,
                                 MultipartFile teachingPlanFile,
                                 MultipartFile customTemplate,
                                 LessonPlanGenerateRequest req) {
        Long ownerId = CurrentUserHolder.currentId();
        if (ownerId == null) throw new BusinessException(401, "未登录");

        // 配额检查
        quotaService.checkQuota(ownerId);

        // 校验（必须在扣费之前，避免参数错误也扣积分）
        if ("per_file".equals(req.getMode())) {
            int fileCnt = pptFiles == null ? 0 : (int) pptFiles.stream().filter(f -> f != null && !f.isEmpty()).count();
            if (fileCnt == 0) throw new BusinessException("按文件配置模式需上传至少一个素材文件");
            if (req.getFileConfigs() == null || req.getFileConfigs().isEmpty())
                throw new BusinessException("请为每个文件配置生成参数");
            if (req.getFileConfigs().size() != fileCnt)
                throw new BusinessException("文件数量与配置数量不一致：文件 " + fileCnt + "，配置 " + req.getFileConfigs().size());
            int totalSessions = 0;
            for (var c : req.getFileConfigs()) {
                if (c.getWeekStart() == null || c.getWeekEnd() == null)
                    throw new BusinessException("请填写每个文件的起止周次");
                if (c.getWeekEnd() < c.getWeekStart())
                    throw new BusinessException("结束周不能小于起始周");
                if (c.getSessionCount() == null || c.getSessionCount() <= 0)
                    throw new BusinessException("每个文件至少生成 1 份教案");
                if (c.getSessionCount() > 20)
                    throw new BusinessException("单文件最多生成 20 份教案");
                totalSessions += c.getSessionCount();
            }
            if (totalSessions > 60) throw new BusinessException("单次最多生成 60 份教案，请减少");
        } else if ("range".equals(req.getMode())) {
            if (req.getWeekStart() == null || req.getWeekEnd() == null) {
                throw new BusinessException("按周次范围模式必须填写起止周次");
            }
            if (req.getWeekEnd() < req.getWeekStart()) {
                throw new BusinessException("结束周不能小于起始周");
            }
            if (req.getWeekEnd() - req.getWeekStart() > 16) {
                throw new BusinessException("一次最多生成 16 周");
            }
        } else {
            if (req.getChapter() == null || req.getChapter().isBlank())
                throw new BusinessException("请填写章节标题");
            if (req.getWeekNo() == null || req.getWeekNo().isBlank())
                throw new BusinessException("请填写周次");
        }

        // 积分检查与扣减（校验通过后才扣费）
        int cost;
        String reason;
        if ("per_file".equals(req.getMode())) {
            int totalSessions = req.getFileConfigs().stream().mapToInt(c -> c.getSessionCount() == null ? 0 : c.getSessionCount()).sum();
            cost = pointService.lessonPlanCostPerFile(totalSessions);
            reason = "生成教案: 按文件配置 共" + totalSessions + "份";
        } else {
            cost = pointService.lessonPlanCost(req.getWeekStart(), req.getWeekEnd());
            reason = "生成教案: " + ("range".equals(req.getMode()) ? "第" + req.getWeekStart() + "-" + req.getWeekEnd() + "周" : req.getChapter());
        }
        pointService.consume(ownerId, cost, reason, "lesson_plan", null);

        GenerationTask task = taskService.create("lesson_plan", ownerId, req);
        String taskDir = props.getUploadDir() + "/" + task.getTaskId();
        List<UploadedFile> ppts = new ArrayList<>();
        UploadedFile planFile = null;
        UploadedFile templateFile = null;
        List<String> persistedPaths = new ArrayList<>();
        try {
            ensureDir(taskDir);
            if (pptFiles != null) {
                for (MultipartFile f : pptFiles) {
                    if (f != null && !f.isEmpty()) {
                        String safe = sanitize(f.getOriginalFilename());
                        Path p = Paths.get(taskDir, "ppt_" + safe);
                        f.transferTo(p);
                        ppts.add(new UploadedFile(f.getOriginalFilename(), new byte[0], p.toAbsolutePath().toString()));
                        persistedPaths.add(p.toAbsolutePath().toString());
                    }
                }
            }
            if (teachingPlanFile != null && !teachingPlanFile.isEmpty()) {
                String safe = sanitize(teachingPlanFile.getOriginalFilename());
                Path p = Paths.get(taskDir, "plan_" + safe);
                teachingPlanFile.transferTo(p);
                planFile = new UploadedFile(teachingPlanFile.getOriginalFilename(), new byte[0], p.toAbsolutePath().toString());
                persistedPaths.add(p.toAbsolutePath().toString());
            }
            if (customTemplate != null && !customTemplate.isEmpty()) {
                Path p = Paths.get(taskDir, "tpl_" + sanitize(customTemplate.getOriginalFilename()));
                customTemplate.transferTo(p);
                templateFile = new UploadedFile(customTemplate.getOriginalFilename(), new byte[0], p.toAbsolutePath().toString());
                persistedPaths.add(p.toAbsolutePath().toString());
            }
        } catch (IOException e) {
            throw new BusinessException("保存上传文件失败: " + e.getMessage());
        }

        taskService.saveUploadedFiles(task.getTaskId(), persistedPaths);
        self().executeAsync(task.getTaskId(), ppts, planFile, templateFile, req, ownerId, cost);
        return task;
    }

    /** 重跑任务 */
    public GenerationTask retry(String oldTaskId) {
        Long ownerId = CurrentUserHolder.currentId();
        if (ownerId == null) throw new BusinessException(401, "未登录");
        GenerationTask old = taskService.get(oldTaskId);
        if (old == null) throw new BusinessException("原任务不存在");
        if (!CurrentUserHolder.isAdmin() && !ownerId.equals(old.getOwnerId())) {
            throw new BusinessException(403, "无权操作此任务");
        }
        if (!"lesson_plan".equals(old.getType())) {
            throw new BusinessException("此任务不是教案任务");
        }
        if (old.getParamsJson() == null || old.getParamsJson().isBlank()) {
            throw new BusinessException("原任务参数已丢失，无法重跑");
        }

        quotaService.checkQuota(ownerId);
        LessonPlanGenerateRequest req = JSON.parseObject(old.getParamsJson(), LessonPlanGenerateRequest.class);
        int cost;
        String reason;
        if ("per_file".equals(req.getMode())) {
            int totalSessions = req.getFileConfigs() == null ? 0
                    : req.getFileConfigs().stream().mapToInt(c -> c.getSessionCount() == null ? 0 : c.getSessionCount()).sum();
            cost = pointService.lessonPlanCostPerFile(totalSessions);
            reason = "重跑教案: 按文件配置 共" + totalSessions + "份";
        } else {
            cost = pointService.lessonPlanCost(req.getWeekStart(), req.getWeekEnd());
            reason = "重跑教案: " + ("range".equals(req.getMode()) ? "第" + req.getWeekStart() + "-" + req.getWeekEnd() + "周" : req.getChapter());
        }
        pointService.consume(ownerId, cost, reason, "lesson_plan_retry", null);
        List<UploadedFile> ppts = new ArrayList<>();
        UploadedFile planFile = null;
        UploadedFile templateFile = null;
        if (old.getUploadedFiles() != null && !old.getUploadedFiles().isBlank()) {
            try {
                List<String> paths = JSON.parseArray(old.getUploadedFiles(), String.class);
                for (String p : paths) {
                    File f = new File(p);
                    if (!f.exists()) continue;
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    String origName = f.getName().replaceFirst("^(ppt|plan|tpl)_", "");
                    UploadedFile uf = new UploadedFile(origName, bytes, p);
                    if (f.getName().startsWith("plan_")) planFile = uf;
                    else if (f.getName().startsWith("tpl_")) templateFile = uf;
                    else ppts.add(uf);
                }
            } catch (Exception e) {
                throw new BusinessException("读取原始文件失败: " + e.getMessage());
            }
        }

        GenerationTask newTask = taskService.create("lesson_plan", ownerId, req);
        if (old.getUploadedFiles() != null) taskService.saveUploadedFilesRaw(newTask.getTaskId(), old.getUploadedFiles());
        self().executeAsync(newTask.getTaskId(), ppts, planFile, templateFile, req, ownerId, cost);
        return newTask;
    }

    private String sanitize(String name) {
        return GenerationSupport.sanitize(name);
    }

    @Async("generationExecutor")
    public void executeAsync(String taskId,
                             List<UploadedFile> ppts,
                             UploadedFile planFile,
                             UploadedFile templateFile,
                             LessonPlanGenerateRequest req,
                             Long ownerId,
                             int cost) {
        if ("per_file".equals(req.getMode())) {
            executePerFile(taskId, ppts, templateFile, req, ownerId, cost);
            return;
        }
        try {
            taskService.markRunning(taskId);

            // 1. 解析素材
            taskService.updateProgress(taskId, 5, "解析章节素材...");
            List<String> srcNames = new ArrayList<>();
            List<String[]> parsedPpts = new ArrayList<>();
            for (UploadedFile f : ppts) {
                String text = fileParseService.parseAuto(f.toFile(props.getUploadDir()));
                parsedPpts.add(new String[]{f.name, text});
                srcNames.add(f.name);
            }
            String planText = "";
            if (planFile != null) {
                taskService.updateProgress(taskId, 10, "解析教学计划...");
                planText = fileParseService.parseAuto(planFile.toFile(props.getUploadDir()));
                srcNames.add(planFile.name);
            }
            int sourceBudget = LessonPlanPrompts.getSourceBudget(req.getContentLevel());
            String allPpt = distributeTruncate(parsedPpts, sourceBudget);

            // 2. 选 LLM
            taskService.updateProgress(taskId, 15, "选择大模型...");
            LlmClient client = pickClient(req.getProvider());

            // 2.5 课程上下文
            LessonPlanPrompts.CourseContext courseCtx = resolveCourseContext(req.getCourseConfigId());

            // 3. 决定本次要生成的"周-章节"组合
            List<WeekChapter> targets = new ArrayList<>();
            if ("range".equals(req.getMode())) {
                taskService.updateProgress(taskId, 18, "解析周次章节映射...");
                List<TeachingWeekItem> autoItems = planText.isBlank()
                        ? Collections.emptyList()
                        : teachingPlanParser.parseText(planText);
                for (int w = req.getWeekStart(); w <= req.getWeekEnd(); w++) {
                    String chapter = "";
                    String topics = "";
                    // 优先手动映射
                    if (req.getManualMapping() != null) {
                        for (var m : req.getManualMapping()) {
                            if (m.getWeek() != null && m.getWeek() == w) {
                                chapter = m.getChapter();
                                topics = m.getTopics();
                                break;
                            }
                        }
                    }
                    // 自动映射
                    if (chapter.isBlank()) {
                        TeachingWeekItem item = teachingPlanParser.findByWeek(autoItems, w);
                        if (item != null) {
                            String c = item.getChapter() == null ? "" : item.getChapter();
                            String t = item.getChapterTitle() == null ? "" : item.getChapterTitle();
                            // 防止 chapter 与 chapterTitle 重复
                            if (!t.isBlank() && !c.equals(t) && !t.startsWith(c)) {
                                chapter = (c + " " + t).trim();
                            } else {
                                chapter = t.isBlank() ? c : t;
                            }
                            topics = String.join("\n", item.getTopics());
                        }
                    }
                    if (chapter.isBlank()) chapter = "第 " + w + " 周教学内容";
                    targets.add(new WeekChapter(w, chapter, topics));
                }
            } else {
                // 单章节模式
                targets.add(new WeekChapter(parseInt(req.getWeekNo()), req.getChapter(), ""));
            }

            // 4. 准备模板
            byte[] templateBytes = templateFile != null
                    ? templateFile.bytes
                    : loadDefaultLessonTemplate();

            // 5. 循环为每周生成教案
            int sessionCount = computeSessionCount(req.getPackageMode(), req.getHoursPerWeek());
            int progressBase = 20;
            int progressRange = 70;  // 20-90
            int weekIndex = 0;

            ensureDir(props.getOutputDir());
            ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
            boolean isMultiOutput = targets.size() > 1 || sessionCount > 1;
            List<LessonPlanData> allDataList = new ArrayList<>();

            try (ZipOutputStream zos = isMultiOutput ? new ZipOutputStream(zipOut, java.nio.charset.StandardCharsets.UTF_8) : null) {
                Path singleOutPath = null;
                String singleOutName = null;

                // ===== 并行生成阶段：N 周教案并行调用 LLM =====
                final LlmClient finalClient = client;
                final String systemPrompt = LessonPlanPrompts.getSystemPrompt(req.getContentLevel(), courseCtx);
                final int finalSessionCount = sessionCount;
                final String finalAllPpt = allPpt;
                final String finalPlanText = planText;
                taskService.updateProgress(taskId, progressBase,
                        String.format("并行生成 %d 周教案...", targets.size()));

                List<LessonPlanData> dataList = parallelizer.mapParallel(targets, wc -> {
                    String userPrompt = LessonPlanPrompts.buildUserPrompt(
                            wc.chapter(),
                            buildSourceTextForWeek(finalAllPpt, finalPlanText, wc),
                            String.valueOf(wc.week()),
                            finalSessionCount,
                            req.getClassName(),
                            req.getTeacher(),
                            req.getAcademicYear(),
                            req.getSemester(),
                            req.getContentLevel()
                    );
                    try {
                        String json = finalClient.chatJson(systemPrompt, userPrompt, ChatOptions.creative());
                        LessonPlanData data = LessonPlanJsonUtil.parseLessonPlanJson(json, LessonPlanData.class);
                        if (isBlank(data.getAcademicYear())) data.setAcademicYear(req.getAcademicYear());
                        if (isBlank(data.getSemester())) data.setSemester(req.getSemester());
                        if (isBlank(data.getTeacher())) data.setTeacher(req.getTeacher());
                        if (isBlank(data.getPlanNo())) data.setPlanNo(req.getPlanNo());
                        return data;
                    } catch (Exception e) {
                        log.warn("第 {} 周生成失败: {}", wc.week(), e.getMessage());
                        return null;
                    }
                }, "lesson-range", (done, total) -> {
                    int p = progressBase + (progressRange * done / Math.max(1, total));
                    taskService.updateProgress(taskId, p,
                            String.format("已生成 %d/%d 周教案", done, total));
                });

                taskService.updateProgress(taskId, progressBase + progressRange,
                        String.format("已生成 %d 周教案，渲染 docx...", targets.size()));

                // ===== 串行渲染阶段：保持 ZipOutputStream 顺序与文件名规则不变 =====
                for (int wi = 0; wi < targets.size(); wi++) {
                    WeekChapter wc = targets.get(wi);
                    LessonPlanData data = dataList.get(wi);
                    if (data == null) continue;
                    allDataList.add(data);

                    List<LessonPlanSession> sessions = data.getSessions();
                    if (sessions == null || sessions.isEmpty()) {
                        log.warn("第 {} 周 LLM 未返回 session", wc.week());
                        continue;
                    }

                    for (int i = 0; i < sessions.size(); i++) {
                        LessonPlanData single = cloneWithOneSession(data, sessions.get(i));
                        byte[] docBytes = filler.fill(new ByteArrayInputStream(templateBytes), single);
                        String safeChapter = nullSafe(wc.chapter()).replaceAll("[\\\\/:*?\"<>|]", "_");
                        String entryName = sessions.size() == 1 && targets.size() == 1
                                ? String.format("教案_第%d周_%s.docx", wc.week(), safeChapter)
                                : String.format("第%d周/教案_第%d周_第%d次_%s.docx", wc.week(), wc.week(), i + 1, safeChapter);

                        if (zos != null) {
                            zos.putNextEntry(new ZipEntry(entryName));
                            zos.write(docBytes);
                            zos.closeEntry();
                        } else {
                            singleOutName = entryName;
                            singleOutPath = Paths.get(props.getOutputDir(), entryName.replace('/', '_'));
                            Files.write(singleOutPath, docBytes);
                        }
                    }
                }

                taskService.updateProgress(taskId, 92, "打包文件...");

                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String outputFileName;
                Path outPath;

                if (isMultiOutput) {
                    String range = "range".equals(req.getMode())
                            ? String.format("第%d-%d周", req.getWeekStart(), req.getWeekEnd())
                            : String.format("第%s周", req.getWeekNo());
                    outputFileName = String.format("教案_%s_共%d周_%s.zip",
                            range, targets.size(), stamp);
                    outPath = Paths.get(props.getOutputDir(), outputFileName);
                    if (zos != null) zos.finish();
                    Files.write(outPath, zipOut.toByteArray());
                } else {
                    if (singleOutPath == null) throw new BusinessException("未生成任何教案文件");
                    outPath = singleOutPath;
                    outputFileName = singleOutName;
                }

                // 6. 写历史记录
                LessonPlanHistory history = new LessonPlanHistory();
                history.setChapter("range".equals(req.getMode())
                        ? String.format("第%d-%d周（按周次范围）", req.getWeekStart(), req.getWeekEnd())
                        : req.getChapter());
                history.setWeekNo(req.getWeekNo());
                history.setWeekStart(req.getWeekStart());
                history.setWeekEnd(req.getWeekEnd());
                history.setHoursPerWeek(req.getHoursPerWeek());
                history.setPackageMode(req.getPackageMode());
                history.setLlmProvider(client.getProvider());
                history.setLlmModel(client.getModelName());
                history.setSourceFiles(String.join(",", srcNames));
                history.setOutputFilePath(outPath.toAbsolutePath().toString());
                history.setOutputFileName(outputFileName);
                history.setStatus("success");
                history.setOwnerId(ownerId);
                history.setTaskId(taskId);
                historyMapper.insert(history);

                // 保存预览 JSON（用于"内容预览"功能）
                try {
                    Path jsonPath = Paths.get(outPath.toAbsolutePath() + ".json");
                    Files.writeString(jsonPath, JSON.toJSONString(allDataList), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.warn("保存预览 JSON 失败: {}", e.getMessage());
                }

                taskService.markSuccess(taskId, history.getId(), "完成 " + targets.size() + " 周教案生成");
            }
        } catch (Exception e) {
            log.error("教案任务执行失败 taskId={}", taskId, e);
            // 各自隔离：markFailed 与 refund 互不影响，任一抛错都不应吞掉对方
            try { taskService.markFailed(taskId, e.getMessage()); }
            catch (Exception markEx) { log.error("markFailed 失败 taskId={}", taskId, markEx); }
            try { pointService.refund(ownerId, cost, "教案生成失败退回", "lesson_plan_refund", taskId); }
            catch (Exception rfEx) { log.error("refund 失败 taskId={} userId={} amount={}", taskId, ownerId, cost, rfEx); }
        }
    }

    /** 为某周构造 source text：包含教学计划原文 + 经 BM25 精筛的 PPT 内容 */
    private String buildSourceTextForWeek(String allPpt, String planText, WeekChapter wc) {
        StringBuilder sb = new StringBuilder();
        if (wc.topics() != null && !wc.topics().isBlank()) {
            sb.append("【本周教学计划要点】\n").append(wc.topics()).append("\n\n");
        }
        if (planText != null && !planText.isBlank()) {
            sb.append("【教学计划全文（请参考与「")
                    .append(wc.chapter()).append("」相关的部分）】\n");
            sb.append(planText.length() > 3000 ? planText.substring(0, 3000) + "\n...(已截断)" : planText);
            sb.append("\n\n");
        }
        sb.append("【章节 PPT 文本（已按章节主题精筛，请围绕「")
                .append(wc.chapter()).append("」生成）】\n");
        // 走 BM25 检索精筛：query = 章节标题 + 周次主题（topics）
        java.util.List<String> kps = new java.util.ArrayList<>();
        if (wc.topics() != null && !wc.topics().isBlank()) {
            for (String line : wc.topics().split("\n")) {
                String t = line.trim();
                if (!t.isEmpty()) kps.add(t);
            }
        }
        // budget 取 LessonPlanPrompts.getSourceBudget 的 70% 给精筛素材，留 30% 给上面的教学计划摘要
        int retrievalBudget = Math.max(2000, (int) (allPpt == null ? 0 : allPpt.length() * 0.7));
        if (allPpt != null && !allPpt.isBlank()) {
            sb.append(sourceRetrievalService.retrieveForLessonPlan(wc.chapter(), kps, allPpt, retrievalBudget));
        }
        return sb.toString();
    }

    /** 多文件按比例分配截断（委托给 GenerationSupport，含句末边界回退） */
    private String distributeTruncate(List<String[]> fileTexts, int budget) {
        return GenerationSupport.distributeTruncate(fileTexts, budget);
    }

    /**
     * per_file 模式执行：每个文件独立解析 + 按 fileConfig 在 weekStart..weekEnd 均分 sessionCount 份教案，
     * 全部教案 docx 合并为一份输出。
     */
    private void executePerFile(String taskId,
                                List<UploadedFile> ppts,
                                UploadedFile templateFile,
                                LessonPlanGenerateRequest req,
                                Long ownerId,
                                int cost) {
        try {
            taskService.markRunning(taskId);
            taskService.updateProgress(taskId, 5, "解析章节素材...");

            List<LessonPlanGenerateRequest.FileGenerationConfig> configs = req.getFileConfigs();
            int sourceBudget = LessonPlanPrompts.getSourceBudget(req.getContentLevel());

            // 解析每个 PPT 文件 → text（按 fileIndex 对齐）
            int n = ppts.size();
            String[] fileTexts = new String[n];
            String[] fileNames = new String[n];
            for (int i = 0; i < n; i++) {
                UploadedFile f = ppts.get(i);
                fileTexts[i] = fileParseService.parseAuto(f.toFile(props.getUploadDir()));
                fileNames[i] = f.name;
            }
            taskService.updateProgress(taskId, 12, "已解析 " + n + " 个文件");

            // 选 LLM
            taskService.updateProgress(taskId, 15, "选择大模型...");
            LlmClient client = pickClient(req.getProvider());

            // 课程上下文
            LessonPlanPrompts.CourseContext courseCtx = resolveCourseContext(req.getCourseConfigId());

            // 模板
            byte[] templateBytes = templateFile != null ? templateFile.bytes : loadDefaultLessonTemplate();

            // 计算总 session 数
            int totalSessions = configs.stream().mapToInt(c -> c.getSessionCount() == null ? 0 : c.getSessionCount()).sum();
            int doneSessions = 0;
            int progressBase = 18;
            int progressRange = 72;  // 18 - 90

            List<byte[]> docBytes = new ArrayList<>();
            List<LessonPlanData> allDataList = new ArrayList<>();
            List<String> srcNames = new ArrayList<>();

            for (int ci = 0; ci < configs.size(); ci++) {
                LessonPlanGenerateRequest.FileGenerationConfig cfg = configs.get(ci);
                int idx = cfg.getFileIndex() == null ? ci : cfg.getFileIndex();
                if (idx < 0 || idx >= n) {
                    log.warn("fileIndex {} 越界（n={}），跳过", idx, n);
                    continue;
                }
                String fileText = fileTexts[idx];
                String fileName = fileNames[idx];
                srcNames.add(fileName);

                String chapter = (cfg.getChapter() == null || cfg.getChapter().isBlank())
                        ? stripExt(fileName) : cfg.getChapter();
                int wStart = cfg.getWeekStart();
                int wEnd = cfg.getWeekEnd();
                int sessions = cfg.getSessionCount();
                int weeks = wEnd - wStart + 1;
                int[] weekAssignment = distributeSessionsToWeeks(sessions, wStart, weeks);

                String truncatedSource = truncateText(fileText, sourceBudget);

                // ===== 阶段 1：让 LLM 先做"知识点规划"——把本文件素材切成 sessions 份不重复的知识点 =====
                taskService.updateProgress(taskId, 18 + (progressRange * doneSessions / Math.max(1, totalSessions)),
                        String.format("[%s] 规划 %d 份教案知识点...", chapter, sessions));
                LessonPlan planResult;
                try {
                    String planningPrompt = LessonPlanPrompts.buildPlanningPrompt(
                            chapter, truncatedSource, weekAssignment, sessions, req.getContentLevel());
                    String planJson = client.chatJson(LessonPlanPrompts.SYSTEM_PROMPT_PLANNING, planningPrompt);
                    planResult = LessonPlanJsonUtil.parsePlanJson(planJson, sessions, weekAssignment, chapter);
                } catch (Exception e) {
                    log.warn("[{}] 知识点规划失败，将退回兜底分配: {}", chapter, e.getMessage());
                    planResult = LessonPlanJsonUtil.buildFallbackPlan(sessions, weekAssignment, chapter);
                }

                // ===== 阶段 2：并行生成每份教案，传入"本份知识点 + 其他份知识点"以避免重复 =====
                final LessonPlan finalPlanResult = planResult;
                final String chapterFinal = chapter;
                final String fileNameFinal = fileName;
                final String truncatedSourceFinal = truncatedSource;
                final LlmClient finalClient = client;
                final String systemPromptFinal = LessonPlanPrompts.getSystemPrompt(req.getContentLevel(), courseCtx);

                taskService.updateProgress(taskId,
                        progressBase + (progressRange * doneSessions / Math.max(1, totalSessions)),
                        String.format("[%s] 并行生成 %d 份教案...", chapter, sessions));

                // 构造每份的输入索引列表
                List<Integer> indices = new ArrayList<>();
                for (int s = 0; s < sessions; s++) indices.add(s);

                // 携带本份索引 + userPrompt 的并行任务
                record SessionTask(int sIndex, int week, PlanItem currentPlan, String userPrompt) {}
                List<SessionTask> sessionTasks = new ArrayList<>();
                for (int s = 0; s < sessions; s++) {
                    int week = weekAssignment[s];
                    PlanItem currentPlan = finalPlanResult.plans.get(s);
                    java.util.List<String> otherSubTitles = new ArrayList<>();
                    java.util.List<String> otherKps = new ArrayList<>();
                    for (int j = 0; j < finalPlanResult.plans.size(); j++) {
                        if (j == s) continue;
                        PlanItem o = finalPlanResult.plans.get(j);
                        otherSubTitles.add("第" + o.week + "周·" + o.subTitle);
                        if (o.knowledgePoints != null) otherKps.addAll(o.knowledgePoints);
                    }
                    String userPrompt = LessonPlanPrompts.buildLessonPromptWithPlan(
                            chapterFinal,
                            buildPerFileSourceText(chapterFinal, fileNameFinal, truncatedSourceFinal),
                            week,
                            req.getClassName(),
                            req.getTeacher(),
                            req.getAcademicYear(),
                            req.getSemester(),
                            req.getContentLevel(),
                            currentPlan.subTitle,
                            currentPlan.knowledgePoints,
                            currentPlan.focus,
                            finalPlanResult.overview,
                            otherSubTitles,
                            otherKps
                    );
                    sessionTasks.add(new SessionTask(s, week, currentPlan, userPrompt));
                }

                // 并行调用 LLM 生成每份教案（重生暂沿用串行后处理：见下方）
                final int doneOffset = doneSessions;  // 闭包捕获当前已完成数
                final int totalSessionsFinal = totalSessions;
                List<LessonPlanData> sessionResults = parallelizer.mapParallel(sessionTasks, t -> {
                    try {
                        String json = finalClient.chatJson(systemPromptFinal, t.userPrompt(), ChatOptions.creative());
                        return LessonPlanJsonUtil.parseLessonPlanJson(json, LessonPlanData.class);
                    } catch (Exception e) {
                        log.warn("[{}] 第 {} 周教案生成失败: {}", chapterFinal, t.week(), e.getMessage());
                        return null;
                    }
                }, "lesson-perfile-" + chapterFinal, (done, total) -> {
                    int globalDone = doneOffset + done;
                    int p = progressBase + (progressRange * globalDone / Math.max(1, totalSessionsFinal));
                    taskService.updateProgress(taskId, p,
                            String.format("[%s] 已生成 %d/%d 份教案", chapterFinal, done, total));
                });

                // 串行后处理：合规校验 + 评审 + 重生（重生仍走串行，量小且偶发）
                boolean complianceEnabled = systemConfigService.getBool("lesson_plan_compliance_check_enabled", true);
                boolean critiqueEnabled = systemConfigService.getBoolWithFallback(
                        "lesson_plan_batch_review_enabled", "lesson_plan_self_critique_enabled", true);
                // 新 key 是否显式开启批量评审（true = 用 reviewBatch 一次评 N 份；false = 逐份评）
                boolean batchReviewEnabled = systemConfigService.getBool(
                        "lesson_plan_batch_review_enabled", false);

                // 批量评审路径：在 sessionResults 全部就绪后一次性评审，得到 reviewResults
                List<ReviewResult> reviewResults = null;
                if (critiqueEnabled && batchReviewEnabled) {
                    java.util.List<LessonPlanData> validData = new ArrayList<>();
                    java.util.List<String> subTitles = new ArrayList<>();
                    java.util.List<java.util.List<String>> kpsList = new ArrayList<>();
                    java.util.List<Integer> validIndices = new ArrayList<>();
                    for (int s = 0; s < sessions; s++) {
                        LessonPlanData d = sessionResults.get(s);
                        if (d == null) continue;
                        validData.add(d);
                        subTitles.add(sessionTasks.get(s).currentPlan().subTitle);
                        kpsList.add(sessionTasks.get(s).currentPlan().knowledgePoints);
                        validIndices.add(s);
                    }
                    if (!validData.isEmpty()) {
                        try {
                            List<ReviewResult> batchResults = LessonPlanQualityHelper.reviewBatch(
                                    finalClient, validData, req.getContentLevel(), subTitles, kpsList);
                            // 把 batchResults 按原 sessionTasks 索引回填
                            ReviewResult[] arr = new ReviewResult[sessions];
                            for (int i = 0; i < validIndices.size() && i < batchResults.size(); i++) {
                                arr[validIndices.get(i)] = batchResults.get(i);
                            }
                            reviewResults = java.util.Arrays.asList(arr);
                        } catch (Exception e) {
                            log.warn("[{}] 批量评审失败，回退逐份评审: {}", chapterFinal, e.getMessage());
                        }
                    }
                }

                for (int s = 0; s < sessions; s++) {
                    SessionTask t = sessionTasks.get(s);
                    LessonPlanData data = sessionResults.get(s);
                    if (data == null) {
                        doneSessions++;
                        continue;
                    }

                    java.util.List<String> issues = complianceEnabled
                            ? LessonPlanQualityHelper.validateLessonPlanData(data, req.getContentLevel())
                            : java.util.Collections.emptyList();
                    boolean needRegen = !issues.isEmpty();
                    String reviewAdvice = "";
                    double reviewScore = 10.0;

                    if (!needRegen && critiqueEnabled) {
                        try {
                            ReviewResult rr;
                            if (reviewResults != null && reviewResults.get(s) != null) {
                                // 批量评审命中
                                rr = reviewResults.get(s);
                            } else {
                                // 逐份评审兜底
                                rr = LessonPlanQualityHelper.reviewLessonPlanData(finalClient, data, req.getContentLevel(),
                                        t.currentPlan().subTitle, t.currentPlan().knowledgePoints);
                            }
                            reviewScore = rr.overall;
                            reviewAdvice = rr.advice;
                            if ("regen".equalsIgnoreCase(rr.action) || rr.overall < 7.0) {
                                needRegen = true;
                            }
                        } catch (Exception ex) {
                            log.warn("[{}] 第 {} 周自检调用失败，跳过自检: {}", chapterFinal, t.week(), ex.getMessage());
                        }
                    }

                    if (needRegen) {
                        log.info("[{}] 第 {} 周教案触发重生 issues={} score={} advice={}",
                                chapterFinal, t.week(), issues, reviewScore, reviewAdvice);
                        try {
                            String fixHint = (issues.isEmpty() ? "" : "请修复以下合规问题：" + String.join("；", issues) + "。")
                                    + (reviewAdvice.isBlank() || "无".equals(reviewAdvice) ? "" : "请按以下建议改进：" + reviewAdvice);
                            String regenPrompt = t.userPrompt() + "\n\n【重生要求】" + fixHint;
                            String json = finalClient.chatJson(systemPromptFinal, regenPrompt, ChatOptions.creative());
                            LessonPlanData regenData = LessonPlanJsonUtil.parseLessonPlanJson(json, LessonPlanData.class);
                            if (regenData.getSessions() != null && !regenData.getSessions().isEmpty()) {
                                data = regenData;
                            }
                        } catch (Exception ex) {
                            log.warn("[{}] 第 {} 周教案重生失败，沿用首份: {}", chapterFinal, t.week(), ex.getMessage());
                        }
                    }

                    if (isBlank(data.getAcademicYear())) data.setAcademicYear(req.getAcademicYear());
                    if (isBlank(data.getSemester())) data.setSemester(req.getSemester());
                    if (isBlank(data.getTeacher())) data.setTeacher(req.getTeacher());
                    if (isBlank(data.getPlanNo())) data.setPlanNo(req.getPlanNo());
                    allDataList.add(data);

                    List<LessonPlanSession> sList = data.getSessions();
                    if (sList == null || sList.isEmpty()) {
                        log.warn("[{}] 第 {} 周 LLM 未返回 session", chapterFinal, t.week());
                        doneSessions++;
                        continue;
                    }

                    LessonPlanData single = cloneWithOneSession(data, sList.get(0));
                    byte[] doc = filler.fill(new ByteArrayInputStream(templateBytes), single);
                    docBytes.add(doc);
                    doneSessions++;
                }
            }

            if (docBytes.isEmpty()) {
                throw new BusinessException("LLM 未生成任何有效教案，请检查素材或重试");
            }

            taskService.updateProgress(taskId, 92, "合并教案到一份文档...");
            byte[] merged = (req.getMergeIntoOne() == null || req.getMergeIntoOne())
                    ? filler.merge(docBytes)
                    : zipDocs(docBytes);

            ensureDir(props.getOutputDir());
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            boolean asZip = !(req.getMergeIntoOne() == null || req.getMergeIntoOne());
            String outputFileName = asZip
                    ? String.format("教案_按文件配置_共%d份_%s.zip", docBytes.size(), stamp)
                    : String.format("教案_按文件配置_共%d份_%s.docx", docBytes.size(), stamp);
            Path outPath = Paths.get(props.getOutputDir(), outputFileName);
            Files.write(outPath, merged);

            // 写历史
            LessonPlanHistory history = new LessonPlanHistory();
            int allMinWeek = configs.stream().mapToInt(LessonPlanGenerateRequest.FileGenerationConfig::getWeekStart).min().orElse(0);
            int allMaxWeek = configs.stream().mapToInt(LessonPlanGenerateRequest.FileGenerationConfig::getWeekEnd).max().orElse(0);
            history.setChapter(String.format("按文件配置（%d个文件，第%d-%d周共%d份）", configs.size(), allMinWeek, allMaxWeek, docBytes.size()));
            history.setWeekStart(allMinWeek);
            history.setWeekEnd(allMaxWeek);
            history.setHoursPerWeek(req.getHoursPerWeek());
            history.setPackageMode("per_file");
            history.setLlmProvider(client.getProvider());
            history.setLlmModel(client.getModelName());
            history.setSourceFiles(String.join(",", srcNames));
            history.setOutputFilePath(outPath.toAbsolutePath().toString());
            history.setOutputFileName(outputFileName);
            history.setStatus("success");
            history.setOwnerId(ownerId);
            history.setTaskId(taskId);
            historyMapper.insert(history);

            try {
                Path jsonPath = Paths.get(outPath.toAbsolutePath() + ".json");
                Files.writeString(jsonPath, JSON.toJSONString(allDataList), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("保存预览 JSON 失败: {}", e.getMessage());
            }

            taskService.markSuccess(taskId, history.getId(), "完成 " + docBytes.size() + " 份教案生成");
        } catch (Exception e) {
            log.error("教案任务执行失败 (per_file) taskId={}", taskId, e);
            try { taskService.markFailed(taskId, e.getMessage()); }
            catch (Exception markEx) { log.error("markFailed 失败 taskId={}", taskId, markEx); }
            try { pointService.refund(ownerId, cost, "教案生成失败退回", "lesson_plan_refund", taskId); }
            catch (Exception rfEx) { log.error("refund 失败 taskId={} userId={} amount={}", taskId, ownerId, cost, rfEx); }
        }
    }

    /** 把 sessionCount 份教案均分到 weeks 周内，返回每份所属周次（长度 = sessionCount） */
    private int[] distributeSessionsToWeeks(int sessionCount, int weekStart, int weeks) {
        int[] r = new int[sessionCount];
        if (weeks <= 0) weeks = 1;
        // 商均匀分配，余数从前面的周次开始多分一份
        int base = sessionCount / weeks;
        int extra = sessionCount % weeks;
        int p = 0;
        for (int w = 0; w < weeks; w++) {
            int cnt = base + (w < extra ? 1 : 0);
            for (int k = 0; k < cnt && p < sessionCount; k++) {
                r[p++] = weekStart + w;
            }
        }
        // 兜底：若 sessionCount < weeks，前 sessionCount 周各 1 份
        if (sessionCount < weeks) {
            for (int i = 0; i < sessionCount; i++) r[i] = weekStart + i;
        }
        return r;
    }

    private String buildPerFileSourceText(String chapter, String fileName, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("【文件: ").append(fileName).append("】\n");
        sb.append("【章节: ").append(chapter).append("】\n");
        sb.append("【内容（已按章节主题精筛）】\n");
        // 走 BM25 检索精筛：query = chapter
        int budget = Math.max(2000, text == null ? 0 : Math.min(text.length(), 6000));
        if (text != null && !text.isBlank()) {
            sb.append(sourceRetrievalService.retrieveForLessonPlan(chapter, java.util.Collections.emptyList(), text, budget));
        }
        return sb.toString();
    }

    private String truncateText(String s, int max) {
        return GenerationSupport.truncateText(s, max);
    }

    private String stripExt(String name) {
        return GenerationSupport.stripExt(name);
    }

    /** per_file 模式不合并时的备选打包：ZIP 单独 docx */
    private byte[] zipDocs(List<byte[]> docs) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, java.nio.charset.StandardCharsets.UTF_8)) {
            for (int i = 0; i < docs.size(); i++) {
                zos.putNextEntry(new ZipEntry(String.format("教案_第%02d份.docx", i + 1)));
                zos.write(docs.get(i));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private byte[] loadDefaultLessonTemplate() throws IOException {
        try (InputStream is = new ClassPathResource("reference/教案模板.docx").getInputStream()) {
            return is.readAllBytes();
        }
    }

    private int computeSessionCount(String packageMode, Integer hoursPerWeek) {
        if (packageMode == null) packageMode = "weekly";
        return switch (packageMode) {
            case "single" -> 1;
            case "full"   -> 3;
            default       -> hoursPerWeek != null && hoursPerWeek == 2 ? 1 : 2;
        };
    }

    private LessonPlanData cloneWithOneSession(LessonPlanData src, LessonPlanSession sess) {
        LessonPlanData d = new LessonPlanData();
        d.setAcademicYear(src.getAcademicYear());
        d.setSemester(src.getSemester());
        d.setTeacher(src.getTeacher());
        d.setPlanNo(src.getPlanNo());
        d.setTeachingResource(src.getTeachingResource());
        d.setHomework(src.getHomework());
        d.setSessions(Collections.singletonList(sess));
        return d;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String nullSafe(String s) { return s == null ? "" : s; }
    private int parseInt(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim().split("[^\\d]")[0]); } catch (Exception e) { return 0; }
    }

    private void ensureDir(String path) throws IOException {
        GenerationSupport.ensureDir(path);
    }

    /** 教师只看自己的，admin 看全部 */
    public List<LessonPlanHistory> listHistory(int limit) {
        LambdaQueryWrapper<LessonPlanHistory> q = new LambdaQueryWrapper<>();
        if (!CurrentUserHolder.isAdmin() && CurrentUserHolder.currentId() != null) {
            q.eq(LessonPlanHistory::getOwnerId, CurrentUserHolder.currentId());
        }
        q.orderByDesc(LessonPlanHistory::getCreateTime).last("LIMIT " + Math.min(limit, 200));
        return historyMapper.selectList(q);
    }

    public LessonPlanHistory getById(Long id) {
        LessonPlanHistory h = historyMapper.selectById(id);
        if (h == null) return null;
        // 数据隔离
        if (!CurrentUserHolder.isAdmin() && CurrentUserHolder.currentId() != null
                && !CurrentUserHolder.currentId().equals(h.getOwnerId())) {
            throw new BusinessException(403, "无权下载该文件");
        }
        return h;
    }

    /** 获取教案预览 JSON（List<LessonPlanData>） */
    public List<LessonPlanData> preview(Long historyId) {
        LessonPlanHistory h = getById(historyId);
        if (h == null) throw new BusinessException("记录不存在");
        if (h.getOutputFilePath() == null) return new ArrayList<>();
        Path jsonPath = Paths.get(h.getOutputFilePath() + ".json");
        if (!Files.exists(jsonPath)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(jsonPath, java.nio.charset.StandardCharsets.UTF_8);
            return JSON.parseArray(json, LessonPlanData.class);
        } catch (Exception e) {
            log.warn("读取预览 JSON 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 删除历史记录（逻辑删除） */
    @Transactional
    public void deleteById(Long id) {
        LessonPlanHistory h = historyMapper.selectById(id);
        if (h == null) throw new BusinessException("记录不存在");
        if (!CurrentUserHolder.isAdmin() && CurrentUserHolder.currentId() != null
                && !CurrentUserHolder.currentId().equals(h.getOwnerId())) {
            throw new BusinessException(403, "无权删除");
        }
        historyMapper.deleteById(id);
    }
}
