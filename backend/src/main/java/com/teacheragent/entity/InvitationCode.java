package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("invitation_code")
public class InvitationCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Integer initialPoints;
    private Integer initialQuota;
    private Long createdBy;
    private Long usedBy;
    private LocalDateTime usedTime;
    private LocalDateTime expireTime;
    private String note;
    /** unused / used / expired / disabled */
    private String status;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
