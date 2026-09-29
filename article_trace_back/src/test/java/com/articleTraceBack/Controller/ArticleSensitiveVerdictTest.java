package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.ArticleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 违禁词与目标状态的判定规则：**草稿不扫**，只有送审 / 发布命中才转待审。
 *
 * 这两条规则住在 Controller 里（判断要用登录态、写入要走 multipart），整条链路跑起来还依赖 RustFS，
 * 所以把规则抽成两个纯函数在这里单独测。
 *
 * <pre>mvn test -Dtest=ArticleSensitiveVerdictTest</pre>
 */
public class ArticleSensitiveVerdictTest {

    @Test
    public void draftsAreNotScanned() {
        assertFalse(ArticleController.shouldCheckSensitive(ArticleService.STATE_DRAFT), "草稿不扫");
        assertTrue(ArticleController.shouldCheckSensitive(ArticleService.STATE_PENDING), "送审要扫");
        assertTrue(ArticleController.shouldCheckSensitive(ArticleService.STATE_PUBLISHED), "发布要扫");
        assertFalse(ArticleController.shouldCheckSensitive(null), "非法目标状态不扫（调用方会先拒）");
    }

    @Test
    public void hitsTurnIntoPendingExceptDrafts() {
        assertEquals(ArticleService.STATE_PENDING,
                ArticleController.applySensitiveVerdict(ArticleService.STATE_PUBLISHED, true),
                "站长直接发布命中也要转待审");
        assertEquals(ArticleService.STATE_PENDING,
                ArticleController.applySensitiveVerdict(ArticleService.STATE_PENDING, true),
                "送审命中仍是待审");
        assertEquals(ArticleService.STATE_DRAFT,
                ArticleController.applySensitiveVerdict(ArticleService.STATE_DRAFT, true),
                "草稿即便判定命中也不改状态");
        assertEquals(ArticleService.STATE_PUBLISHED,
                ArticleController.applySensitiveVerdict(ArticleService.STATE_PUBLISHED, false),
                "没命中就按原目标走");
        assertNull(ArticleController.applySensitiveVerdict(null, true), "非法目标状态原样返回 null");
    }
}
