package com.articleTraceBack;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.articleTraceBack.mapper")
@EnableScheduling
public class ArticleTraceBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArticleTraceBackApplication.class, args);
    }

}
