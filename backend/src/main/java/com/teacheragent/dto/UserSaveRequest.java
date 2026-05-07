package com.teacheragent.dto;

import lombok.Data;

@Data
public class UserSaveRequest {
    /** 不传 = 新增；传 = 编辑 */
    private Long id;
    private String username;
    /** 留空 = 不改密；新增时必填 */
    private String password;
    private String role;
    private String email;
    private String realName;
    private String status;
    private Integer monthlyQuota;
    private Integer points;
}
