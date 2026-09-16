package com.articleTraceBack;

import com.articleTraceBack.Service.AvatarApplyService;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.AvatarApplyMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.AvatarApply;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 头像审核的并发验证。
 *
 * <p>核心断言：并发提交只落一条待审记录（靠 {@code uk_pending(user_id, pending_flag)} 兜底）；
 * 并发审批只有一个站长成功——否则「通过」与「拒绝」会互相覆盖，
 * 可能出现「已被拒绝、头像却换成了那张待审图」。</p>
 *
 * <p>{@link RustFsUtil} 被替换为 mock，用例不依赖真实的 RustFS。</p>
 *
 * <pre>mvn test -Dtest=AvatarApplyConcurrencyTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class AvatarApplyConcurrencyTest {

    private static final String OLD_PIC = "zz-test-old-logo.png";

    @Autowired
    private AvatarApplyService avatarApplyService;

    @Autowired
    private AvatarApplyMapper applyMapper;

    @Autowired
    private UserMapper userMapper;

    @MockitoBean
    private RustFsUtil rustFsUtil;

    private Integer testUserId;
    private String testUsername;

    @BeforeEach
    public void setUp() {
        testUsername = "avc" + (System.currentTimeMillis() % 100000000L);
        User user = new User();
        user.setUsername(testUsername);
        user.setNickname("头像并发");
        user.setPassword("x");
        user.setEmail(testUsername + "@example.invalid");
        user.setType(2);
        user.setUserPic(OLD_PIC);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        testUserId = user.getId();

        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.copyTo(anyString(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);
    }

    @AfterEach
    public void tearDown() {
        if (testUserId == null) {
            return;
        }
        QueryWrapper<AvatarApply> aw = new QueryWrapper<>();
        aw.eq("user_id", testUserId);
        applyMapper.delete(aw);
        userMapper.deleteById(testUserId);
    }

    private MultipartFile png() {
        return new MockMultipartFile("userLogo", "avatar.png", "image/png", new byte[]{1, 2, 3});
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
                    if (avatarApplyService.submit(testUserId, png())) {
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

        QueryWrapper<AvatarApply> aw = new QueryWrapper<>();
        aw.eq("user_id", testUserId).eq("status", AvatarApply.STATUS_PENDING);
        assertEquals(1L, applyMapper.selectCount(aw).longValue(), "并发提交后应只有一条待审记录");
    }

    @Test
    public void testConcurrentReviewOnlyOneWins() throws Exception {
        assertTrue(avatarApplyService.submit(testUserId, png()), "提交应成功");
        AvatarApply mine = avatarApplyService.findMine(testUserId);
        Integer applyId = mine.getId();

        int threads = 2;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            // 一个站长点「通过」、一个点「拒绝」，模拟同时操作
            final boolean pass = (i == 0);
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (avatarApplyService.review(applyId, pass, "并发场景", testUserId)) {
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
        assertTrue(doneGate.await(20, TimeUnit.SECONDS), "并发审批应在超时前完成");
        pool.shutdown();

        assertEquals(1, successCount.get(), "并发审批应恰好只有一个请求成功");

        AvatarApply after = applyMapper.selectById(applyId);
        assertTrue(after.getStatus() == AvatarApply.STATUS_APPROVED
                        || after.getStatus() == AvatarApply.STATUS_REJECTED,
                "最终状态应是通过或拒绝其中之一，实际=" + after.getStatus());

        // 关键：user_pic 必须与最终状态一致——失败的那次不能偷偷把头像换掉
        String finalPic = userMapper.selectById(testUserId).getUserPic();
        if (after.getStatus() == AvatarApply.STATUS_APPROVED) {
            assertEquals(mine.getPendingPic(), finalPic, "通过时头像应为待审对象");
        } else {
            assertNotEquals(mine.getPendingPic(), finalPic, "拒绝时头像不应变成待审对象");
            assertEquals(OLD_PIC, finalPic, "拒绝时头像应保持原样");
        }
    }
}
