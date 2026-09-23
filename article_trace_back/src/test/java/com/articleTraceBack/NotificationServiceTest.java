package com.articleTraceBack;

import com.articleTraceBack.Service.NotificationService;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站内通知基础设施集成测试。
 *
 * <pre>mvn test -Dtest=NotificationServiceTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class NotificationServiceTest {

    /** 测试专用收件人 id，避免污染真实用户信箱 */
    private static final int TEST_USER_ID = 999999;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationMapper notificationMapper;

    @AfterEach
    public void cleanup() {
        QueryWrapper<Notification> wrapper = new QueryWrapper<>();
        wrapper.eq("receiver_id", TEST_USER_ID);
        notificationMapper.delete(wrapper);
    }

    @Test
    public void testNotifyAndReadFlow() {
        int before = notificationService.unreadCount(TEST_USER_ID);

        // 1. 渠道解析：email-code 配置为 mail → 不产生站内信；author-apply-approved 为 inbox → 产生
        notificationService.notify(TEST_USER_ID, "email-code", "标题A", "内容A");
        notificationService.notify(TEST_USER_ID, "author-apply-approved", "标题B", "内容B");

        // 2. 只有站内信那条计入未读
        assertEquals(before + 1, notificationService.unreadCount(TEST_USER_ID), "未读数应为 +1");

        // 3. 列表可见，且系统消息发送方为 -1
        PageBean<Notification> page = notificationService.listByReceiver(TEST_USER_ID, null, 1, 10);
        assertEquals(1, page.getTotal(), "应只有 1 条站内信");
        Notification n = page.getItems().get(0);
        assertEquals("标题B", n.getTitle());
        assertEquals(-1, n.getSenderId().intValue(), "系统消息发送方应为 -1");
        assertEquals(0, n.getIsRead().intValue(), "初始应为未读");

        // 4. 标记已读后未读数回落
        assertTrue(notificationService.markRead(TEST_USER_ID, n.getId()), "标记已读应成功");
        assertEquals(before, notificationService.unreadCount(TEST_USER_ID), "已读后未读数应回落");

        // 5. 幂等：重复标记返回 false
        assertFalse(notificationService.markRead(TEST_USER_ID, n.getId()), "重复标记应返回 false");
    }

    @Test
    public void testFilterByTypeAndDelete() {
        // author-apply-* → apply 类型；未配置场景走 defaultChannel(inbox) → system 类型
        notificationService.notify(TEST_USER_ID, "author-apply-approved", "申请通过", "内容X");
        notificationService.notify(TEST_USER_ID, "unknown-scene", "系统消息", "内容Y");

        PageBean<Notification> all = notificationService.listByReceiver(TEST_USER_ID, null, 1, 10);
        assertEquals(2, all.getTotal(), "不传 type 应查全部");

        PageBean<Notification> applies = notificationService.listByReceiver(
                TEST_USER_ID, Notification.TYPE_AUDIT, 1, 10);
        assertEquals(1, applies.getTotal(), "type=audit 应只返回审核相关");
        assertEquals(Notification.TYPE_AUDIT, applies.getItems().get(0).getType(), "类型应落为 audit");

        // 非法 type 退化为查全部，而不是报错或查空
        assertEquals(2, notificationService.listByReceiver(TEST_USER_ID, "not-a-type", 1, 10).getTotal(),
                "非法 type 应退化为查全部");

        // 删除：不能删别人的，能删自己的
        int targetId = all.getItems().get(0).getId();
        assertFalse(notificationService.delete(TEST_USER_ID + 1, targetId), "不应能删除他人的消息");
        assertTrue(notificationService.delete(TEST_USER_ID, targetId), "删除自己的消息应成功");
        assertEquals(1, notificationService.listByReceiver(TEST_USER_ID, null, 1, 10).getTotal(),
                "删除后总数应减一");
    }

    @Test
    public void testNotifyRoleWithUnknownRoleIsSafe() {
        // 不存在的角色 → 无收件人；容错设计下不应抛异常
        notificationService.notifyRole(99, "author-apply-submitted", "标题", "内容");
        assertEquals(0, notificationService.unreadCount(TEST_USER_ID));
    }
}
