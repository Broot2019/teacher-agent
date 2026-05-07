CREATE TABLE IF NOT EXISTS course_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id    BIGINT       NOT NULL,
    course_name      VARCHAR(200) NOT NULL DEFAULT 'Java 程序设计基础',
    major            VARCHAR(200) NOT NULL DEFAULT '大数据技术',
    education_level  VARCHAR(100) NOT NULL DEFAULT '高职院校',
    student_description VARCHAR(500) NOT NULL DEFAULT '学生基础参差不齐',
    teaching_mode    VARCHAR(100) NOT NULL DEFAULT '理实一体',
    class_name       VARCHAR(200) NULL,
    programming_language VARCHAR(32) NULL COMMENT '编程语言（仅程序设计类课程使用：java/python/cpp/c/csharp/go/javascript/typescript/php/sql 等）',
    is_active        TINYINT      NOT NULL DEFAULT 0,
    create_time      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
