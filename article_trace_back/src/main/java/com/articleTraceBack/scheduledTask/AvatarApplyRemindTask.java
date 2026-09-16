package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.Service.AvatarApplyService;
import com.articleTraceBack.Service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 头像待审提醒任务。
 *
 * <p>每 12 小时检查是否有待审批的头像，有则向站长发送提醒（场景 {@code avatar-remind}
 * 在配置中为 mail，即只发邮件，不产生站内信）。</p>
 */
@Slf4j
@Component
@EnableScheduling
public class AvatarApplyRemindTask {

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;

    /** 与 application.yml 的 notification.scenes 对齐 */
    private static final String SCENE_REMIND = "avatar-remind";

    private final AvatarApplyService avatarApplyService;
    private final NotificationService notificationService;

    public AvatarApplyRemindTask(AvatarApplyService avatarApplyService,
                                 NotificationService notificationService) {
        this.avatarApplyService = avatarApplyService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${avatar-apply.remindCron:0 0 0/12 * * ?}")
    public void remindPendingApplies() {
        try {
            int pending = avatarApplyService.pendingCount();
            if (pending <= 0) {
                return;
            }
            notificationService.notifyRole(ROLE_MASTER, SCENE_REMIND, "有头像待审核",
                    "当前有 " + pending + " 条头像待审核，请及时处理。");
            log.info("avatar apply remind sent: {} pending apply(s)", pending);
        } catch (Exception e) {
            log.error("avatar apply remind task failed", e);
        }
    }
}
