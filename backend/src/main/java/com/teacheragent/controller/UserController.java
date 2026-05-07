package com.teacheragent.controller;

import com.teacheragent.common.AdminOnly;
import com.teacheragent.common.R;
import com.teacheragent.dto.UserSaveRequest;
import com.teacheragent.entity.User;
import com.teacheragent.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@AdminOnly
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/list")
    public R<List<User>> list() {
        return R.ok(userService.list());
    }

    @PostMapping("/save")
    public R<User> save(@RequestBody UserSaveRequest req) {
        return R.ok(userService.saveByAdmin(req), "保存成功");
    }

    @PostMapping("/toggle-status/{id}")
    public R<Void> toggleStatus(@PathVariable Long id) {
        userService.toggleStatus(id);
        return R.ok(null, "状态已切换");
    }

    @PostMapping("/reset-password/{id}")
    public R<Map<String, String>> resetPassword(@PathVariable Long id) {
        String newPwd = userService.resetPassword(id);
        return R.ok(Map.of("newPassword", newPwd), "已重置，请将新密码告知用户");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok(null, "已删除");
    }
}
