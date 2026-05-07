package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generation_task")
public class GenerationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** UUID */
    private String taskId;

    /** lesson_plan / question_bank */
    private String type;

    private Long ownerId;

    /** pending / running / success / failed / cancelled */
    private String status;

    private Integer progress;

    private String stageText;

    private String paramsJson;

    private Long resultHistoryId;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    /** 原始上传文件路径 JSON，用于重跑 */
    private String uploadedFiles;

    @TableLogic
    private Integer deleted;
}
