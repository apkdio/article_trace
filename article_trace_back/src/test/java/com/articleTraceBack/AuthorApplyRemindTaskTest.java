package com.articleTraceBack;

import com.articleTraceBack.Service.AuthorApplyService;
import com.articleTraceBack.mapper.AuthorApplyMapper;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.User;
import com.articleTraceBack.scheduledTask.AuthorApplyRemindTask;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 待审提醒任务测试：有待审申请时提醒站长，无待审时不打扰。
 *
 * <p>测试覆盖：关闭邮件渠道（避免真实发信），并把提醒场景临时改为 inbox，
 * 这样可以直接断言「站长是否收到提醒」。</p>
 *
 * <pre>mvn test -Dtest=AuthorApplyRemindTaskTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "notification.mail.enabled=false",
        "notification.scenes.author-apply-remind=inbox"
})
public class AuthorApplyRemindTaskTest {

    private static final int ROLE_MASTER = 0;
    private static final String REMIND_TITLE = "有作者申请待审批";

    @Autowired
    private AuthorApplyRemindTask remindTask;

    @Autowired
    private AuthorApplyService applyService;

    @Autowired
    private AuthorApplyMapper applyMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    private Integer testApplyId;

    @AfterEach
    public void tearDown() {
        if (testApplyId != null) {
            applyMapper.deleteById(testApplyId);
            testApplyId = null;
        }
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("title", REMIND_TITLE);
        notificationMapper.delete(nw);
    }

    @Test
    public void testRemindWhenPendingExists() {
        User master = userMapper.selectList(new QueryWrapper<User>().eq("type", ROLE_MASTER)).get(0);

        // 造一条待审申请
        AuthorApply apply = new AuthorApply();
        apply.setUserId(master.getId());
        apply.setReason("测试待审提醒");
        apply.setStatus(AuthorApply.STATUS_PENDING);
        apply.setCreateTime(LocalDateTime.now());
        applyMapper.insert(apply);
        testApplyId = apply.getId();

        assertTrue(applyService.pendingCount() >= 1, "应存在待审申请");

        // 执行提醒任务
        remindTask.remindPendingApplies();

        // 站长应收到提醒
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", master.getId()).eq("title", REMIND_TITLE);
        assertTrue(notificationMapper.selectCount(nw) >= 1, "站长应收到待审提醒");

        // 提醒不改申请状态
        assertTrue(applyService.pendingCount() >= 1, "提醒不应改变待审状态");
    }

    @Test
    public void testNoRemindWhenNoPending() {
        // 清掉所有待审申请
        QueryWrapper<AuthorApply> aw = new QueryWrapper<>();
        aw.eq("status", AuthorApply.STATUS_PENDING);
        applyMapper.delete(aw);

        remindTask.remindPendingApplies();

        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("title", REMIND_TITLE);
        assertTrue(notificationMapper.selectCount(nw) == 0, "无待审申请时不应产生提醒");
    }
}
