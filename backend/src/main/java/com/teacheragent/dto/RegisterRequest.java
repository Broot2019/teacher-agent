package com.teacheragent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    /** 注册邀请码（当系统要求时必填） */
    private String invitationCode;
    private String email;
    private String realName;
}
