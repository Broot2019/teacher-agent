package com.teacheragent.controller;

import com.teacheragent.common.BusinessException;
import com.teacheragent.common.R;
import com.teacheragent.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final AppProperties props;

    @PostMapping("/upload-template/lesson-plan")
    public R<Map<String, String>> uploadLessonTemplate(@RequestPart("file") MultipartFile file) throws IOException {
        return R.ok(saveTemplate(file, "lesson-plan"));
    }

    @PostMapping("/upload-template/question-bank")
    public R<Map<String, String>> uploadQuestionTemplate(@RequestPart("file") MultipartFile file) throws IOException {
        return R.ok(saveTemplate(file, "question-bank"));
    }

    private Map<String, String> saveTemplate(MultipartFile file, String type) throws IOException {
        if (file == null || file.isEmpty()) throw new BusinessException("文件为空");
        Path dir = Paths.get(props.getTemplateDir(), type);
        File d = dir.toFile();
        if (!d.exists()) d.mkdirs();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String name = stamp + "_" + file.getOriginalFilename();
        Path target = dir.resolve(name);
        Files.write(target, file.getBytes());
        Map<String, String> m = new HashMap<>();
        m.put("path", target.toAbsolutePath().toString());
        m.put("fileName", name);
        return m;
    }
}
