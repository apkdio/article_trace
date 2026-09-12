package com.articleTraceBack;

import com.articleTraceBack.Controller.NotificationController;
import com.articleTraceBack.Service.NotificationService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.PageBean;
import com.articleTraceBack.pojo.Result;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 站内信接口层测试：直接调用 Controller，用 ThreadLocal 模拟登录态。
 *
 * <pre>mvn test -Dtest=NotificationControllerTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class NotificationControllerTest {

    /** 测试专用收件人 id */
    private static final int TEST_USER_ID = 999998;

    @Autowired
    private NotificationController notificationController;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationMapper notificationMapper;

    @BeforeEach
    public void login() {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", TEST_USER_ID);
        userInfo.put("name", "test-user");
        ThreadLocalUtil.set(userInfo);
    }

    @AfterEach
    public void cleanup() {
        ThreadLocalUtil.remove();
        QueryWrapper<Notification> wrapper = new QueryWrapper<>();
        wrapper.eq("receiver_id", TEST_USER_ID);
        notificationMapper.delete(wrapper);
    }

    @Test
    public void testListUnreadAndReadFlow() {
        notificationService.notify(TEST_USER_ID, "author-apply-approved", "标题1", "内容1");
        notificationService.notify(TEST_USER_ID, "author-apply-rejected", "标题2", "内容2");

        // 未读数
        Result<Integer> unread = notificationController.unreadCount();
        assertEquals(0, unread.getCode());
        assertEquals(2, unread.getData());

        // 列表
        Result<PageBean<Notification>> list = notificationController.list(1, 10);
        assertEquals(0, list.getCode());
        assertEquals(2, list.getData().getTotal());

        // 单条已读 → 未读数 -1
        int firstId = list.getData().getItems().get(0).getId();
        assertEquals(0, notificationController.read(firstId).getCode());
        assertEquals(1, notificationController.unreadCount().getData());

        // 全部已读
        Result<Integer> readAll = notificationController.readAll();
        assertEquals(0, readAll.getCode());
        assertEquals(1, readAll.getData(), "应影响 1 条未读");
        assertEquals(0, notificationController.unreadCount().getData());
    }

    @Test
    public void testUnauthenticatedIsRejected() {
        ThreadLocalUtil.remove();
        Result<PageBean<Notification>> list = notificationController.list(1, 10);
        assertEquals(1, list.getCode(), "未登录应返回失败码");
        assertEquals("未登录！", list.getMessage());
    }
}
