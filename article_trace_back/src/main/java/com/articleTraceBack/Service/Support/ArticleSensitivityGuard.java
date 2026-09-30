package com.articleTraceBack.Service.Support;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Utils.AhoCorasickUtil;
import com.articleTraceBack.pojo.Article;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 违禁词与目标状态的判定规则：**草稿不扫**，只有送审 / 发布命中才转待审，命中后按结果打标。
 *
 * 这三件事原先散在 {@code ArticleController} 上（判断要用登录态、写入要走 multipart），但它们本身与 HTTP 无关，
 * 放在 controller 里既让那一层不干净、又得靠「把方法声明成包可见」才测得动，所以挪到 service 层下的 Support。
 */
public final class ArticleSensitivityGuard {

    private ArticleSensitivityGuard() {
    }

    /** 草稿不进敏感词监测：草稿是还没打算交出去的私有产物，扫了也不进审核队列，只会让作者改稿时被标记牵住 */
    public static boolean shouldCheckSensitive(Integer target) {
        return target != null && target != ArticleService.STATE_DRAFT;
    }

    /** 命中违禁词后的最终状态：草稿不动，其余（送审 / 站长直接发布）一律转待审 */
    public static Integer applySensitiveVerdict(Integer target, boolean sensitiveHit) {
        if (target == null || !sensitiveHit || target == ArticleService.STATE_DRAFT) {
            return target;
        }
        return ArticleService.STATE_PENDING;
    }

    /** 落「命中违禁词」标记；每次写入都重算，命中词改掉后标记回0 */
    public static void applyMark(Article article, List<AhoCorasickUtil.Match> matches) {
        if (matches.isEmpty()) {
            article.setSensitiveHit(0);
            article.setSensitiveWords(null);
            return;
        }
        article.setSensitiveHit(1);
        article.setSensitiveWords(joinKeywords(matches));
    }

    /** 命中的词去重后拼成顿号分隔的一行，超长截断 */
    private static String joinKeywords(List<AhoCorasickUtil.Match> matches) {
        Set<String> keywords = new LinkedHashSet<>();
        for (AhoCorasickUtil.Match match : matches) {
            keywords.add(match.keyword);
        }
        StringBuilder sb = new StringBuilder();
        for (String keyword : keywords) {
            if (sb.length() + keyword.length() > 250) {
                sb.append('…');
                break;
            }
            if (!sb.isEmpty()) {
                sb.append('、');
            }
            sb.append(keyword);
        }
        return sb.toString();
    }
}
