package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 密码哈希（不返回前端） */
    private String passwordHash;

    /** admin / teacher */
    private String role;

    private String email;

    private String realName;

    /** enabled / disabled */
    private String status;

    /** 积分余额 */
    private Integer points;

    private LocalDateTime lastLoginTime;

    /** 每月最大生成次数 */
    private Integer monthlyQuota;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
