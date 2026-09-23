package com.articleTraceBack;

import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.Utils.BcryptUtils;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 两人同时改密：改密走条件更新（`WHERE password = 旧哈希`），只有一个能生效，
 * 后写者不会静默覆盖先写者的新密码。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class PasswordChangeCasTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TestFixtures fixtures;

    @Test
    public void testConcurrentChangeOnlyOneWins() throws Exception {
        // 用夹具建用户（统一 zz-test- 前缀），密码另外置成已知哈希，才能验证旧密码的失效
        String oldPass = "old-pass-123";
        int userId = fixtures.ensureUser(2);
        String name = userMapper.selectById(userId).getUsername();
        userMapper.update(null, new UpdateWrapper<User>().eq("id", userId)
                .set("password", BcryptUtils.encodePass(oldPass)));

        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            String newPass = "new-pass-" + i;
            pool.execute(() -> {
                try {
                    start.await();
                    if (userService.updatePass(name, newPass, oldPass)) {
                        ok.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发改密未在 10 秒内结束");
        pool.shutdown();

        assertEquals(1, ok.get(), "同时改密只应有一个成功");
        assertFalse(userService.updatePass(name, "another-pass", oldPass),
                "赢家已换掉密码，拿旧密码再改必须失败");
    }
}
