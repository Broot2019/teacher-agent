package com.teacheragent.dto;

import lombok.Data;

@Data
public class CourseConfigSaveRequest {

    private Long id;

    private String courseName;

    private String major;

    private String educationLevel;

    private String studentDescription;

    private String teachingMode;

    private String className;

    /** 编程语言（仅程序设计类课程使用） */
    private String programmingLanguage;
}
