package com.articleTraceBack;

import com.articleTraceBack.Service.MailDeliverer;
import com.articleTraceBack.Service.MailService;
import com.articleTraceBack.mapper.NotificationMailMapper;
import com.articleTraceBack.pojo.NotificationMail;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件投递（P2）集成测试：走真实 SMTP，验证落库、异步投递与失败重试。
 *
 * <p>需显式指定收件人才执行（避免常规构建误发邮件）：</p>
 * <pre>mvn test -Dtest=MailServiceTest -Dmail.to=your@mail.com</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class MailServiceTest {

    @Autowired
    private MailService mailService;

    @Autowired
    private NotificationMailMapper mailMapper;

    /** 落库 + 异步投递：最终状态应为 sent */
    @Test
    public void testSendRecordsAndDelivers() throws Exception {
        String to = requireReceiver();

        mailService.send(to, "P2 投递测试", "这是一封来自 P2 异步投递链路的测试邮件。");

        NotificationMail latest = latestMail();
        assertNotNull(latest, "邮件应已落库");
        assertEquals(to, latest.getToEmail());

        NotificationMail done = awaitSent(latest.getId());
        assertEquals(MailDeliverer.STATUS_SENT, done.getStatus(), "真实 SMTP 下应投递成功");
        assertNotNull(done.getSentTime(), "成功时应记录发送时间");

        mailMapper.deleteById(latest.getId());
    }

    /** 失败记录应被重试任务补发成功 */
    @Test
    public void testRetryFailedResubmits() throws Exception {
        String to = requireReceiver();

        NotificationMail failed = new NotificationMail();
        failed.setToEmail(to);
        failed.setSubject("P2 重试测试");
        failed.setContent("这条记录模拟一次投递失败。");
        failed.setStatus(MailDeliverer.STATUS_FAILED);
        failed.setRetryCount(1);
        failed.setCreateTime(LocalDateTime.now());
        mailMapper.insert(failed);

        int retried = mailService.retryFailed();
        assertTrue(retried >= 1, "应至少提交 1 条重试");

        NotificationMail done = awaitSent(failed.getId());
        assertEquals(MailDeliverer.STATUS_SENT, done.getStatus(), "重试后应投递成功");

        mailMapper.deleteById(failed.getId());
    }

    private String requireReceiver() {
        String to = System.getProperty("mail.to");
        Assumptions.assumeTrue(to != null && !to.isBlank(), "未指定 -Dmail.to，跳过真实发信测试");
        return to;
    }

    private NotificationMail latestMail() {
        QueryWrapper<NotificationMail> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id").last("limit 1");
        return mailMapper.selectOne(wrapper);
    }

    /**
     * 轮询等待异步投递完成（最多 40 秒）。
     *
     * <p>不能以「离开 pending」为结束条件 —— 重试场景的记录初始就是 failed，
     * 那样会立刻返回旧状态，造成假失败。</p>
     */
    private NotificationMail awaitSent(Long id) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            NotificationMail mail = mailMapper.selectById(id);
            if (mail != null && MailDeliverer.STATUS_SENT.equals(mail.getStatus())) {
                return mail;
            }
            Thread.sleep(1000);
        }
        return mailMapper.selectById(id);
    }
}
