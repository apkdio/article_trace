package com.articleTraceBack.Service.Support;

import com.articleTraceBack.Service.ArticleService;

/**
 * 作者/站长的发布意图与角色 → 目标状态。请求体里的 {@code state} 只当意图用，不当结果。
 *
 * 原先住在 {@code ArticleController} 里（判断要用登录态、写入要走 multipart），但这就是条业务规则：
 * 作者提交一律落待审，只有站长能直接发布。挪出来之后 Controller 只负责取角色、传参、把非法值变成报错。
 */
public final class ArticlePublishPolicy {

    private ArticlePublishPolicy() {
    }

    /**
     * @param requested 请求体里的 state，只当意图：0 = 存草稿，1/2/3 = 提交
     * @return 目标状态；取值不在 {0,1,2,3} 内时返回 {@code null}（由调用方拒绝）
     */
    public static Integer resolveTargetState(Integer requested, int roleType) {
        if (requested == null) {
            return null;
        }
        if (requested == ArticleService.STATE_DRAFT) {
            return ArticleService.STATE_DRAFT;
        }
        // 只有合法的「非草稿」状态值才算提交意图，其余取值一律视为非法入参
        if (requested != ArticleService.STATE_PUBLISHED
                && requested != ArticleService.STATE_PENDING
                && requested != ArticleService.STATE_REJECTED) {
            return null;
        }
        return (roleType == ArticleService.ROLE_MASTER)
                ? ArticleService.STATE_PUBLISHED
                : ArticleService.STATE_PENDING;
    }
}
