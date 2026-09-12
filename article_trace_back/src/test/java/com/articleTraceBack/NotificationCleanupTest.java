package com.articleTraceBack;

import com.articleTraceBack.Service.NotificationService;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.pojo.Notification;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站内信清理（30 天）测试。
 *
 * <pre>mvn test -Dtest=NotificationCleanupTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class NotificationCleanupTest {

    private static final int TEST_USER_ID = 999997;
    private static final int KEEP_DAYS = 30;

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

    /** 超期（无论已读未读）被删，未超期保留 */
    @Test
    public void testCleanupExpired() {
        Integer expiredUnread = insert("超期未读", LocalDateTime.now().minusDays(31), 0);
        Integer expiredRead = insert("超期已读", LocalDateTime.now().minusDays(45), 1);
        Integer freshUnread = insert("未超期未读", LocalDateTime.now().minusDays(29), 0);
        Integer boundary = insert("刚好边界内", LocalDateTime.now().minusDays(1), 0);

        int deleted = notificationService.cleanupExpired(KEEP_DAYS);

        assertTrue(deleted >= 2, "应至少删除 2 条超期记录，实际 " + deleted);
        // 超期的两条（含已读）都应被删掉
        assertNull(notificationMapper.selectById(expiredUnread), "超期未读应被删除");
        assertNull(notificationMapper.selectById(expiredRead), "超期已读同样应被删除");
        // 未超期的保留
        assertTrue(notificationMapper.selectById(freshUnread) != null, "未超期记录应保留");
        assertTrue(notificationMapper.selectById(boundary) != null, "未超期记录应保留");
    }

    /** keepDays <= 0 时不做任何清理（防御性） */
    @Test
    public void testCleanupWithInvalidKeepDays() {
        Integer old = insert("很久以前", LocalDateTime.now().minusDays(100), 0);
        assertEquals(0, notificationService.cleanupExpired(0), "keepDays<=0 时不应删除任何记录");
        assertTrue(notificationMapper.selectById(old) != null, "记录应保留");
    }

    private Integer insert(String title, LocalDateTime createTime, int isRead) {
        Notification n = new Notification();
        n.setTitle(title);
        n.setSenderId(-1);
        n.setReceiverId(TEST_USER_ID);
        n.setContent(title);
        n.setCreateTime(createTime);
        n.setIsRead(isRead);
        notificationMapper.insert(n);
        return n.getId();
    }
}
