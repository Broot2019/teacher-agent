package com.teacheragent.controller;

import com.teacheragent.common.AdminOnly;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.common.R;
import com.teacheragent.dto.LlmConfigSaveRequest;
import com.teacheragent.entity.LlmConfig;
import com.teacheragent.service.LlmConfigService;
import com.teacheragent.service.llm.TestResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llm-config")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigService llmConfigService;

    @GetMapping("/list")
    public R<List<LlmConfig>> list() {
        List<LlmConfig> configs = llmConfigService.list();
        // 非 admin 不应返回真实 key，也不返回 mask（避免 mask 字符串被前端 v-model 回写覆盖真实 key）
        if (!CurrentUserHolder.isAdmin()) {
            configs.forEach(c -> c.setApiKey(""));
        }
        return R.ok(configs);
    }

    @GetMapping("/active")
    public R<LlmConfig> getActive() {
        LlmConfig active = llmConfigService.getActive();
        if (active != null && !CurrentUserHolder.isAdmin()) {
            active.setApiKey("");
        }
        return R.ok(active);
    }

    @AdminOnly
    @PostMapping("/save")
    public R<LlmConfig> save(@RequestBody @Valid LlmConfigSaveRequest req) {
        return R.ok(llmConfigService.save(req), "保存成功");
    }

    @AdminOnly
    @PostMapping("/test/{provider}")
    public R<TestResult> test(@PathVariable String provider) {
        return R.ok(llmConfigService.test(provider));
    }

    @AdminOnly
    @PostMapping("/activate/{provider}")
    public R<Void> activate(@PathVariable String provider) {
        llmConfigService.activate(provider);
        return R.ok(null, "已激活 " + provider);
    }

    @AdminOnly
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        llmConfigService.delete(id);
        return R.ok(null, "已删除");
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
