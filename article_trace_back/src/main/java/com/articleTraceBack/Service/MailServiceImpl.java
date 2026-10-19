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

    /** pending / sending 的卡死判定阈值：投递是秒级动作，超过它视为进程中断留下的悬挂行 */
    private static final int STUCK_MINUTES = 10;

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
        NotificationMail mail = new NotificationMail();
        mail.setToEmail(toEmail);
        mail.setSubject(subject);
        mail.setContent(content);
        mail.setContentHtml(contentHtml == null || contentHtml.isBlank() ? null : contentHtml);
        mail.setStatus(STATUS_PENDING);
        mail.setRetryCount(0);
        mail.setCreateTime(LocalDateTime.now());
        try {
            mailMapper.insert(mail);
        } catch (Exception e) {
            log.error("enqueue mail failed: to={}, subject={}", toEmail, subject, e);
            return;
        }
        try {
            submit(mail.getId());
        } catch (Exception e) {
            // 线程池打满或已关闭：标 failed 交给重试扫描。留在 pending 而进程随后退出的话，
            // 修复前没有任何任务会再捡起它
            log.error("submit mail task rejected, mark failed: id={}", mail.getId(), e);
            markFailed(mail.getId(), "submit rejected, see server log");
        }
    }

    @Override
    public int retryFailed() {
        int maxRetry = properties.getMail().getMaxRetry();
        try {
            LocalDateTime stuckBefore = LocalDateTime.now().minusMinutes(STUCK_MINUTES);
            QueryWrapper<NotificationMail> wrapper = new QueryWrapper<>();
            // 三类要补投：failed 未超限的常规重试；pending 卡住的（进程被杀、线程池拒绝）；
            // sending 卡住的（投递中途进程退出，sending_since 停在旧时刻；历史行可能为 NULL）
            wrapper.and(w -> w
                    .eq("status", STATUS_FAILED).le("retry_count", maxRetry)
                    .or(x -> x.eq("status", STATUS_PENDING).lt("create_time", stuckBefore))
                    .or(x -> x.eq("status", STATUS_SENDING)
                            .and(y -> y.isNull("sending_since").or().lt("sending_since", stuckBefore))));
            List<NotificationMail> list = mailMapper.selectList(wrapper);
            int claimed = 0;
            for (NotificationMail mail : list) {
                if (claim(mail, stuckBefore)) {
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

    /**
     * 领取（置 sending）并刷新 {@code sending_since}：只有真正改动了行才算领到。
     *
     * <p>{@code sending} 分支必须带「上次领取已过阈值」的条件——对本来就是 sending 的行，
     * 只按 status 匹配等于「匹配但值不变」的更新，而驱动默认按 CLIENT_FOUND_ROWS 语义返回 1，
     * 重叠的两次扫描会把同一封信投两遍。刷新 sending_since 同时也让这个判断在
     * 「按改变行数」语义下依然排他。</p>
     */
    private boolean claim(NotificationMail mail, LocalDateTime stuckBefore) {
        UpdateWrapper<NotificationMail> claim = new UpdateWrapper<>();
        claim.eq("id", mail.getId())
                .set("status", STATUS_SENDING)
                .set("sending_since", LocalDateTime.now());
        if (STATUS_SENDING.equals(mail.getStatus())) {
            claim.eq("status", STATUS_SENDING)
                    .and(w -> w.isNull("sending_since").or().lt("sending_since", stuckBefore));
        } else {
            // pending / failed：状态真的会变，天然排他
            claim.eq("status", mail.getStatus());
        }
        return mailMapper.update(null, claim) == 1;
    }

    /** 把一封还停在 pending / sending 的行标成 failed（提交被拒等场景；不计入重试次数上限的消耗） */
    private void markFailed(Long mailId, String error) {
        try {
            UpdateWrapper<NotificationMail> fail = new UpdateWrapper<>();
            fail.eq("id", mailId).in("status", STATUS_PENDING, STATUS_SENDING)
                    .set("status", STATUS_FAILED).set("error", error);
            mailMapper.update(null, fail);
        } catch (Exception e) {
            log.error("mark mail failed error: id={}", mailId, e);
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
