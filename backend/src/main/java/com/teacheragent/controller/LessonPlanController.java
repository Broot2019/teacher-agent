package com.teacheragent.controller;

import com.teacheragent.common.BusinessException;
import com.teacheragent.common.R;
import com.teacheragent.dto.LessonPlanGenerateRequest;
import com.teacheragent.entity.GenerationTask;
import com.teacheragent.entity.LessonPlanHistory;
import com.teacheragent.service.LessonPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/lesson-plan")
@RequiredArgsConstructor
public class LessonPlanController {

    private final LessonPlanService lessonPlanService;

    /**
     * 提交生成任务（异步）
     * 立即返回 taskId，前端轮询 /api/task/{taskId} 获取进度
     */
    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<GenerationTask> generate(
            @RequestPart(value = "pptFiles", required = false) List<MultipartFile> pptFiles,
            @RequestPart(value = "teachingPlanFile", required = false) MultipartFile teachingPlanFile,
            @RequestPart(value = "customTemplate", required = false) MultipartFile customTemplate,
            @RequestPart(value = "request") String requestJson) {

        if (requestJson == null || requestJson.isBlank()) {
            throw new BusinessException("缺少 request 参数");
        }
        LessonPlanGenerateRequest req = com.alibaba.fastjson2.JSON.parseObject(requestJson, LessonPlanGenerateRequest.class);
        return R.ok(lessonPlanService.submit(pptFiles, teachingPlanFile, customTemplate, req), "已提交，正在生成");
    }

    @GetMapping("/history")
    public R<List<LessonPlanHistory>> history(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(lessonPlanService.listHistory(limit));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable Long id) throws Exception {
        LessonPlanHistory h = lessonPlanService.getById(id);
        if (h == null) throw new BusinessException("记录不存在");
        File file = new File(h.getOutputFilePath());
        if (!file.exists()) throw new BusinessException("文件已被移动或删除");
        String enc = URLEncoder.encode(h.getOutputFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + enc + "\"; filename*=UTF-8''" + enc)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }

    /** 预览：返回 List<LessonPlanData> JSON */
    @GetMapping("/preview/{id}")
    public R<?> preview(@PathVariable Long id) {
        return R.ok(lessonPlanService.preview(id));
    }

    /** 重跑任务 */
    @PostMapping("/retry/{taskId}")
    public R<?> retry(@PathVariable String taskId) {
        return R.ok(lessonPlanService.retry(taskId), "已重新提交");
    }

    /** 删除历史记录 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        lessonPlanService.deleteById(id);
        return R.ok(null, "已删除");
    }
}
