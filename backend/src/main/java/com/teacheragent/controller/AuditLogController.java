package com.teacheragent.controller;

import com.teacheragent.common.AdminOnly;
import com.teacheragent.common.R;
import com.teacheragent.entity.AuditLog;
import com.teacheragent.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@AdminOnly
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/list")
    public R<List<AuditLog>> list(@RequestParam(defaultValue = "100") int limit,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(required = false) Long userId) {
        return R.ok(auditLogService.list(limit, action, userId));
    }
}
