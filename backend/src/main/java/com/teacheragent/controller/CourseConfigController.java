package com.teacheragent.controller;

import com.teacheragent.common.R;
import com.teacheragent.dto.CourseConfigSaveRequest;
import com.teacheragent.entity.CourseConfig;
import com.teacheragent.service.CourseConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/course-config")
@RequiredArgsConstructor
public class CourseConfigController {

    private final CourseConfigService service;

    @GetMapping("/list")
    public R<List<CourseConfig>> list() {
        return R.ok(service.listMine());
    }

    @GetMapping("/active")
    public R<CourseConfig> active() {
        return R.ok(service.getActive());
    }

    @PostMapping("/save")
    public R<CourseConfig> save(@RequestBody CourseConfigSaveRequest req) {
        return R.ok(service.save(req));
    }

    @PostMapping("/activate/{id}")
    public R<Void> activate(@PathVariable Long id) {
        service.activate(id);
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return R.ok(null);
    }
}
