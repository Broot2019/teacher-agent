package com.teacheragent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.common.UploadedFile;
import com.teacheragent.config.AppProperties;
import com.teacheragent.dto.QuestionBankGenerateRequest;
import com.teacheragent.entity.GenerationTask;
import com.teacheragent.entity.LlmConfig;
import com.teacheragent.entity.QuestionBankHistory;
import com.teacheragent.mapper.LlmConfigMapper;
import com.teacheragent.mapper.QuestionBankHistoryMapper;
import com.teacheragent.service.generation.GenerationParallelizer;
import com.teacheragent.service.generation.GenerationSupport;
import com.teacheragent.service.lessonplan.LessonPlanJsonUtil;
import com.teacheragent.service.llm.ChatOptions;
import com.teacheragent.service.llm.LlmClient;
import com.teacheragent.service.llm.LlmClientFactory;
import com.teacheragent.service.questionbank.Question;
import com.teacheragent.service.questionbank.QuestionBankPrompts;
import com.teacheragent.service.questionbank.QuestionBankXlsxFiller;
import com.teacheragent.service.retrieval.SourceRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankService extends AbstractGenerationService<QuestionBankGenerateRequest> {

    private final FileParseService fileParseService;
    private final LlmClientFactory llmClientFactory;
    private final LlmConfigMapper llmConfigMapper;
    private final QuestionBankHistoryMapper historyMapper;
    private final QuestionBankXlsxFiller filler;
    private final TaskService taskService;
    private final AppProperties props;
    private final ApplicationContext applicationContext;
    private final com.teacheragent.mapper.QuestionItemMapper questionItemMapper;
    private final QuotaService quotaService;
    private final PointService pointService;
    private final SystemConfigService systemConfigService;
    private final com.teacheragent.service.questionbank.JavaCompilerService javaCompilerService;
    private final com.teacheragent.service.questionbank.CodeCompilerRouter codeCompilerRouter;
    private final CourseConfigService courseConfigService;
    private final GenerationParallelizer parallelizer;
    private final SourceRetrievalService sourceRetrievalService;

    @Override
    protected String taskType() {
        return "question_bank";
    }

    private QuestionBankService self() {
        return applicationContext.getBean(QuestionBankService.class);
    }

    private String resolveCourseName(Long courseConfigId) {
        try {
            com.teacheragent.entity.CourseConfig cfg = courseConfigId != null
                    ? courseConfigService.getById(courseConfigId)
                    : courseConfigService.getActive();
            if (cfg != null && cfg.getCourseName() != null && !cfg.getCourseName().isBlank()) {
                return cfg.getCourseName();
            }
        } catch (Exception ignored) {}
        return QuestionBankPrompts.DEFAULT_COURSE_NAME;
    }

    /**
     * 解析当前编程题语言，优先级：
     * 1. 请求显式 programmingLanguage（用户在生成表单单次覆盖）
     * 2. CourseConfig.programmingLanguage（课程配置）
     * 3. 从 sourceText 自动检测（看素材里的语言关键字）
     * 4. 默认 java
     *
     * @param requestLanguage 请求 DTO 中的 programmingLanguage（可空）
     * @param courseConfigId  课程配置 ID（可空走 active）
     * @param sourceText      已解析的素材文本，用于自动检测兜底（可空跳过检测）
     */
    private String resolveProgrammingLanguage(String requestLanguage, Long courseConfigId, String sourceText) {
        // 1. 请求显式覆盖
        if (requestLanguage != null && !requestLanguage.isBlank()) {
            return requestLanguage.toLowerCase().trim();
        }
        // 2. 课程配置
        try {
            com.teacheragent.entity.CourseConfig cfg = courseConfigId != null
                    ? courseConfigService.getById(courseConfigId)
                    : courseConfigService.getActive();
            if (cfg != null && cfg.getProgrammingLanguage() != null && !cfg.getProgrammingLanguage().isBlank()) {
                return cfg.getProgrammingLanguage().toLowerCase().trim();
            }
        } catch (Exception ignored) {}
        // 3. 自动检测
        String detected = detectLanguageFromSource(sourceText);
        if (detected != null) {
            log.info("课程配置未指定编程语言，从素材自动检测到: {}", detected);
            return detected;
        }
        // 4. 默认
        return "java";
    }

    /**
     * 从素材文本中检测主导编程语言（轻量关键字计数）。
     * 返回 null 表示无明显证据，由调用方走默认值。
     */
    private String detectLanguageFromSource(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.length() > 20000 ? text.substring(0, 20000) : text;
        Map<String, Integer> scores = new java.util.HashMap<>();
        // python：def / import / print(
        scores.put("python", count(t, "def ") * 3 + count(t, "import ") * 2
                + count(t, "print(") * 2 + count(t, "if __name__") * 5 + count(t, "elif ") * 3);
        // java：public class / System.out / public static void main / @Override
        scores.put("java", count(t, "public class") * 5 + count(t, "System.out") * 3
                + count(t, "public static void main") * 5 + count(t, "@Override") * 3
                + count(t, "private ") * 2);
        // c：#include / printf / scanf
        scores.put("c", count(t, "#include") * 4 + count(t, "printf") * 3 + count(t, "scanf") * 3);
        // cpp：std::cout / namespace / vector<
        scores.put("cpp", count(t, "std::cout") * 5 + count(t, "namespace") * 3
                + count(t, "vector<") * 3 + count(t, "cout <<") * 4);
        // csharp：using System / Console.WriteLine
        scores.put("csharp", count(t, "using System") * 5 + count(t, "Console.WriteLine") * 4);
        // go：func / fmt.Println / package main
        scores.put("go", count(t, "func ") * 3 + count(t, "fmt.Println") * 4 + count(t, "package main") * 5);
        // javascript：function / console.log / const / =>
        scores.put("javascript", count(t, "function") * 2 + count(t, "console.log") * 3
                + count(t, "const ") * 2 + count(t, "=>") * 2 + count(t, "let ") * 2);
        // sql：SELECT / FROM / WHERE / CREATE TABLE
        int sqlScore = count(t.toUpperCase(), "SELECT ") * 2 + count(t.toUpperCase(), "FROM ") * 2
                + count(t.toUpperCase(), "CREATE TABLE") * 4 + count(t.toUpperCase(), "WHERE ") * 1;
        scores.put("sql", sqlScore);
        // php：<?php / echo
        scores.put("php", count(t, "<?php") * 5 + count(t, "->") * 1);
        // 选最高分；得分 < 5 视为无明显证据
        String best = null;
        int bestScore = 4; // 阈值
        for (var e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private int count(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) return 0;
        int c = 0, i = 0;
        while ((i = text.indexOf(needle, i)) != -1) { c++; i += needle.length(); }
        return c;
    }

    /** 兼容旧调用：仅按 courseConfigId 解析（不走自动检测） */
    private String resolveProgrammingLanguage(Long courseConfigId) {
        return resolveProgrammingLanguage(null, courseConfigId, null);
    }

    public GenerationTask submit(List<MultipartFile> pptFiles,
                                 MultipartFile customTemplate,
                                 QuestionBankGenerateRequest req) {
        Long ownerId = CurrentUserHolder.currentId();
        if (ownerId == null) throw new BusinessException(401, "未登录");

        quotaService.checkQuota(ownerId);

        // 校验（必须在扣费之前，避免参数错误也扣积分）
        if (pptFiles == null || pptFiles.isEmpty() || pptFiles.stream().allMatch(f -> f == null || f.isEmpty())) {
            throw new BusinessException("请上传章节 PPT/PDF 文件");
        }
        int totalQuestions;
        if ("per_file".equals(req.getMode())) {
            int fileCnt = (int) pptFiles.stream().filter(f -> f != null && !f.isEmpty()).count();
            if (req.getFileConfigs() == null || req.getFileConfigs().isEmpty())
                throw new BusinessException("请为每个文件配置题型数量");
            if (req.getFileConfigs().size() != fileCnt)
                throw new BusinessException("文件数量与配置数量不一致：文件 " + fileCnt + "，配置 " + req.getFileConfigs().size());
            int sum = 0;
            for (var c : req.getFileConfigs()) {
                if (c.getChapter() == null || c.getChapter().isBlank())
                    throw new BusinessException("请填写每个文件的章节标题");
                int cnt = c.getTypeCount() == null ? 0
                        : c.getTypeCount().values().stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
                if (cnt <= 0) throw new BusinessException("文件 [" + c.getChapter() + "] 至少选择一种题型并设置数量");
                sum += cnt;
            }
            if (sum > 200) throw new BusinessException("单次最多生成 200 道题目");
            totalQuestions = sum;
        } else {
            if (req.getChapter() == null || req.getChapter().isBlank()) {
                throw new BusinessException("请填写章节标题");
            }
            totalQuestions = req.getTypeCount() == null ? 0
                    : req.getTypeCount().values().stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
            if (totalQuestions <= 0) throw new BusinessException("至少选择一种题型并设置数量");
        }

        // 积分检查与扣减（校验通过后才扣费）
        // 两段式计费：base + totalQuestions × perQuestion；标准模式与 per_file 模式统一公式
        int cost = pointService.questionBankCost(totalQuestions);
        String reason = "per_file".equals(req.getMode())
                ? "生成题库: 按文件配置 共" + totalQuestions + "道"
                : "生成题库: " + req.getChapter() + " 共" + totalQuestions + "道";
        pointService.consume(ownerId, cost, reason, "question_bank", null);

        GenerationTask task = taskService.create("question_bank", ownerId, req);
        String taskDir = props.getUploadDir() + "/" + task.getTaskId();
        List<UploadedFile> ppts = new ArrayList<>();
        UploadedFile templateFile = null;
        List<String> persistedPaths = new ArrayList<>();
        try {
            ensureDir(taskDir);
            for (MultipartFile f : pptFiles) {
                if (f != null && !f.isEmpty()) {
                    String safe = sanitize(f.getOriginalFilename());
                    java.nio.file.Path p = java.nio.file.Paths.get(taskDir, "ppt_" + safe);
                    f.transferTo(p);
                    ppts.add(new UploadedFile(f.getOriginalFilename(), new byte[0], p.toAbsolutePath().toString()));
                    persistedPaths.add(p.toAbsolutePath().toString());
                }
            }
            if (customTemplate != null && !customTemplate.isEmpty()) {
                byte[] bytes = customTemplate.getBytes();
                java.nio.file.Path p = java.nio.file.Paths.get(taskDir, "tpl_" + sanitize(customTemplate.getOriginalFilename()));
                Files.write(p, bytes);
                templateFile = new UploadedFile(customTemplate.getOriginalFilename(), bytes, p.toAbsolutePath().toString());
                persistedPaths.add(p.toAbsolutePath().toString());
            }
        } catch (IOException e) {
            throw new BusinessException("保存文件失败: " + e.getMessage());
        }

        taskService.saveUploadedFiles(task.getTaskId(), persistedPaths);
        self().executeAsync(task.getTaskId(), ppts, templateFile, req, ownerId, cost);
        return task;
    }

    /** 重跑题库任务 */
    public GenerationTask retry(String oldTaskId) {
        Long ownerId = CurrentUserHolder.currentId();
        if (ownerId == null) throw new BusinessException(401, "未登录");
        GenerationTask old = taskService.get(oldTaskId);
        if (old == null) throw new BusinessException("原任务不存在");
        if (!CurrentUserHolder.isAdmin() && !ownerId.equals(old.getOwnerId())) {
            throw new BusinessException(403, "无权操作此任务");
        }
        if (!"question_bank".equals(old.getType())) throw new BusinessException("此任务不是题库任务");
        if (old.getParamsJson() == null || old.getParamsJson().isBlank())
            throw new BusinessException("原任务参数已丢失");

        quotaService.checkQuota(ownerId);
        QuestionBankGenerateRequest req = JSON.parseObject(old.getParamsJson(), QuestionBankGenerateRequest.class);
        int totalQuestions;
        String reason;
        if ("per_file".equals(req.getMode())) {
            totalQuestions = req.getFileConfigs() == null ? 0 : req.getFileConfigs().stream()
                    .mapToInt(c -> c.getTypeCount() == null ? 0
                            : c.getTypeCount().values().stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum())
                    .sum();
            reason = "重跑题库: 按文件配置 共" + totalQuestions + "道";
        } else {
            totalQuestions = req.getTypeCount() == null ? 0
                    : req.getTypeCount().values().stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
            reason = "重跑题库: " + req.getChapter() + " 共" + totalQuestions + "道";
        }
        int cost = pointService.questionBankCost(totalQuestions);
        pointService.consume(ownerId, cost, reason, "question_bank_retry", null);
        List<UploadedFile> ppts = new ArrayList<>();
        UploadedFile templateFile = null;
        if (old.getUploadedFiles() != null && !old.getUploadedFiles().isBlank()) {
            try {
                List<String> paths = JSON.parseArray(old.getUploadedFiles(), String.class);
                for (String p : paths) {
                    File f = new File(p);
                    if (!f.exists()) continue;
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    String origName = f.getName().replaceFirst("^(ppt|tpl)_", "");
                    UploadedFile uf = new UploadedFile(origName, bytes, p);
                    if (f.getName().startsWith("tpl_")) templateFile = uf;
                    else ppts.add(uf);
                }
            } catch (Exception e) {
                throw new BusinessException("读取原文件失败: " + e.getMessage());
            }
        }

        GenerationTask newTask = taskService.create("question_bank", ownerId, req);
        if (old.getUploadedFiles() != null) taskService.saveUploadedFilesRaw(newTask.getTaskId(), old.getUploadedFiles());
        self().executeAsync(newTask.getTaskId(), ppts, templateFile, req, ownerId, cost);
        return newTask;
    }

    private String sanitize(String name) {
        return GenerationSupport.sanitize(name);
    }

    @Async("generationExecutor")
    public void executeAsync(String taskId,
                             List<UploadedFile> ppts,
                             UploadedFile templateFile,
                             QuestionBankGenerateRequest req,
                             Long ownerId,
                             int cost) {
        if ("per_file".equals(req.getMode())) {
            executePerFile(taskId, ppts, templateFile, req, ownerId, cost);
            return;
        }
        try {
            taskService.markRunning(taskId);

            taskService.updateProgress(taskId, 5, "解析章节素材...");
            List<String> srcNames = new ArrayList<>();
            List<String[]> parsedPpts = new ArrayList<>();
            for (UploadedFile f : ppts) {
                String text = fileParseService.parseAuto(f.toFile(props.getUploadDir()));
                parsedPpts.add(new String[]{f.name, text});
                srcNames.add(f.name);
            }
            String sourceText = distributeTruncate(parsedPpts, 8000);

            taskService.updateProgress(taskId, 15, "选择大模型...");
            LlmClient client = pickClient(req.getProvider());
            String courseName = resolveCourseName(req.getCourseConfigId());
            // 编程题语言：优先 req.programmingLanguage，其次课程配置，再次从素材检测，最后默认 java
            String programmingLanguage = resolveProgrammingLanguage(
                    req.getProgrammingLanguage(), req.getCourseConfigId(), sourceText);
            log.info("[题库] 本次编程题语言解析结果 = {} (req={}, courseConfigId={})",
                    programmingLanguage, req.getProgrammingLanguage(), req.getCourseConfigId());
            Map<String, Integer> typeCount = req.getTypeCount();
            List<String> typesInOrder = new ArrayList<>();
            for (String t : List.of("single", "multi", "judge", "program")) {
                Integer c = typeCount.get(t);
                if (c != null && c > 0) typesInOrder.add(t);
            }

            int progressBase = 20;
            int progressRange = 70;
            List<Question> all = new ArrayList<>();
            // 每种题型的失败诊断信息，便于"全部为空"时给出可读错误
            List<String> typeFailures = new ArrayList<>();

            // ===== 4 题型并行生成（受 LlmRateLimiter 节流，按 provider.maxConcurrent 节制） =====
            taskService.updateProgress(taskId, progressBase,
                    String.format("并行生成 %d 个题型...", typesInOrder.size()));
            final LlmClient finalClient = client;
            final String finalCourseName = courseName;
            final String finalLanguage = programmingLanguage;
            final String finalChapter = req.getChapter();
            final String finalSourceText = sourceText;
            List<TypeGenerateOutcome> outcomes = parallelizer.mapParallel(typesInOrder, type -> {
                int count = typeCount.get(type);
                return generateAndValidateQuestions(finalClient, finalChapter, finalSourceText,
                        type, count, req.getDifficulty(), req.getContentLevel(), finalCourseName, finalLanguage);
            }, "qbank-types", (done, total) -> {
                int p = progressBase + (progressRange * done / Math.max(1, total));
                taskService.updateProgress(taskId, p,
                        String.format("已生成 %d/%d 个题型", done, total));
            });

            for (int i = 0; i < typesInOrder.size(); i++) {
                String type = typesInOrder.get(i);
                TypeGenerateOutcome outcome = outcomes.get(i);
                if (outcome == null) {
                    typeFailures.add(typeName(type) + ": 并行调用异常");
                    continue;
                }
                all.addAll(outcome.questions);
                if (outcome.questions.isEmpty() && outcome.reason != null) {
                    typeFailures.add(typeName(type) + ": " + outcome.reason);
                }
            }

            taskService.updateProgress(taskId, progressBase + progressRange,
                    String.format("完成 %d 道题目生成，渲染 Excel...", all.size()));

            // ===== 本地后处理（无 LLM 调用）：答案分布均衡 + 题干相似度去重 =====
            if (systemConfigService.getBool("question_bank_balance_distribution_enabled", true)) {
                com.teacheragent.service.questionbank.QuestionPostProcessor
                        .balanceSingleChoiceDistribution(all);
            }
            if (systemConfigService.getBool("question_bank_dedup_enabled", true)) {
                all = com.teacheragent.service.questionbank.QuestionPostProcessor
                        .deduplicateBySimilarity(all);
            }

            if (all.isEmpty()) {
                String detail = typeFailures.isEmpty() ? "所有题型均未返回有效题目" : String.join("；", typeFailures);
                throw new BusinessException("题目生成失败 - " + detail
                        + "（建议：1) 在「模型配置」检查激活模型是否可用；2) 缩短素材或减少题数；3) 切换为更稳定的厂商如 deepseek/glm-4-plus）");
            }

            taskService.updateProgress(taskId, 92, "渲染 Excel 文件...");
            byte[] templateBytes = templateFile != null
                    ? templateFile.bytes
                    : loadDefaultTemplate();
            byte[] xlsxBytes = filler.fill(new ByteArrayInputStream(templateBytes), all);

            ensureDir(props.getOutputDir());
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeChapter = nullSafe(req.getChapter()).replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = String.format("题库_%s_%d题_%s.xlsx", safeChapter, all.size(), stamp);
            Path outPath = Paths.get(props.getOutputDir(), fileName);
            Files.write(outPath, xlsxBytes);

            QuestionBankHistory history = new QuestionBankHistory();
            history.setChapter(req.getChapter());
            history.setQuestionTypes(JSON.toJSONString(typeCount));
            history.setDifficultyDist(req.getDifficulty());
            history.setTotalCount(all.size());
            history.setLlmProvider(client.getProvider());
            history.setLlmModel(client.getModelName());
            history.setSourceFiles(String.join(",", srcNames));
            history.setOutputFilePath(outPath.toAbsolutePath().toString());
            history.setOutputFileName(fileName);
            history.setStatus("success");
            history.setOwnerId(ownerId);
            history.setTaskId(taskId);
            historyMapper.insert(history);

            // 题目入库（用于手动管理与预览）
            for (int i = 0; i < all.size(); i++) {
                Question q = all.get(i);
                com.teacheragent.entity.QuestionItem qi = new com.teacheragent.entity.QuestionItem();
                qi.setBankId(history.getId());
                qi.setType(q.getType());
                qi.setKnowledge(q.getKnowledge());
                qi.setStem(q.getStem());
                qi.setDifficulty(q.getDifficulty());
                qi.setAnswer(q.getAnswer());
                qi.setExplanation(q.getExplanation());
                if (q.getOptions() != null) qi.setOptionsJson(JSON.toJSONString(q.getOptions()));
                qi.setSortOrder(i);
                qi.setOwnerId(ownerId);
                questionItemMapper.insert(qi);
            }

            taskService.markSuccess(taskId, history.getId(), "完成 " + all.size() + " 道题目生成");
        } catch (Exception e) {
            log.error("题库任务执行失败 taskId={}", taskId, e);
            try { taskService.markFailed(taskId, e.getMessage()); }
            catch (Exception markEx) { log.error("markFailed 失败 taskId={}", taskId, markEx); }
            try { pointService.refund(ownerId, cost, "题库生成失败退回", "question_bank_refund", taskId); }
            catch (Exception rfEx) { log.error("refund 失败 taskId={} userId={} amount={}", taskId, ownerId, cost, rfEx); }
        }
    }

    /**
     * per_file 模式：每个文件独立解析、独立按 typeCount 生成各题型，最终合并到一个 Excel。
     */
    private void executePerFile(String taskId,
                                List<UploadedFile> ppts,
                                UploadedFile templateFile,
                                QuestionBankGenerateRequest req,
                                Long ownerId,
                                int cost) {
        try {
            taskService.markRunning(taskId);
            taskService.updateProgress(taskId, 5, "解析章节素材...");

            List<QuestionBankGenerateRequest.FileQuestionConfig> configs = req.getFileConfigs();
            int n = ppts.size();
            String[] fileTexts = new String[n];
            String[] fileNames = new String[n];
            for (int i = 0; i < n; i++) {
                UploadedFile f = ppts.get(i);
                fileTexts[i] = fileParseService.parseAuto(f.toFile(props.getUploadDir()));
                fileNames[i] = f.name;
            }

            taskService.updateProgress(taskId, 15, "选择大模型...");
            LlmClient client = pickClient(req.getProvider());
            String courseName = resolveCourseName(req.getCourseConfigId());
            // per_file：合并所有文件的素材做语言检测兜底
            String mergedSrc = String.join("\n\n", fileTexts);
            String programmingLanguage = resolveProgrammingLanguage(
                    req.getProgrammingLanguage(), req.getCourseConfigId(), mergedSrc);
            log.info("[题库 per_file] 本次编程题语言解析结果 = {} (req={}, courseConfigId={})",
                    programmingLanguage, req.getProgrammingLanguage(), req.getCourseConfigId());
            int totalQuestions = 0;
            int totalTypeCalls = 0;
            for (var c : configs) {
                if (c.getTypeCount() != null) {
                    for (var v : c.getTypeCount().values()) {
                        if (v != null && v > 0) {
                            totalQuestions += v;
                            totalTypeCalls++;
                        }
                    }
                }
            }
            int progressBase = 18;
            int progressRange = 70;

            List<Question> all = new ArrayList<>();
            List<String> srcNames = new ArrayList<>();
            // 每个 (file × type) 调用的失败诊断
            List<String> callFailures = new ArrayList<>();

            // ===== 把 (文件 × 题型) 全部 task 拍平成一个并行批次（受 LlmRateLimiter 节流） =====
            record CallTask(String chapter, String fileSource, String type, int count) {}
            List<CallTask> callTasks = new ArrayList<>();
            for (int ci = 0; ci < configs.size(); ci++) {
                var cfg = configs.get(ci);
                int idx = cfg.getFileIndex() == null ? ci : cfg.getFileIndex();
                if (idx < 0 || idx >= n) continue;
                String text = fileTexts[idx];
                String fileName = fileNames[idx];
                srcNames.add(fileName);
                String chapter = cfg.getChapter();
                String fileSource = "【文件: " + fileName + "】\n" + text;

                for (String type : List.of("single", "multi", "judge", "program")) {
                    Integer count = cfg.getTypeCount() == null ? null : cfg.getTypeCount().get(type);
                    if (count == null || count <= 0) continue;
                    callTasks.add(new CallTask(chapter, fileSource, type, count));
                }
            }

            taskService.updateProgress(taskId, progressBase,
                    String.format("并行生成 %d 个调用（文件×题型）...", callTasks.size()));
            final LlmClient finalClient = client;
            final String finalCourseName = courseName;
            final String finalLanguage = programmingLanguage;
            List<TypeGenerateOutcome> outcomes = parallelizer.mapParallel(callTasks, t ->
                    generateAndValidateQuestions(finalClient, t.chapter(), t.fileSource(),
                            t.type(), t.count(), req.getDifficulty(), req.getContentLevel(), finalCourseName, finalLanguage),
                    "qbank-perfile", (done, total) -> {
                        int p = progressBase + (progressRange * done / Math.max(1, total));
                        taskService.updateProgress(taskId, p,
                                String.format("已生成 %d/%d 个调用", done, total));
                    });

            for (int i = 0; i < callTasks.size(); i++) {
                CallTask t = callTasks.get(i);
                TypeGenerateOutcome outcome = outcomes.get(i);
                if (outcome == null) {
                    callFailures.add("[" + t.chapter() + "] " + typeName(t.type()) + ": 并行调用异常");
                    continue;
                }
                all.addAll(outcome.questions);
                if (outcome.questions.isEmpty() && outcome.reason != null) {
                    callFailures.add("[" + t.chapter() + "] " + typeName(t.type()) + ": " + outcome.reason);
                }
            }
            taskService.updateProgress(taskId, progressBase + progressRange,
                    String.format("完成 %d 道题目生成，渲染 Excel...", all.size()));

            // ===== 本地后处理（无 LLM 调用）：答案分布均衡 + 题干相似度去重 =====
            if (systemConfigService.getBool("question_bank_balance_distribution_enabled", true)) {
                com.teacheragent.service.questionbank.QuestionPostProcessor
                        .balanceSingleChoiceDistribution(all);
            }
            if (systemConfigService.getBool("question_bank_dedup_enabled", true)) {
                all = com.teacheragent.service.questionbank.QuestionPostProcessor
                        .deduplicateBySimilarity(all);
            }

            if (all.isEmpty()) {
                String detail = callFailures.isEmpty() ? "所有题型均未返回有效题目" : String.join("；", callFailures);
                throw new BusinessException("题目生成失败 - " + detail
                        + "（建议：1) 在「模型配置」检查激活模型是否可用；2) 缩短素材或减少题数；3) 切换为更稳定的厂商如 deepseek/glm-4-plus）");
            }

            taskService.updateProgress(taskId, 92, "渲染 Excel 文件...");
            byte[] templateBytes = templateFile != null ? templateFile.bytes : loadDefaultTemplate();
            byte[] xlsxBytes = filler.fill(new ByteArrayInputStream(templateBytes), all);

            ensureDir(props.getOutputDir());
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("题库_按文件配置_%d文件_%d题_%s.xlsx", configs.size(), all.size(), stamp);
            Path outPath = Paths.get(props.getOutputDir(), fileName);
            Files.write(outPath, xlsxBytes);

            QuestionBankHistory history = new QuestionBankHistory();
            String chapterDesc = "按文件配置（" + configs.size() + " 个文件）：" +
                    configs.stream().map(QuestionBankGenerateRequest.FileQuestionConfig::getChapter).collect(java.util.stream.Collectors.joining(" | "));
            history.setChapter(chapterDesc.length() > 200 ? chapterDesc.substring(0, 200) : chapterDesc);
            // 汇总 typeCount
            Map<String, Integer> summary = new java.util.HashMap<>();
            for (var c : configs) {
                if (c.getTypeCount() != null) {
                    for (var en : c.getTypeCount().entrySet()) {
                        summary.merge(en.getKey(), en.getValue() == null ? 0 : en.getValue(), Integer::sum);
                    }
                }
            }
            history.setQuestionTypes(JSON.toJSONString(summary));
            history.setDifficultyDist(req.getDifficulty());
            history.setTotalCount(all.size());
            history.setLlmProvider(client.getProvider());
            history.setLlmModel(client.getModelName());
            history.setSourceFiles(String.join(",", srcNames));
            history.setOutputFilePath(outPath.toAbsolutePath().toString());
            history.setOutputFileName(fileName);
            history.setStatus("success");
            history.setOwnerId(ownerId);
            history.setTaskId(taskId);
            historyMapper.insert(history);

            for (int i = 0; i < all.size(); i++) {
                Question q = all.get(i);
                com.teacheragent.entity.QuestionItem qi = new com.teacheragent.entity.QuestionItem();
                qi.setBankId(history.getId());
                qi.setType(q.getType());
                qi.setKnowledge(q.getKnowledge());
                qi.setStem(q.getStem());
                qi.setDifficulty(q.getDifficulty());
                qi.setAnswer(q.getAnswer());
                qi.setExplanation(q.getExplanation());
                if (q.getOptions() != null) qi.setOptionsJson(JSON.toJSONString(q.getOptions()));
                qi.setSortOrder(i);
                qi.setOwnerId(ownerId);
                questionItemMapper.insert(qi);
            }

            taskService.markSuccess(taskId, history.getId(), "完成 " + all.size() + " 道题目生成");
        } catch (Exception e) {
            log.error("题库 per_file 任务执行失败 taskId={}", taskId, e);
            try { taskService.markFailed(taskId, e.getMessage()); }
            catch (Exception markEx) { log.error("markFailed 失败 taskId={}", taskId, markEx); }
            try { pointService.refund(ownerId, cost, "题库生成失败退回", "question_bank_refund", taskId); }
            catch (Exception rfEx) { log.error("refund 失败 taskId={} userId={} amount={}", taskId, ownerId, cost, rfEx); }
        }
    }

    private String typeName(String type) {
        return Map.of("single", "单选题", "multi", "多选题", "judge", "判断题", "program", "编程题")
                .getOrDefault(type, type);
    }

    /**
     * 单种题型生成结果 + 失败原因（供上层聚合）
     */
    private static class TypeGenerateOutcome {
        final List<Question> questions;
        /** 当 questions 为空时给出失败原因，便于诊断；非空时为 null */
        final String reason;
        TypeGenerateOutcome(List<Question> questions, String reason) {
            this.questions = questions;
            this.reason = reason;
        }
        static TypeGenerateOutcome ok(List<Question> qs) { return new TypeGenerateOutcome(qs, null); }
        static TypeGenerateOutcome fail(String r) { return new TypeGenerateOutcome(new ArrayList<>(), r); }
    }

    /**
     * 生成 + 合规过滤 + 编译校验（编程题）+ 二次自检 + 必要时重生
     * 改为返回 TypeGenerateOutcome：questions 为空时附带可读 reason，便于上层聚合诊断
     */
    /**
     * 生成 + 合规过滤 + 编译校验（编程题）+ 二次自检 + 必要时重生
     * 改为返回 TypeGenerateOutcome：questions 为空时附带可读 reason，便于上层聚合诊断
     *
     * @param language 编程语言（仅 program 题型使用，其他题型可传 null）
     */
    private TypeGenerateOutcome generateAndValidateQuestions(LlmClient client, String chapter, String sourceText,
                                                             String type, int count, String difficulty, String contentLevel,
                                                             String courseName, String language) {
        boolean critiqueEnabled = systemConfigService.getBool("question_bank_self_critique_enabled", false);
        boolean compileEnabled = systemConfigService.getBool("program_question_compile_check_enabled", true);

        // BM25 精筛：把整段 sourceText 按"章节 + 题型偏好"检索 top-K，避免长素材尾部噪声
        String refinedSource = sourceRetrievalService.retrieveForQuestion(chapter, type, sourceText, 6000);
        String prompt = buildPromptForType(type, chapter, refinedSource, count, difficulty, contentLevel, language);
        if (prompt == null) return TypeGenerateOutcome.fail("不支持的题型");

        String sysPrompt = QuestionBankPrompts.getSystemPrompt(courseName);

        // 第一次生成 + 解析
        List<Question> result;
        String firstError = null;
        String rawJsonForDiag = null;
        try {
            String json = client.chatJson(sysPrompt, prompt);
            rawJsonForDiag = json;
            result = parseQuestionsJson(json, type);
            if (result.isEmpty()) {
                firstError = "LLM 返回内容无法解析为题目数组（响应前 80 字: "
                        + (json == null ? "null" : json.substring(0, Math.min(80, json.length())).replace("\n", "\\n"))
                        + ")";
            }
        } catch (Exception e) {
            log.error("生成 {} ({}) LLM 调用失败: {}", typeName(type), chapter, e.getMessage(), e);
            return TypeGenerateOutcome.fail("LLM 调用失败 - " + e.getMessage());
        }

        // 1. 合规过滤
        int beforeFilter = result.size();
        result = filterValidQuestions(result, type);
        int afterFilter = result.size();
        if (beforeFilter > 0 && afterFilter == 0) {
            log.warn("[{}] {} 合规过滤淘汰 {} 道题（答案格式或选项数不达标）", chapter, typeName(type), beforeFilter);
        }

        // 2. 编程题编译校验（按 language 路由：java 走完整 javac 编译，其他语言走轻量代码格式校验）
        int beforeCompile = result.size();
        if ("program".equals(type) && compileEnabled) {
            result.removeIf(q -> !codeCompilerRouter.tryCompile(q.getAnswer(), language));
            int afterCompile = result.size();
            if (beforeCompile > 0 && afterCompile == 0) {
                log.warn("[{}] 编程题 {} 道全部编译/格式校验失败 language={}", chapter, beforeCompile, language);
            }
        }

        // 2.1 编程题运行校验（默认关闭；启用后 java 真实运行比对样例，其他语言走代码格式校验）
        boolean runtimeCheckEnabled = systemConfigService.getBool(
                "program_question_runtime_check_enabled", false);
        if ("program".equals(type) && runtimeCheckEnabled && !result.isEmpty()) {
            int beforeRun = result.size();
            // 优先：从 stem 提取样例，启用样例校验；失败回退"运行不崩"档
            result.removeIf(q -> {
                com.teacheragent.service.questionbank.JavaCompilerService.SampleIo io =
                        com.teacheragent.service.questionbank.JavaCompilerService.extractSampleIo(q.getStem());
                if (io != null) {
                    return !codeCompilerRouter.tryRunWithStdin(q.getAnswer(), io.input(), io.output(), 3000L, language);
                }
                return !codeCompilerRouter.tryRunWithStdin(q.getAnswer(), null, null, 3000L, language);
            });
            int afterRun = result.size();
            if (beforeRun > afterRun) {
                log.info("[{}] 编程题运行校验淘汰 {} 道（运行时崩溃 / 超时 / 样例输出不匹配）",
                        chapter, beforeRun - afterRun);
            }
        }

        // 2.5 题目语义校验（默认关闭；启用后约多 N/10 次低温度 LLM 调用换取约 5-10% 答案正确率提升）
        boolean semanticValidateEnabled = systemConfigService.getBool(
                "question_bank_semantic_validate_enabled", false);
        if (semanticValidateEnabled && !"program".equals(type) && !result.isEmpty()) {
            try {
                List<Boolean> okList = com.teacheragent.service.questionbank.QuestionSemanticValidator
                        .batchValidate(client, result);
                int before = result.size();
                List<Question> kept = new ArrayList<>();
                for (int i = 0; i < result.size() && i < okList.size(); i++) {
                    if (okList.get(i)) kept.add(result.get(i));
                }
                int dropped = before - kept.size();
                if (dropped > 0) {
                    log.info("[{}] {} 语义校验淘汰 {} 道（answer 字母与选项内容不匹配）",
                            chapter, typeName(type), dropped);
                }
                result = kept;
            } catch (Exception e) {
                log.warn("[{}] {} 语义校验异常，沿用原结果: {}", chapter, typeName(type), e.getMessage());
            }
        }

        boolean needRegen = result.size() < (int) Math.ceil(count * 0.7);

        // 3. 二次自检（默认关闭以缩短生成时间；可在系统配置开启）
        String reviewAdvice = "";
        if (!needRegen && critiqueEnabled && !result.isEmpty()) {
            try {
                ReviewResult rr = reviewQuestions(client, chapter, type, count, result);
                if ("regen".equalsIgnoreCase(rr.action) || rr.overall < 7.0) {
                    needRegen = true;
                    reviewAdvice = rr.advice;
                }
            } catch (Exception ex) {
                log.warn("题目自检失败，跳过自检: {}", ex.getMessage());
            }
        }

        // 4. 重生（最多 1 次，仅在首次结果不足时触发）
        if (needRegen) {
            log.info("[{}] {} 触发重生 currentSize={} expected={} advice={}",
                    chapter, typeName(type), result.size(), count, reviewAdvice);
            try {
                String fixHint = reviewAdvice.isBlank() || "无".equals(reviewAdvice) ? "" : "请按以下建议改进：" + reviewAdvice;
                String regenPrompt = prompt + (fixHint.isEmpty() ? "" : "\n\n【重生要求】" + fixHint);
                String json = client.chatJson(sysPrompt, regenPrompt);
                List<Question> regen = parseQuestionsJson(json, type);
                regen = filterValidQuestions(regen, type);
                if ("program".equals(type) && compileEnabled) {
                    regen.removeIf(q -> !codeCompilerRouter.tryCompile(q.getAnswer(), language));
                }
                if (regen.size() > result.size()) {
                    result = regen;
                }
            } catch (Exception ex) {
                log.warn("题目重生失败，沿用首批: {}", ex.getMessage());
            }
        }

        if (result.isEmpty()) {
            String reason;
            if (firstError != null) {
                reason = firstError;
            } else if (beforeFilter > 0) {
                reason = "LLM 返回 " + beforeFilter + " 道题但全部不符合格式（答案/选项不达标"
                        + ("program".equals(type) ? " 或编译失败" : "") + "）";
            } else {
                reason = "LLM 返回 0 道题";
            }
            return TypeGenerateOutcome.fail(reason);
        }
        // 编程题 stem 前缀替换为带语言标识，方便教师在 Excel 一眼识别
        if ("program".equals(type) && language != null && !"java".equalsIgnoreCase(language)) {
            String langLabel = QuestionBankPrompts.languageDisplayName(language);
            for (Question q : result) {
                if (q.getStem() != null && q.getStem().startsWith("【编程题】")) {
                    q.setStem("【编程题・" + langLabel + "】" + q.getStem().substring("【编程题】".length()));
                }
            }
        }
        return TypeGenerateOutcome.ok(result);
    }

    /** 题目合规过滤 */
    private List<Question> filterValidQuestions(List<Question> qs, String type) {
        List<Question> r = new ArrayList<>();
        for (Question q : qs) {
            if (q == null || q.getStem() == null || q.getStem().isBlank()) continue;
            if (q.getAnswer() == null || q.getAnswer().isBlank()) continue;
            switch (type) {
                case "single" -> {
                    if (q.getOptions() == null || q.getOptions().size() != 4) continue;
                    String a = q.getAnswer().trim().toUpperCase();
                    if (!a.matches("[A-D]")) continue;
                }
                case "multi" -> {
                    if (q.getOptions() == null || q.getOptions().size() != 4) continue;
                    String a = q.getAnswer().trim().toUpperCase().replace(" ", "");
                    if (!a.matches("[A-D](,[A-D])+")) continue;
                }
                case "judge" -> {
                    String a = q.getAnswer().trim();
                    if (!"正确".equals(a) && !"错误".equals(a) && !"对".equals(a) && !"错".equals(a)
                            && !"true".equalsIgnoreCase(a) && !"false".equalsIgnoreCase(a)) continue;
                }
                case "program" -> {
                    // 多语言通用：要求答案不为空且长度合理（具体语言编译/运行校验在后续 codeCompilerRouter 处理）
                    if (q.getAnswer().trim().length() < 30) continue;
                }
            }
            r.add(q);
        }
        return r;
    }

    /** 题库二次自检结果 */
    private static class ReviewResult {
        double overall = 10.0;
        String action = "pass";
        String advice = "";
    }

    /** 调 LLM 评审一批题目 */
    private ReviewResult reviewQuestions(LlmClient client, String chapter, String type, int expected, List<Question> qs) throws Exception {
        ReviewResult r = new ReviewResult();
        // 提取精简 JSON 摘要（仅 stem/answer/difficulty/knowledge）
        com.alibaba.fastjson2.JSONArray brief = new com.alibaba.fastjson2.JSONArray();
        for (Question q : qs) {
            JSONObject o = new JSONObject();
            o.put("stem", q.getStem());
            o.put("knowledge", q.getKnowledge());
            o.put("difficulty", q.getDifficulty());
            o.put("answer", q.getAnswer());
            brief.add(o);
        }
        String prompt = QuestionBankPrompts.buildReviewPrompt(chapter, type, expected, brief.toJSONString());
        String json = client.chatJson(QuestionBankPrompts.SYSTEM_PROMPT_REVIEW, prompt);
        if (json == null || json.isBlank()) return r;
        String cleaned = stripCodeBlock(json.trim());
        JSONObject obj;
        try {
            obj = JSON.parseObject(cleaned);
        } catch (Exception e) {
            obj = JSON.parseObject(LessonPlanJsonUtil.repairJsonQuotes(cleaned));
        }
        r.overall = obj.containsKey("overall") ? obj.getDoubleValue("overall") : 10.0;
        r.action = obj.getString("action") == null ? "pass" : obj.getString("action");
        r.advice = obj.getString("advice") == null ? "" : obj.getString("advice");
        return r;
    }

    private byte[] loadDefaultTemplate() throws IOException {
        try (InputStream is = new ClassPathResource("reference/题目导入模板.xlsx").getInputStream()) {
            return is.readAllBytes();
        }
    }

    private String buildPromptForType(String type, String chapter, String src, int count, String difficulty, String contentLevel, String language) {
        return switch (type) {
            case "single"  -> QuestionBankPrompts.buildSinglePrompt(chapter, src, count, difficulty, contentLevel);
            case "multi"   -> QuestionBankPrompts.buildMultiPrompt(chapter, src, count, difficulty, contentLevel);
            case "judge"   -> QuestionBankPrompts.buildJudgePrompt(chapter, src, count, difficulty, contentLevel);
            case "program" -> QuestionBankPrompts.buildProgramPrompt(chapter, src, count, difficulty, contentLevel, language);
            default -> null;
        };
    }

    private List<Question> parseQuestionsJson(String json, String type) {
        if (json == null) return new ArrayList<>();
        String cleaned = stripCodeBlock(json.trim());
        try {
            return doParseQuestions(cleaned, type);
        } catch (Exception e) {
            // 兜底：尝试修复字符串值内嵌套的未转义双引号
            try {
                String repaired = LessonPlanJsonUtil.repairJsonQuotes(cleaned);
                return doParseQuestions(repaired, type);
            } catch (Exception ee) {
                log.error("题目 JSON 解析失败 type={}（修复后仍失败: {}）", type, ee.getMessage());
                return new ArrayList<>();
            }
        }
    }

    private List<Question> doParseQuestions(String cleaned, String type) {
        JSONArray arr;
        if (cleaned.startsWith("[")) {
            arr = JSON.parseArray(cleaned);
        } else {
            JSONObject obj = JSON.parseObject(cleaned);
            arr = obj.getJSONArray("questions");
            if (arr == null) {
                for (String k : obj.keySet()) {
                    Object v = obj.get(k);
                    if (v instanceof JSONArray ja) { arr = ja; break; }
                }
            }
            if (arr == null) {
                arr = new JSONArray();
                arr.add(obj);
            }
        }
        List<Question> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Question q = new Question();
            q.setType(type);
            q.setKnowledge(o.getString("knowledge"));
            q.setDifficulty(orDefault(o.getString("difficulty"), "一般"));
            q.setAnswer(o.getString("answer"));
            q.setExplanation(orDefault(o.getString("explanation"), ""));
            String stem = o.getString("stem");
            if ("program".equals(type)) {
                // 编程题 stem 前缀（语言标识在 generateAndValidateQuestions 中按实际 language 替换）
                stem = "【编程题】" + stem;
            }
            q.setStem(stem);
            JSONArray opts = o.getJSONArray("options");
            if (opts != null) {
                List<String> list = new ArrayList<>();
                for (int j = 0; j < opts.size(); j++) list.add(opts.getString(j));
                q.setOptions(list);
            }
            result.add(q);
        }
        return result;
    }

    private String stripCodeBlock(String s) {
        return GenerationSupport.stripCodeBlock(s);
    }

    private String orDefault(String s, String d) { return (s == null || s.isBlank()) ? d : s; }
    private String nullSafe(String s) { return s == null ? "" : s; }

    /** 多文件按比例分配截断（委托给 GenerationSupport，含句末边界回退） */
    private String distributeTruncate(List<String[]> fileTexts, int budget) {
        return GenerationSupport.distributeTruncate(fileTexts, budget);
    }

    private void ensureDir(String path) throws IOException {
        GenerationSupport.ensureDir(path);
    }

    public List<QuestionBankHistory> listHistory(int limit) {
        LambdaQueryWrapper<QuestionBankHistory> q = new LambdaQueryWrapper<>();
        if (!CurrentUserHolder.isAdmin() && CurrentUserHolder.currentId() != null) {
            q.eq(QuestionBankHistory::getOwnerId, CurrentUserHolder.currentId());
        }
        q.orderByDesc(QuestionBankHistory::getCreateTime).last("LIMIT " + Math.min(limit, 200));
        return historyMapper.selectList(q);
    }

    public QuestionBankHistory getById(Long id) {
        QuestionBankHistory h = historyMapper.selectById(id);
        if (h == null) return null;
        if (!CurrentUserHolder.isAdmin() && CurrentUserHolder.currentId() != null
                && !CurrentUserHolder.currentId().equals(h.getOwnerId())) {
            throw new BusinessException(403, "无权下载该文件");
        }
        return h;
    }

    /** 删除历史记录 */
    @Transactional
    public void deleteById(Long id) {
        QuestionBankHistory h = historyMapper.selectById(id);
        if (h == null) throw new BusinessException("记录不存在");
        if (!CurrentUserHolder.isAdmin() && CurrentUserHolder.currentId() != null
                && !CurrentUserHolder.currentId().equals(h.getOwnerId())) {
            throw new BusinessException(403, "无权删除");
        }
        historyMapper.deleteById(id);
    }
}
