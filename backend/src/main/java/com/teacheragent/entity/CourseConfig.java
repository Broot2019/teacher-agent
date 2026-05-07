package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_config")
public class CourseConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private String courseName;

    private String major;

    private String educationLevel;

    private String studentDescription;

    private String teachingMode;

    private String className;

    /** 编程语言（仅程序设计类课程使用，影响编程题生成与编译校验路由） */
    private String programmingLanguage;

    private Integer isActive;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
