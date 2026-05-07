package com.teacheragent.controller;

import com.teacheragent.common.R;
import com.teacheragent.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 图形验证码接口。
 * AuthInterceptor 已经将 /api/captcha 列入白名单，未登录即可获取。
 */
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /** 获取一次新的验证码：返回 key + 图片 dataURL */
    @GetMapping("")
    public R<Map<String, Object>> generate() {
        return R.ok(captchaService.generate());
    }
}
