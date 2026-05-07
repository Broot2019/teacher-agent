package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("question_item")
public class QuestionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bankId;
    private String type;
    private String knowledge;
    private String stem;
    private String difficulty;
    private String answer;
    private String explanation;
    private String optionsJson;
    private Integer sortOrder;
    private Long ownerId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
