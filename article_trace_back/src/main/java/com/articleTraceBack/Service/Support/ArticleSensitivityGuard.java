package com.articleTraceBack.Service.Support;

import com.articleTraceBack.Service.ArticleService;

/**
 * 违禁词与目标状态的判定规则：**草稿不扫**，只有送审 / 发布命中才转待审。
 *
 * 规则原先挂在 {@code ArticleController} 上（判断要用登录态、写入要走 multipart），但它本身与 HTTP 无关，
 * 放在controller 里既让那一层不干净、又得靠「把方法声明成包可见」才测得动，所以挪到 service 层下的 Support。
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
}
