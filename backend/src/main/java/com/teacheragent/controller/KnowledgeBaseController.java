package com.teacheragent.controller;

import com.teacheragent.common.R;
import com.teacheragent.entity.KnowledgeBase;
import com.teacheragent.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    @GetMapping("/list")
    public R<List<KnowledgeBase>> list() {
        return R.ok(service.listMine());
    }

    @PostMapping("/upload")
    public R<KnowledgeBase> upload(@RequestParam("title") String title,
                                   @RequestParam("file") MultipartFile file) {
        return R.ok(service.upload(title, file));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return R.ok(null);
    }
}
