package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.EmailUtil;
import com.articleTraceBack.mapper.NotificationMailMapper;
import com.articleTraceBack.pojo.NotificationMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 邮件投递器。
 *
 * <p><b>命名注意</b>：不能叫 {@code MailSender} —— Spring Boot 邮件自动配置已注册同名 bean，
 * 会触发 BeanDefinitionOverrideException。</p>
 *
 * <p><b>必须是独立 bean</b>：{@code @Async} 依赖 Spring 代理，同类内部自调用不会异步。</p>
 */
@Slf4j
@Service
public class MailDeliverer {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_FAILED = "failed";

    private final NotificationMailMapper mailMapper;
    private final EmailUtil emailUtil;

    public MailDeliverer(NotificationMailMapper mailMapper, EmailUtil emailUtil) {
        this.mailMapper = mailMapper;
        this.emailUtil = emailUtil;
    }

    /**
     * 异步投递一条已落库的邮件，并回写状态。
     *
     * <p>失败时 {@code retry_count} 递增（记录累计失败次数），由重试任务按上限决定是否再试。</p>
     */
    @Async("mailExecutor")
    public void deliver(Long mailId) {
        try {
            NotificationMail mail = mailMapper.selectById(mailId);
            if (mail == null) {
                log.warn("mail record not found: id={}", mailId);
                return;
            }
            boolean ok = emailUtil.sendText(mail.getToEmail(), mail.getSubject(), mail.getContent());
            if (ok) {
                mail.setStatus(STATUS_SENT);
                mail.setSentTime(LocalDateTime.now());
                mail.setError(null);
            } else {
                int failures = (mail.getRetryCount() == null ? 0 : mail.getRetryCount()) + 1;
                mail.setStatus(STATUS_FAILED);
                mail.setRetryCount(failures);
                mail.setError("send failed, see server log");
                log.warn("mail delivery failed: id={}, to={}, failures={}", mailId, mail.getToEmail(), failures);
            }
            mailMapper.updateById(mail);
        } catch (Exception e) {
            log.error("deliver mail error: id={}", mailId, e);
        }
    }
}
