package com.articleTraceBack;

import com.articleTraceBack.Service.Support.SessionExpiryNotifier;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.Notification;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录到期提醒：6h / 1h 两档，每档每个 token 只提醒一次，1h 档优先。
 *
 * <pre>mvn test -Dtest=SessionExpiryNotifierTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class SessionExpiryNotifierTest {

    @Autowired
    private SessionExpiryNotifier notifier;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate redis;

    /** 造一个会话：Redis 里按用户名存 token，TTL 可控 */
    private String login(int userId, long ttlSeconds) {
        String username = userMapper.selectById(userId).getUsername();
        redis.opsForValue().set(username, "zz-test-token-" + System.nanoTime(), ttlSeconds, TimeUnit.SECONDS);
        return username;
    }

    private List<Notification> notes(int userId) {
        return notificationMapper.selectList(
                new QueryWrapper<Notification>().eq("receiver_id", userId).orderByAsc("id"));
    }

    private void cleanup(int userId, String username) {
        redis.delete(username);
        redis.keys("login:expire-notice:*").forEach(redis::delete);
        notificationMapper.delete(new QueryWrapper<Notification>().eq("receiver_id", userId));
    }

    @Test
    public void oneHourNoticeIsSentOnceAndTakesPriority() {
        int userId = fixtures.ensureUser(1);
        String username = login(userId, 59 * 60);
        try {
            long ttl = notifier.notifyIfNeeded(userId, username);
            assertTrue(ttl > 0 && ttl <= 60 * 60, "应返回剩余秒数");
            notifier.notifyIfNeeded(userId, username);   // 同一 token 同一档位不重复

            List<Notification> list = notes(userId);
            assertEquals(1, list.size(), "1 小时档只应提醒一次");
            assertTrue(list.get(0).getTitle().contains("1 小时"), "1 小时档优先，不应再发 6 小时档");
        } finally {
            cleanup(userId, username);
        }
    }

    @Test
    public void sixHourNoticeIsSentOnce() {
        int userId = fixtures.ensureUser(1);
        String username = login(userId, 5 * 3600);
        try {
            notifier.notifyIfNeeded(userId, username);
            notifier.notifyIfNeeded(userId, username);

            List<Notification> list = notes(userId);
            assertEquals(1, list.size(), "6 小时档只应提醒一次");
            assertTrue(list.get(0).getTitle().contains("6 小时"));
        } finally {
            cleanup(userId, username);
        }
    }

    @Test
    public void freshSessionIsNotWarned() {
        int userId = fixtures.ensureUser(1);
        String username = login(userId, 12 * 3600);
        try {
            long ttl = notifier.notifyIfNeeded(userId, username);
            assertTrue(ttl > 6 * 3600, "剩余充足时不提醒");
            assertEquals(0, notes(userId).size());
        } finally {
            cleanup(userId, username);
        }
    }
}
