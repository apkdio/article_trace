package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Service.Support.ArticlePublishPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 「草稿 / 提交」意图 + 角色 → 目标状态：请求体里的 state 只当意图，不当结果。
 *
 * <pre>mvn test -Dtest=ArticlePublishPolicyTest</pre>
 */
public class ArticlePublishPolicyTest {

    /** 站长 */
    private static final int MASTER = ArticleService.ROLE_MASTER;
    /** 作者（非站长） */
    private static final int WRITER = 1;

    @Test
    public void draftIntentStaysDraftForEveryone() {
        assertEquals(ArticleService.STATE_DRAFT,
                ArticlePublishPolicy.resolveTargetState(ArticleService.STATE_DRAFT, MASTER));
        assertEquals(ArticleService.STATE_DRAFT,
                ArticlePublishPolicy.resolveTargetState(ArticleService.STATE_DRAFT, WRITER));
    }

    @Test
    public void submitIntentLandsPendingExceptMaster() {
        assertEquals(ArticleService.STATE_PUBLISHED,
                ArticlePublishPolicy.resolveTargetState(ArticleService.STATE_PUBLISHED, MASTER), "站长提交即发布");
        assertEquals(ArticleService.STATE_PENDING,
                ArticlePublishPolicy.resolveTargetState(ArticleService.STATE_PUBLISHED, WRITER), "作者提交只能送审");
        assertEquals(ArticleService.STATE_PENDING,
                ArticlePublishPolicy.resolveTargetState(ArticleService.STATE_PENDING, WRITER));
        assertEquals(ArticleService.STATE_PENDING,
                ArticlePublishPolicy.resolveTargetState(ArticleService.STATE_REJECTED, WRITER), "驳回稿重投也是送审");
    }

    @Test
    public void illegalValuesAreRejected() {
        assertNull(ArticlePublishPolicy.resolveTargetState(null, WRITER), "不传 state");
        assertNull(ArticlePublishPolicy.resolveTargetState(9, WRITER), "取值不在 {0,1,2,3}");
        assertNull(ArticlePublishPolicy.resolveTargetState(-1, MASTER), "负数同样非法");
    }
}
