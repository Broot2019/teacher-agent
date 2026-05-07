package com.teacheragent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;

    /** 验证码会话 key（来自 GET /api/captcha 返回的 key 字段） */
    private String captchaKey;
    /** 用户输入的验证码字符 */
    private String captchaCode;
}
