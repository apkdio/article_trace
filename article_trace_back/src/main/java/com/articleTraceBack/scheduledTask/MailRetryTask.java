package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.Service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 失败邮件重试任务：每 10 分钟扫描 {@code notification_mail} 中 status=failed 且未超重试上限的记录重新投递。 */
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

    /** 重投失败邮件（每 10 分钟） */
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
