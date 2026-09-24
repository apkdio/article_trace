package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.UserService;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 首页「我的发布」的计数口径：只算已发布（state=1），草稿、待审、已驳回都不算。
 *
 * <pre>mvn test -Dtest=UserPublishedCountTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class UserPublishedCountTest {

    private static final int AUTHOR = 1;
    private static final int MASTER = 0;

    @Autowired
    private UserService userService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @Test
    public void testCountsOnlyPublished() {
        int uid = fixtures.ensureUser(AUTHOR);
        Integer[] created = {
                insert(uid, ArticleService.STATE_DRAFT),
                insert(uid, ArticleService.STATE_PUBLISHED),
                insert(uid, ArticleService.STATE_PUBLISHED),
                insert(uid, ArticleService.STATE_PENDING),
                insert(uid, ArticleService.STATE_REJECTED)
        };
        try {
            assertEquals(2, userService.countPublishedArticles(uid),
                    "五篇里只有两篇已发布，草稿 / 待审 / 已驳回都不该计入");
        } finally {
            for (Integer id : created) {
                articleMapper.deleteById(id);
            }
        }
    }

    @Test
    public void testZeroWithoutPublished() {
        int uid = fixtures.ensureUser(MASTER);
        assertEquals(0, userService.countPublishedArticles(uid), "没有任何文章的账号应为 0");
    }

    private Integer insert(int uid, int state) {
        Article a = new Article();
        a.setTitle("发布计数测试-" + TestFixtures.unique() + "-" + state);
        a.setContent("published-count-test");
        a.setState(state);
        a.setCreateUser(uid);
        a.setCreateTime(LocalDateTime.now());
        a.setCoverImg("");
        articleMapper.insert(a);
        return a.getId();
    }
}
