package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_bank_history")
public class QuestionBankHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String chapter;

    private String questionTypes;

    private String difficultyDist;

    private Integer totalCount;

    private String llmProvider;

    private String llmModel;

    private String sourceFiles;

    private String outputFilePath;

    private String outputFileName;

    private String status;

    private String errorMsg;

    private Long ownerId;

    private String taskId;

    private String materialIds;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
