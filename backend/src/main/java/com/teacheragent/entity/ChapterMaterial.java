package com.teacheragent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chapter_material")
public class ChapterMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String chapter;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String course;
    private String description;
    private Long ownerId;
    private Integer isPublic;
    private Integer useCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
