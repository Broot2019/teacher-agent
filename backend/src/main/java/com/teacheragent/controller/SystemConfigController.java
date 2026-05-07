package com.teacheragent.controller;

import com.teacheragent.common.AdminOnly;
import com.teacheragent.common.BusinessException;
import com.teacheragent.common.R;
import com.teacheragent.service.PointService;
import com.teacheragent.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@AdminOnly
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;
    private final PointService pointService;

    @GetMapping("/config")
    public R<Map<String, String>> list() {
        return R.ok(systemConfigService.list());
    }

    @PostMapping("/config")
    public R<Void> save(@RequestBody Map<String, String> map) {
        systemConfigService.save(map);
        return R.ok(null, "配置已保存");
    }

    @PostMapping("/point/grant")
    public R<Void> grantPoints(@RequestBody Map<String, Object> body) {
        Object uidObj = body.get("userId");
        Object amtObj = body.get("amount");
        if (uidObj == null || amtObj == null) {
            throw new BusinessException("缺少 userId 或 amount 参数");
        }
        if (!(uidObj instanceof Number) || !(amtObj instanceof Number)) {
            throw new BusinessException("userId 和 amount 必须为数字");
        }
        Long userId = ((Number) uidObj).longValue();
        int amount = ((Number) amtObj).intValue();
        String reason = (String) body.getOrDefault("reason", "管理员调整积分");
        pointService.grant(userId, amount, reason);
        return R.ok(null, "积分已调整");
    }

    @GetMapping("/point/logs")
    public R<List<com.teacheragent.entity.PointLog>> pointLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "100") int limit) {
        return R.ok(pointService.list(userId, limit));
    }
}
