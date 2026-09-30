package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.Support.ArticleSensitivityGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 违禁词与目标状态的判定规则：**草稿不扫**，只有送审 / 发布命中才转待审。
 *
 * 规则本身与 HTTP 无关（要用登录态、要走 multipart 的是调用方），住在
 * {@code Service/Support/ArticleSensitivityGuard}，这里只测规则。
 *
 * <pre>mvn test -Dtest=ArticleSensitivityGuardTest</pre>
 */
public class ArticleSensitivityGuardTest {

    @Test
    public void draftsAreNotScanned() {
        assertFalse(ArticleSensitivityGuard.shouldCheckSensitive(ArticleService.STATE_DRAFT), "草稿不扫");
        assertTrue(ArticleSensitivityGuard.shouldCheckSensitive(ArticleService.STATE_PENDING), "送审要扫");
        assertTrue(ArticleSensitivityGuard.shouldCheckSensitive(ArticleService.STATE_PUBLISHED), "发布要扫");
        assertFalse(ArticleSensitivityGuard.shouldCheckSensitive(null), "非法目标状态不扫（调用方会先拒）");
    }

    @Test
    public void hitsTurnIntoPendingExceptDrafts() {
        assertEquals(ArticleService.STATE_PENDING,
                ArticleSensitivityGuard.applySensitiveVerdict(ArticleService.STATE_PUBLISHED, true),
                "站长直接发布命中也要转待审");
        assertEquals(ArticleService.STATE_PENDING,
                ArticleSensitivityGuard.applySensitiveVerdict(ArticleService.STATE_PENDING, true),
                "送审命中仍是待审");
        assertEquals(ArticleService.STATE_DRAFT,
                ArticleSensitivityGuard.applySensitiveVerdict(ArticleService.STATE_DRAFT, true),
                "草稿即便判定命中也不改状态");
        assertEquals(ArticleService.STATE_PUBLISHED,
                ArticleSensitivityGuard.applySensitiveVerdict(ArticleService.STATE_PUBLISHED, false),
                "没命中就按原目标走");
        assertNull(ArticleSensitivityGuard.applySensitiveVerdict(null, true), "非法目标状态原样返回 null");
    }
}
