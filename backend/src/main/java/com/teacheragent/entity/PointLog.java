package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("point_log")
public class PointLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer changeAmount;
    private Integer balance;
    private String reason;
    private String relatedType;
    private String relatedId;
    private LocalDateTime createTime;
}
