package com.articleTraceBack.Service;

import com.articleTraceBack.Utils.EmailUtil;
import com.articleTraceBack.config.NotificationProperties;
import com.articleTraceBack.mapper.NotificationMailMapper;
import com.articleTraceBack.pojo.NotificationMail;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件投递服务实现：落库、异步投递、失败重试。用显式线程池而非 {@code @Async}（同类自调用不会异步）。
 * {@code retry_count} 为累计失败次数，默认最多「首次 + 2 次重试」。
 */
@Slf4j
@Service
public class MailServiceImpl implements MailService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENDING = "sending";
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
    public void send(String toEmail, String subject, String content, String contentHtml) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("skip mail: empty receiver, subject={}", subject);
            return;
        }
        try {
            NotificationMail mail = new NotificationMail();
            mail.setToEmail(toEmail);
            mail.setSubject(subject);
            mail.setContent(content);
            mail.setContentHtml(contentHtml == null || contentHtml.isBlank() ? null : contentHtml);
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
            int claimed = 0;
            for (NotificationMail mail : list) {
                // 条件更新把这条领走：重叠的两次扫描里只有一次能把 failed 改成 sending，
                // 否则同一封信会被投递两遍。
                UpdateWrapper<NotificationMail> claim = new UpdateWrapper<>();
                claim.eq("id", mail.getId()).eq("status", STATUS_FAILED).set("status", STATUS_SENDING);
                if (mailMapper.update(null, claim) == 1) {
                    claimed++;
                    submit(mail.getId());
                }
            }
            return claimed;
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
            String html = mail.getContentHtml();
            boolean ok = (html == null || html.isBlank())
                    ? emailUtil.sendText(mail.getToEmail(), mail.getSubject(), mail.getContent())
                    : emailUtil.sendMultipart(mail.getToEmail(), mail.getSubject(), mail.getContent(), html);
            UpdateWrapper<NotificationMail> write = new UpdateWrapper<>();
            write.eq("id", mailId).in("status", STATUS_PENDING, STATUS_SENDING);
            if (ok) {
                write.set("status", STATUS_SENT).set("sent_time", LocalDateTime.now()).set("error", null);
            } else {
                int failures = (mail.getRetryCount() == null ? 0 : mail.getRetryCount()) + 1;
                write.set("status", STATUS_FAILED).set("error", "send failed, see server log")
                        .setSql("retry_count = retry_count + 1");
                log.warn("mail delivery failed: id={}, to={}, failures={}", mailId, mail.getToEmail(), failures);
            }
            mailMapper.update(null, write);
        } catch (Exception e) {
            log.error("deliver mail error: id={}", mailId, e);
            // 领取时已置 sending，异常不回写就会永久卡在 sending、再也不会被重试
            UpdateWrapper<NotificationMail> back = new UpdateWrapper<>();
            back.eq("id", mailId).eq("status", STATUS_SENDING)
                    .set("status", STATUS_FAILED).set("error", "deliver error, see server log")
                    .setSql("retry_count = retry_count + 1");
            mailMapper.update(null, back);
        }
    }
}
