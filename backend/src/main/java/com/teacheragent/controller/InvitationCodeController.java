package com.teacheragent.controller;

import com.teacheragent.common.AdminOnly;
import com.teacheragent.common.R;
import com.teacheragent.entity.InvitationCode;
import com.teacheragent.service.InvitationCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invitation-code")
@AdminOnly
@RequiredArgsConstructor
public class InvitationCodeController {

    private final InvitationCodeService invitationCodeService;

    @PostMapping("/create")
    public R<InvitationCode> create(@RequestBody Map<String, Object> body) {
        Integer days = body.get("validDays") != null ? ((Number) body.get("validDays")).intValue() : 30;
        Integer points = body.get("initialPoints") != null ? ((Number) body.get("initialPoints")).intValue() : null;
        Integer quota = body.get("initialQuota") != null ? ((Number) body.get("initialQuota")).intValue() : null;
        String note = (String) body.get("note");
        return R.ok(invitationCodeService.create(days, points, quota, note), "邀请码已生成");
    }

    @GetMapping("/list")
    public R<List<InvitationCode>> list(@RequestParam(defaultValue = "200") int limit) {
        return R.ok(invitationCodeService.list(limit));
    }

    @PostMapping("/disable/{id}")
    public R<Void> disable(@PathVariable Long id) {
        invitationCodeService.disable(id);
        return R.ok(null, "已禁用");
    }

    @PostMapping("/batch-create")
    public R<List<InvitationCode>> batchCreate(@RequestBody Map<String, Object> body) {
        int count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 5;
        Integer days = body.get("validDays") != null ? ((Number) body.get("validDays")).intValue() : 30;
        Integer points = body.get("initialPoints") != null ? ((Number) body.get("initialPoints")).intValue() : null;
        Integer quota = body.get("initialQuota") != null ? ((Number) body.get("initialQuota")).intValue() : null;
        String note = (String) body.get("note");
        java.util.List<InvitationCode> codes = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(count, 50); i++) {
            codes.add(invitationCodeService.create(days, points, quota, note));
        }
        return R.ok(codes, "已生成 " + codes.size() + " 个邀请码");
    }
}
