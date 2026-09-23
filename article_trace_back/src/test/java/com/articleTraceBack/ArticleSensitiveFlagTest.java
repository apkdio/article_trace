package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 审核通过（2→1）后应当清掉「命中违禁词」标记，但保留命中的词供排查。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class ArticleSensitiveFlagTest {

    private static final int STATE_PUBLISHED = 1;
    private static final int STATE_PENDING = 2;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @Test
    public void approvingClearsHitMarkAndKeepsHitWords() {
        int userId = fixtures.ensureUser(1);
        Article article = new Article();
        article.setTitle("zz-test-命中稿-" + System.nanoTime());
        article.setContent("zz-test-content.json");
        article.setState(STATE_PENDING);
        article.setSensitiveHit(1);
        article.setSensitiveWords("zztest违禁词甲");
        article.setCreateUser(userId);
        article.setCreateTime(LocalDateTime.now());
        articleMapper.insert(article);

        assertTrue(articleService.updateState(article.getId(), STATE_PUBLISHED), "2→1 应当成功");
        Article after = articleMapper.selectById(article.getId());
        assertEquals(STATE_PUBLISHED, after.getState());
        assertEquals(0, after.getSensitiveHit(), "审核通过后不该再挂命中标记");
        assertEquals("zztest违禁词甲", after.getSensitiveWords(), "命中的词要留着作排查依据");
    }
}
