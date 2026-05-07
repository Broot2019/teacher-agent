package com.teacheragent.controller;

import com.teacheragent.common.R;
import com.teacheragent.entity.ChapterMaterial;
import com.teacheragent.service.ChapterMaterialService;
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
@RequestMapping("/api/material")
@RequiredArgsConstructor
public class ChapterMaterialController {

    private final ChapterMaterialService materialService;

    @GetMapping("/list")
    public R<List<ChapterMaterial>> list(@RequestParam(required = false) String chapter,
                                         @RequestParam(required = false) String course) {
        return R.ok(materialService.list(chapter, course));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ChapterMaterial> upload(@RequestPart("file") MultipartFile file,
                                     @RequestPart(value = "chapter", required = false) String chapter,
                                     @RequestPart(value = "course", required = false) String course,
                                     @RequestPart(value = "description", required = false) String description,
                                     @RequestPart(value = "isPublic", required = false) String isPublic) {
        Integer pub = isPublic == null ? 1 : ("0".equals(isPublic) ? 0 : 1);
        return R.ok(materialService.upload(file, chapter, course, description, pub), "上传成功");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return R.ok(null, "已删除");
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable Long id) throws Exception {
        ChapterMaterial m = materialService.getForUse(id);
        File file = new File(m.getFilePath());
        if (!file.exists()) return ResponseEntity.notFound().build();
        String enc = URLEncoder.encode(m.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + enc)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
