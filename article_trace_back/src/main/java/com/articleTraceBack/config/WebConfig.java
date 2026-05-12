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
                .excludePathPatterns(excludeUrl);
    }
}
