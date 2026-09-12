package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.Service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 站内信清理任务。
 *
 * <p>每天凌晨删除超过 {@code notification.cleanup.keepDays} 天的站内信（默认 30 天，无论是否已读）。</p>
 */
@Slf4j
@Component
@EnableScheduling
public class NotificationCleanupTask {

    private final NotificationService notificationService;

    @Value("${notification.cleanup.keepDays:30}")
    private int keepDays;

    public NotificationCleanupTask(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${notification.cleanup.cron:0 0 3 * * ?}")
    public void cleanupExpiredNotifications() {
        try {
            int deleted = notificationService.cleanupExpired(keepDays);
            if (deleted > 0) {
                log.info("notification cleanup task done: {} record(s) removed", deleted);
            }
        } catch (Exception e) {
            log.error("notification cleanup task failed", e);
        }
    }
}
