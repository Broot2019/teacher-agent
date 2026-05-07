package com.teacheragent.controller;

import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.common.R;
import com.teacheragent.service.QaAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaAssistantService qaService;

    @PostMapping("/ask")
    public R<Map<String, String>> ask(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) throw new BusinessException("请输入问题");
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");
        return R.ok(qaService.ask(userId, question));
    }
}
