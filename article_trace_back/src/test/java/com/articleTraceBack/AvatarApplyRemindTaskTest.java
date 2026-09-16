package com.articleTraceBack;

import com.articleTraceBack.Service.AvatarApplyService;
import com.articleTraceBack.mapper.AvatarApplyMapper;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.AvatarApply;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.User;
import com.articleTraceBack.scheduledTask.AvatarApplyRemindTask;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 头像待审提醒任务测试：有待审头像时提醒站长，无待审时不打扰。
 *
 * <p>测试覆盖：关闭邮件渠道（避免真实发信），并把提醒场景临时改为 inbox，
 * 这样可以直接断言「站长是否收到提醒」。</p>
 *
 * <pre>mvn test -Dtest=AvatarApplyRemindTaskTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "notification.mail.enabled=false",
        "notification.scenes.avatar-remind=inbox"
})
public class AvatarApplyRemindTaskTest {

    private static final int ROLE_MASTER = 0;
    private static final String REMIND_TITLE = "有头像待审核";

    @Autowired
    private AvatarApplyRemindTask remindTask;

    @Autowired
    private AvatarApplyService avatarApplyService;

    @Autowired
    private AvatarApplyMapper applyMapper;

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
        User master = userMapper.selectList(new QueryWrapper<User>().eq("type", ROLE_MASTER)).getFirst();

        // 造一条待审记录（pending_pic 为 NOT NULL，必须给值）
        AvatarApply apply = new AvatarApply();
        apply.setUserId(master.getId());
        apply.setPendingPic("zz-test-remind.png");
        apply.setStatus(AvatarApply.STATUS_PENDING);
        apply.setCreateTime(LocalDateTime.now());
        applyMapper.insert(apply);
        testApplyId = apply.getId();

        assertTrue(avatarApplyService.pendingCount() >= 1, "应存在待审头像");

        remindTask.remindPendingApplies();

        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", master.getId()).eq("title", REMIND_TITLE);
        assertTrue(notificationMapper.selectCount(nw) >= 1, "站长应收到头像待审提醒");

        // 提醒不改记录状态
        assertEquals(AvatarApply.STATUS_PENDING, applyMapper.selectById(testApplyId).getStatus(),
                "提醒不应改变待审状态");
    }

    @Test
    public void testNoRemindWhenNoPending() {
        // 清掉所有待审记录
        QueryWrapper<AvatarApply> aw = new QueryWrapper<>();
        aw.eq("status", AvatarApply.STATUS_PENDING);
        applyMapper.delete(aw);

        remindTask.remindPendingApplies();

        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("title", REMIND_TITLE);
        assertEquals(0L, notificationMapper.selectCount(nw).longValue(), "无待审时不应产生提醒");
    }
}
