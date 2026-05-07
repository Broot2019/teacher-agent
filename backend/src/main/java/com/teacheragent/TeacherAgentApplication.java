package com.teacheragent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.teacheragent.mapper")
public class TeacherAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeacherAgentApplication.class, args);
    }

}
