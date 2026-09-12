package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.Service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 失败邮件重试任务。
 *
 * <p>每 10 分钟扫描 {@code notification_mail} 中 status=failed 且失败次数未超上限的记录重新投递。</p>
 */
@Slf4j
@Component
@EnableScheduling
public class MailRetryTask {

    private final MailService mailService;

    @Value("${notification.mail.enabled:true}")
    private boolean enabled;

    public MailRetryTask(MailService mailService) {
        this.mailService = mailService;
    }

    @Scheduled(cron = "${notification.mail.retryCron:0 */10 * * * ?}")
    public void retryFailedMails() {
        if (!enabled) {
            return;
        }
        try {
            int count = mailService.retryFailed();
            if (count > 0) {
                log.info("mail retry: {} failed mail(s) resubmitted", count);
            }
        } catch (Exception e) {
            log.error("mail retry task failed", e);
        }
    }
}
