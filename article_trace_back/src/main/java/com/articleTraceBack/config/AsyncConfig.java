package com.articleTraceBack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 邮件投递线程池。
 *
 * <p>邮件发送走独立线程池，避免 SMTP 阻塞业务线程。队列满时由调用线程执行（不丢任务）；
 * 即使任务被丢弃，{@code notification_mail} 里的 pending/failed 记录也会被重试任务补发。</p>
 *
 * <p>注意：这里**不需要** {@code @EnableAsync} —— 投递是通过 {@code executor.execute(...)}
 * 显式提交的（见 {@code MailServiceImpl}），因此不受 {@code @Async} 同类自调用的限制。</p>
 */
@Configuration
public class AsyncConfig {

    @Bean("mailExecutor")
    public ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
