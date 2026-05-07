package com.teacheragent.controller;

import com.teacheragent.common.R;
import com.teacheragent.dto.LoginRequest;
import com.teacheragent.dto.LoginResponse;
import com.teacheragent.dto.RegisterRequest;
import com.teacheragent.entity.PointLog;
import com.teacheragent.entity.User;
import com.teacheragent.service.PointService;
import com.teacheragent.service.SystemConfigService;
import com.teacheragent.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PointService pointService;
    private final SystemConfigService systemConfigService;

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        return R.ok(userService.login(req), "登录成功");
    }

    @PostMapping("/register")
    public R<LoginResponse> register(@RequestBody @Valid RegisterRequest req) {
        return R.ok(userService.register(req), "注册成功");
    }

    @GetMapping("/me")
    public R<User> me() {
        return R.ok(userService.getCurrent());
    }

    @PostMapping("/change-password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPwd = body.getOrDefault("oldPassword", "");
        String newPwd = body.getOrDefault("newPassword", "");
        if (newPwd.length() < 6) {
            return R.fail("新密码长度至少 6 位");
        }
        userService.changePassword(oldPwd, newPwd);
        return R.ok(null, "密码已更新");
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok(null, "已退出");
    }

    @GetMapping("/point-rules")
    public R<Map<String, Object>> pointRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("lessonPlanBaseCost", systemConfigService.getInt("lesson_plan_cost", 10));
        rules.put("lessonPlanRangeCost", systemConfigService.getInt("lesson_plan_range_cost", 5));
        rules.put("questionBankCost", systemConfigService.getInt("question_bank_cost", 5));
        rules.put("questionBankPerQuestionCost", systemConfigService.getInt("question_bank_per_question_cost", 1));
        return R.ok(rules);
    }

    @GetMapping("/point-logs")
    public R<List<PointLog>> myPointLogs(@RequestParam(defaultValue = "50") int limit) {
        Long uid = com.teacheragent.common.CurrentUserHolder.currentId();
        return R.ok(pointService.list(uid, limit));
    }
}
