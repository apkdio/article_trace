package com.articleTraceBack;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.articleTraceBack.mapper")
@EnableScheduling
public class ArticleTraceBackApplication {

    /** 应用入口 */
    public static void main(String[] args) {
        SpringApplication.run(ArticleTraceBackApplication.class, args);
    }

}
