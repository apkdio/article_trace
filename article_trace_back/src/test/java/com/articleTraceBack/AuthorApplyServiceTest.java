package com.articleTraceBack;

import com.articleTraceBack.Service.AuthorApplyService;
import com.articleTraceBack.mapper.AuthorApplyMapper;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.AuthorApply;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作者申请-审批集成测试。
 *
 * <p>关闭邮件渠道（properties），避免测试往站长邮箱真实发信。</p>
 * <pre>mvn test -Dtest=AuthorApplyServiceTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class AuthorApplyServiceTest {

    @Autowired
    private AuthorApplyService applyService;

    @Autowired
    private AuthorApplyMapper applyMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    private Integer testUserId;
    private String testUsername;

    @BeforeEach
    public void setUp() {
        // username 列是 varchar(20)，保持短且唯一
        testUsername = "ap" + (System.currentTimeMillis() % 100000000L);
        User user = new User();
        user.setUsername(testUsername);
        user.setNickname("申请测试");
        user.setPassword("x");
        user.setResetPass("x");
        user.setEmail("");
        user.setType(2); // 读者
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        testUserId = user.getId();
    }

    @AfterEach
    public void tearDown() {
        if (testUserId == null) {
            return;
        }
        QueryWrapper<AuthorApply> aw = new QueryWrapper<>();
        aw.eq("user_id", testUserId);
        applyMapper.delete(aw);

        // 发给申请人本人的通知
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", testUserId);
        notificationMapper.delete(nw);

        // 发给站长的测试通知（内容里带测试用户名，便于精确清理）
        QueryWrapper<Notification> masterNw = new QueryWrapper<>();
        masterNw.like("content", testUsername);
        notificationMapper.delete(masterNw);

        userMapper.deleteById(testUserId);
    }

    @Test
    public void testSubmitAndApprove() {
        // 1. 提交申请
        assertTrue(applyService.submit(testUserId, "想写文章"), "首次提交应成功");
        AuthorApply mine = applyService.findMine(testUserId);
        assertNotNull(mine, "应能查到我提交的申请");
        assertEquals(AuthorApply.STATUS_PENDING, mine.getStatus());
        assertEquals("想写文章", mine.getReason());

        // 2. 重复提交被拒
        assertFalse(applyService.submit(testUserId, "再提交一次"), "有待审申请时不应允许重复提交");

        // 3. 待审数量
        assertTrue(applyService.pendingCount() >= 1);

        // 4. 审批通过
        assertTrue(applyService.review(mine.getId(), true, null, testUserId));

        // 5. 状态变为通过，且用户被提升为作者
        AuthorApply after = applyMapper.selectById(mine.getId());
        assertEquals(AuthorApply.STATUS_APPROVED, after.getStatus());
        assertNotNull(after.getReviewTime());
        assertEquals(1, userMapper.selectById(testUserId).getType(), "通过后应升级为作者");

        // 6. 申请人收到通知
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", testUserId).eq("title", "作者申请已通过");
        assertTrue(notificationMapper.selectCount(nw) >= 1, "申请人应收到通过通知");

        // 7. 已处理的申请不能重复审批
        assertFalse(applyService.review(mine.getId(), true, null, testUserId), "已处理的申请不应能重复审批");
    }

    @Test
    public void testSubmitAndReject() {
        assertTrue(applyService.submit(testUserId, "申请理由"));
        AuthorApply mine = applyService.findMine(testUserId);

        assertTrue(applyService.review(mine.getId(), false, "暂不符合要求", testUserId));

        AuthorApply after = applyMapper.selectById(mine.getId());
        assertEquals(AuthorApply.STATUS_REJECTED, after.getStatus());
        assertEquals("暂不符合要求", after.getRejectReason());
        // 拒绝不改身份
        assertEquals(2, userMapper.selectById(testUserId).getType(), "拒绝后身份不应变化");

        // 被拒后可重新申请
        assertTrue(applyService.submit(testUserId, "再次申请"), "被拒后应允许重新申请");
    }
}
