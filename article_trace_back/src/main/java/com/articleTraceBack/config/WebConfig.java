package com.articleTraceBack.config;

import com.articleTraceBack.Utils.TokenCheck;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
// 配置拦截器的使用
public class WebConfig implements WebMvcConfigurer {
    private final TokenCheck tokenCheck;
    @Value("${spring.tokenCheck.excludeUrls}")
    String[] excludeUrl;

    public WebConfig(TokenCheck tokenCheck) {
        this.tokenCheck = tokenCheck;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenCheck)
                .excludePathPatterns(excludeUrl)
                // 站点功能开关在登录页就要读，必须免登录。这类「结构上就该公开」的路径直接写死在这里，
                // 不进配置——和 TokenCheck 硬编码放行 /reader/** 是同一个理由：它不随环境变化，
                // 写进 yml 只会平白多出四处要同步的地方。
                .excludePathPatterns("/site/features");
    }
}
