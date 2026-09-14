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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作者申请审批的并发验证。
 *
 * <p>核心断言：两个站长同时审批同一条申请时，**只有一个**能成功；
 * 申请人只会收到一条结果通知（而不是「已通过」「未通过」各一条）。</p>
 *
 * <pre>mvn test -Dtest=AuthorApplyConcurrencyTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class AuthorApplyConcurrencyTest {

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
        testUsername = "ac" + (System.currentTimeMillis() % 100000000L);
        User user = new User();
        user.setUsername(testUsername);
        user.setNickname("并发测试");
        user.setPassword("x");
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

        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", testUserId);
        notificationMapper.delete(nw);

        QueryWrapper<Notification> masterNw = new QueryWrapper<>();
        masterNw.like("content", testUsername);
        notificationMapper.delete(masterNw);

        userMapper.deleteById(testUserId);
    }

    @Test
    public void testConcurrentReviewOnlyOneWins() throws Exception {
        assertTrue(applyService.submit(testUserId, "并发测试申请"), "提交申请应成功");
        AuthorApply mine = applyService.findMine(testUserId);
        Integer applyId = mine.getId();

        int threads = 2;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            // 一个点「通过」、一个点「拒绝」，模拟两个站长同时操作
            final boolean pass = (i == 0);
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (applyService.review(applyId, pass, "并发场景", testUserId)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown(); // 同时起跑
        assertTrue(doneGate.await(20, TimeUnit.SECONDS), "并发审批应在超时前完成");
        pool.shutdown();

        assertEquals(1, successCount.get(), "并发审批应恰好只有一个请求成功");

        // 状态只被写入一次，且是二者之一
        AuthorApply after = applyMapper.selectById(applyId);
        assertTrue(after.getStatus() == AuthorApply.STATUS_APPROVED
                        || after.getStatus() == AuthorApply.STATUS_REJECTED,
                "最终状态应是通过或拒绝其中之一，实际=" + after.getStatus());

        // 关键：申请人只收到一条结果通知，而不是两条互相矛盾的
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", testUserId)
                .in("title", List.of("作者申请已通过", "作者申请未通过"));
        Long notifyCount = notificationMapper.selectCount(nw);
        assertEquals(1L, notifyCount.longValue(), "并发审批只应产生一条结果通知，实际=" + notifyCount);
    }

    @Test
    public void testConcurrentSubmitOnlyOnePendingRow() throws Exception {
        int threads = 4;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (applyService.submit(testUserId, "并发提交")) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(20, TimeUnit.SECONDS), "并发提交应在超时前完成");
        pool.shutdown();

        assertEquals(1, successCount.get(), "并发提交应恰好只有一个成功");

        QueryWrapper<AuthorApply> aw = new QueryWrapper<>();
        aw.eq("user_id", testUserId).eq("status", AuthorApply.STATUS_PENDING);
        assertEquals(1L, applyMapper.selectCount(aw).longValue(), "并发提交后应只有一条待审记录");
    }
}
