package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lesson_plan_history")
public class LessonPlanHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String chapter;

    private String weekNo;

    private Integer weekStart;

    private Integer weekEnd;

    private Integer hoursPerWeek;

    /** single / weekly / full */
    private String packageMode;

    private String llmProvider;

    private String llmModel;

    private String sourceFiles;

    private String outputFilePath;

    private String outputFileName;

    private String status;

    private String errorMsg;

    private Long ownerId;

    private String taskId;

    /** 使用的资料库ID列表（逗号分隔） */
    private String materialIds;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
