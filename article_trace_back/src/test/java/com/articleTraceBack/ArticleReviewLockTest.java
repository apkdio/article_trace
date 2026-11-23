package com.articleTraceBack;

import com.articleTraceBack.Service.Support.ArticleReviewLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 单篇审核占用：同一时间只让一个站长审，同一人可续期，非持有者释放无效。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class ArticleReviewLockTest {

    @Autowired
    private ArticleReviewLock lock;

    @Test
    public void onlyOneMasterCanHold() {
        int articleId = 900_000 + (int) (System.nanoTime() % 100_000);
        try {
            assertTrue(lock.tryLock(articleId, 1, "master-a"), "第一个站长应拿到占用");
            assertFalse(lock.tryLock(articleId, 2, "master-b"), "第二个站长此时不能进入");
            assertTrue(lock.tryLock(articleId, 1, "master-a"), "同一人重复进入视为续期");

            lock.release(articleId, 2);
            assertFalse(lock.tryLock(articleId, 2, "master-b"), "非持有者释放不应生效");

            lock.release(articleId, 1);
            assertTrue(lock.tryLock(articleId, 2, "master-b"), "释放后其他人可以接手");
        } finally {
            lock.release(articleId, 1);
            lock.release(articleId, 2);
        }
    }
}
