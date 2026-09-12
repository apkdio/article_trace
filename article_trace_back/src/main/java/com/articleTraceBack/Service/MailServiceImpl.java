package com.articleTraceBack.Service;

import com.articleTraceBack.config.NotificationProperties;
import com.articleTraceBack.mapper.NotificationMailMapper;
import com.articleTraceBack.pojo.NotificationMail;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件投递服务实现。
 *
 * <p><b>retry_count 语义</b>：累计失败次数（首次投递失败即记 1）。重试条件为
 * {@code failed 且 retry_count ≤ maxRetry}，即默认最多「首次 + 2 次重试」共 3 次尝试。</p>
 */
@Slf4j
@Service
public class MailServiceImpl implements MailService {

    private final NotificationMailMapper mailMapper;
    private final MailDeliverer mailDeliverer;
    private final NotificationProperties properties;

    public MailServiceImpl(NotificationMailMapper mailMapper,
                           MailDeliverer mailDeliverer,
                           NotificationProperties properties) {
        this.mailMapper = mailMapper;
        this.mailDeliverer = mailDeliverer;
        this.properties = properties;
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
            mail.setStatus(MailDeliverer.STATUS_PENDING);
            mail.setRetryCount(0);
            mail.setCreateTime(LocalDateTime.now());
            mailMapper.insert(mail);
            // 跨 bean 调用，@Async 才生效
            mailDeliverer.deliver(mail.getId());
        } catch (Exception e) {
            log.error("enqueue mail failed: to={}, subject={}", toEmail, subject, e);
        }
    }

    @Override
    public int retryFailed() {
        int maxRetry = properties.getMail().getMaxRetry();
        try {
            QueryWrapper<NotificationMail> wrapper = new QueryWrapper<>();
            wrapper.eq("status", MailDeliverer.STATUS_FAILED)
                    .le("retry_count", maxRetry);
            List<NotificationMail> list = mailMapper.selectList(wrapper);
            for (NotificationMail mail : list) {
                mailDeliverer.deliver(mail.getId());
            }
            return list.size();
        } catch (Exception e) {
            log.error("retry failed mails error", e);
            return 0;
        }
    }
}
