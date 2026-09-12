package com.articleTraceBack.scheduledTask;

import com.articleTraceBack.Service.AuthorApplyService;
import com.articleTraceBack.Service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 作者申请待审提醒任务。
 *
 * <p>每天凌晨检查是否有待审批的作者申请，有则向站长发送提醒（场景 {@code author-apply-remind}
 * 在配置中为 mail，即只发邮件，不产生站内信）。</p>
 */
@Slf4j
@Component
@EnableScheduling
public class AuthorApplyRemindTask {

    /** 站长角色 type */
    private static final int ROLE_MASTER = 0;

    /** 与 application.yml 的 notification.scenes 对齐 */
    private static final String SCENE_REMIND = "author-apply-remind";

    private final AuthorApplyService applyService;
    private final NotificationService notificationService;

    public AuthorApplyRemindTask(AuthorApplyService applyService,
                                 NotificationService notificationService) {
        this.applyService = applyService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${author-apply.remindCron:0 0 1 * * ?}")
    public void remindPendingApplies() {
        try {
            int pending = applyService.pendingCount();
            if (pending <= 0) {
                return;
            }
            notificationService.notifyRole(ROLE_MASTER, SCENE_REMIND, "有作者申请待审批",
                    "当前有 " + pending + " 条作者申请待审批，请及时处理。");
            log.info("author apply remind sent: {} pending apply(s)", pending);
        } catch (Exception e) {
            log.error("author apply remind task failed", e);
        }
    }
}
