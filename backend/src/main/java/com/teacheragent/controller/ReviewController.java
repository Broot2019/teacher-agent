package com.teacheragent.controller;

import com.teacheragent.common.BusinessException;
import com.teacheragent.common.CurrentUserHolder;
import com.teacheragent.common.R;
import com.teacheragent.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/lesson-plan")
    public R<Map<String, Object>> reviewLessonPlan(@RequestBody Map<String, Object> body) {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");
        return R.ok(reviewService.reviewLessonPlan(userId, body));
    }

    @PostMapping("/question-bank")
    public R<Map<String, Object>> reviewQuestionBank(@RequestBody Map<String, Object> body) {
        Long userId = CurrentUserHolder.currentId();
        if (userId == null) throw new BusinessException(401, "未登录");
        return R.ok(reviewService.reviewQuestionBank(userId, body));
    }
}
