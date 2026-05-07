package com.teacheragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "teacher-agent")
public class AppProperties {

    private String dataDir = "./data";
    private String uploadDir = "./data/uploads";
    private String outputDir = "./data/outputs";
    private String templateDir = "./data/templates";
    /** 文件解析缓存目录（PPT/PDF/DOCX 解析结果按 sha256 落盘，重跑/重生命中后秒级返回） */
    private String cacheDir = "./data/cache";
}
