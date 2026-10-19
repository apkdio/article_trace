package com.articleTraceBack;

import com.articleTraceBack.Service.MailServiceImpl;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件投递集成测试：走真实 SMTP，验证落库、异步投递与失败重试。
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

        mailService.send(to, "邮件投递测试", "这是一封来自异步投递链路的测试邮件。");

        NotificationMail latest = latestMail();
        assertNotNull(latest, "邮件应已落库");
        assertEquals(to, latest.getToEmail());

        NotificationMail done = awaitSent(latest.getId());
        assertEquals(MailServiceImpl.STATUS_SENT, done.getStatus(), "真实 SMTP 下应投递成功");
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
        failed.setStatus(MailServiceImpl.STATUS_FAILED);
        failed.setRetryCount(1);
        failed.setCreateTime(LocalDateTime.now());
        mailMapper.insert(failed);

        int retried = mailService.retryFailed();
        assertTrue(retried >= 1, "应至少提交 1 条重试");

        NotificationMail done = awaitSent(failed.getId());
        assertEquals(MailServiceImpl.STATUS_SENT, done.getStatus(), "重试后应投递成功");

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
            if (mail != null && MailServiceImpl.STATUS_SENT.equals(mail.getStatus())) {
                return mail;
            }
            Thread.sleep(1000);
        }
        return mailMapper.selectById(id);
    }

    /** 卡死的 pending 行（模拟进程在投递前被杀）应被补投扫描捡起 */
    @Test
    public void stuckPendingRowIsRescuedByRetryScan() throws Exception {
        NotificationMail stuck = new NotificationMail();
        stuck.setToEmail("nobody@example.invalid");
        stuck.setSubject("P1-6 卡死补投");
        stuck.setContent("create_time 已超 10 分钟的 pending 行");
        stuck.setStatus(MailServiceImpl.STATUS_PENDING);
        stuck.setRetryCount(0);
        stuck.setCreateTime(LocalDateTime.now().minusMinutes(30));
        mailMapper.insert(stuck);

        int retried = mailService.retryFailed();
        assertTrue(retried >= 1, "卡死的 pending 行应被补投扫描提交");

        NotificationMail after = mailMapper.selectById(stuck.getId());
        assertTrue(MailServiceImpl.STATUS_SENDING.equals(after.getStatus())
                        || MailServiceImpl.STATUS_SENT.equals(after.getStatus())
                        || MailServiceImpl.STATUS_FAILED.equals(after.getStatus()),
                "卡死行应离开 pending（投递结果取决于 SMTP，这里只验证它被捡起）");
        mailMapper.deleteById(stuck.getId());
    }

    /** 刚落库的 pending 行（投递还没跑完的正常窗口）不得被扫描抢走 */
    @Test
    public void freshPendingRowIsNotRescued() {
        NotificationMail fresh = new NotificationMail();
        fresh.setToEmail("nobody@example.invalid");
        fresh.setSubject("P1-6 新行不抢");
        fresh.setContent("create_time 是现在");
        fresh.setStatus(MailServiceImpl.STATUS_PENDING);
        fresh.setRetryCount(0);
        fresh.setCreateTime(LocalDateTime.now());
        mailMapper.insert(fresh);

        mailService.retryFailed();

        NotificationMail after = mailMapper.selectById(fresh.getId());
        assertTrue(MailServiceImpl.STATUS_PENDING.equals(after.getStatus())
                        || MailServiceImpl.STATUS_SENT.equals(after.getStatus()),
                "10 分钟内的 pending 行不该被补投扫描领走");
        mailMapper.deleteById(fresh.getId());
    }

    /**
     * 卡死的 {@code sending} 行要能被捡起，但「刚被领取、还没投完」的 sending 行不能再被领取一次
     * （回归：只按 status 匹配等于「匹配但值不变」的更新，驱动返回 1，同一封会投两遍）。
     */
    @Test
    public void sendingRowIsClaimedOnlyWhenStale() throws Exception {
        // ① 刚刚领取过的 sending 行：sending_since 是现在 → 不该被再领一次
        NotificationMail fresh = new NotificationMail();
        fresh.setToEmail("nobody@example.invalid");
        fresh.setSubject("sending 领取排他测试");
        fresh.setContent("刚领取，投递中");
        fresh.setStatus(MailServiceImpl.STATUS_SENDING);
        fresh.setRetryCount(0);
        fresh.setCreateTime(LocalDateTime.now());
        fresh.setSendingSince(LocalDateTime.now());
        mailMapper.insert(fresh);

        mailService.retryFailed();
        assertEquals(MailServiceImpl.STATUS_SENDING, mailMapper.selectById(fresh.getId()).getStatus(),
                "sending_since 是才写的，不该被第二次扫描领走（领走就会重复投递）");

        // ② 把领取时刻推回阈值之前：这时才该被捡起（进程中途退出的自愈路径）
        NotificationMail stale = new NotificationMail();
        stale.setToEmail("nobody@example.invalid");
        stale.setSubject("sending 卡死自愈测试");
        stale.setContent("领取时刻已超阈值");
        stale.setStatus(MailServiceImpl.STATUS_SENDING);
        stale.setRetryCount(0);
        stale.setCreateTime(LocalDateTime.now().minusMinutes(30));
        stale.setSendingSince(LocalDateTime.now().minusMinutes(30));
        mailMapper.insert(stale);

        mailService.retryFailed();
        NotificationMail rescued = awaitSendOutcome(stale.getId());
        assertFalse(MailServiceImpl.STATUS_SENDING.equals(rescued.getStatus()),
                "卡死的 sending 行应被捡起并重新投递（结果取决于 SMTP，只要离开 sending 即可）");

        mailMapper.deleteById(fresh.getId());
        mailMapper.deleteById(stale.getId());
    }

    /** 轮询等待行离开 sending（最多 20 秒），投递结果本身取决于 SMTP */
    private NotificationMail awaitSendOutcome(Long id) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            NotificationMail mail = mailMapper.selectById(id);
            if (mail != null && !MailServiceImpl.STATUS_SENDING.equals(mail.getStatus())) {
                return mail;
            }
            Thread.sleep(1000);
        }
        return mailMapper.selectById(id);
    }
}
