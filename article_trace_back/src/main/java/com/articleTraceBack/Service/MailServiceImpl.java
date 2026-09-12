package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.EmailUtil;
import com.articleTraceBack.config.NotificationProperties;
import com.articleTraceBack.mapper.NotificationMailMapper;
import com.articleTraceBack.pojo.NotificationMail;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件投递服务实现：落库、异步投递、失败重试。
 *
 * <p><b>为什么用显式线程池而不是 @Async</b>：{@code send()} / {@code retryFailed()} 都在本类内部
 * 触发投递，而 {@code @Async} 依赖 Spring 代理，同类自调用不会异步（会退化成阻塞业务线程的同步发信），
 * 所以这里直接向线程池 {@code execute} 提交任务。</p>
 *
 * <p><b>retry_count 语义</b>：累计失败次数（首次投递失败即记 1）。重试条件为
 * {@code failed 且 retry_count ≤ maxRetry}，即默认最多「首次 + 2 次重试」共 3 次尝试。</p>
 */
@Slf4j
@Service
public class MailServiceImpl implements MailService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_FAILED = "failed";

    private final NotificationMailMapper mailMapper;
    private final EmailUtil emailUtil;
    private final NotificationProperties properties;
    private final ThreadPoolTaskExecutor mailExecutor;

    public MailServiceImpl(NotificationMailMapper mailMapper,
                           EmailUtil emailUtil,
                           NotificationProperties properties,
                           @Qualifier("mailExecutor") ThreadPoolTaskExecutor mailExecutor) {
        this.mailMapper = mailMapper;
        this.emailUtil = emailUtil;
        this.properties = properties;
        this.mailExecutor = mailExecutor;
    }

    @Override
    public void send(String toEmail, String subject, String content) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("skip mail: empty receiver, subject={}", subject);
            return;
        }
        try {
            NotificationMail mail = new NotificationMail();
            mail.setToEmail(toEmail);
            mail.setSubject(subject);
            mail.setContent(content);
            mail.setStatus(STATUS_PENDING);
            mail.setRetryCount(0);
            mail.setCreateTime(LocalDateTime.now());
            mailMapper.insert(mail);
            submit(mail.getId());
        } catch (Exception e) {
            log.error("enqueue mail failed: to={}, subject={}", toEmail, subject, e);
        }
    }

    @Override
    public int retryFailed() {
        int maxRetry = properties.getMail().getMaxRetry();
        try {
            QueryWrapper<NotificationMail> wrapper = new QueryWrapper<>();
            wrapper.eq("status", STATUS_FAILED)
                    .le("retry_count", maxRetry);
            List<NotificationMail> list = mailMapper.selectList(wrapper);
            for (NotificationMail mail : list) {
                submit(mail.getId());
            }
            return list.size();
        } catch (Exception e) {
            log.error("retry failed mails error", e);
            return 0;
        }
    }

    /** 向邮件线程池提交一次投递任务 */
    private void submit(Long mailId) {
        mailExecutor.execute(() -> deliver(mailId));
    }

    /** 投递一次并回写状态（失败时 retry_count 递增） */
    private void deliver(Long mailId) {
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
