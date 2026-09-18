package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章状态机验证。
 *
 * <p>状态：0 草稿 / 1 已发布 / 2 待审核 / 3 已驳回；角色：0 站长 / 1 作者 / 2 读者。</p>
 *
 * <pre>mvn test -Dtest=ArticleStateMachineTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=false")
public class ArticleStateMachineTest {

    private static final int AUTHOR = 1;
    private static final int MASTER = 0;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @Test
    public void testTransferTable() {
        // ---- 合法 ----
        assertTrue(articleService.canTransfer(0, 0, AUTHOR), "编辑草稿");
        assertTrue(articleService.canTransfer(0, 2, AUTHOR), "草稿送审");
        assertTrue(articleService.canTransfer(1, 2, AUTHOR), "已发布改后重新送审");
        assertTrue(articleService.canTransfer(3, 2, AUTHOR), "已驳回改后重投");
        assertTrue(articleService.canTransfer(1, 0, AUTHOR), "已发布下架为草稿");
        assertTrue(articleService.canTransfer(3, 0, AUTHOR), "已驳回放弃转草稿");
        assertTrue(articleService.canTransfer(2, 0, AUTHOR), "待审核撤回为草稿");
        assertTrue(articleService.canTransfer(2, 1, MASTER), "站长审核通过");
        assertTrue(articleService.canTransfer(2, 3, MASTER), "站长审核驳回");
        assertTrue(articleService.canTransfer(1, 3, MASTER), "站长追回已误审发布的文章");
        assertTrue(articleService.canTransfer(0, 1, MASTER), "站长直接发布自己的草稿");
        assertTrue(articleService.canTransfer(1, 1, MASTER), "站长改完已发布文章后仍保持发布");

        // ---- 非法 ----
        assertFalse(articleService.canTransfer(0, 1, AUTHOR), "作者不得绕过审核直接发布");
        assertFalse(articleService.canTransfer(3, 1, AUTHOR), "作者不得直接把驳回稿发布");
        assertFalse(articleService.canTransfer(1, 1, AUTHOR), "作者的已发布文章要走重新送审，不能原地保持发布");
        assertFalse(articleService.canTransfer(0, 3, MASTER), "站长不能驳回一篇草稿");
        assertFalse(articleService.canTransfer(3, 3, MASTER), "不能重复驳回");
        assertFalse(articleService.canTransfer(0, 99, MASTER), "非法目标状态");
        assertFalse(articleService.canTransfer(0, -1, MASTER), "非法目标状态");
    }

    @Test
    public void testUpdateStateRespectsStateMachine() {
        Integer id = insertArticle(ArticleService.STATE_PENDING);
        try {
            assertFalse(articleService.updateState(id, 99), "非法目标状态应被拒绝");
            assertFalse(articleService.updateState(id, 0), "站长不能把待审文章改成草稿");

            assertTrue(articleService.updateState(id, ArticleService.STATE_PUBLISHED), "2→1 审核通过");
            assertTrue(articleService.updateState(id, ArticleService.STATE_REJECTED), "1→3 追回已发布");

            assertFalse(articleService.updateState(id, ArticleService.STATE_PUBLISHED), "3→1 应被拒绝");
            assertFalse(articleService.updateState(999999, ArticleService.STATE_PUBLISHED),
                    "文章不存在应返回 false");
        } finally {
            articleMapper.deleteById(id);
        }
    }

    /**
     * 造一篇指定状态的文章。
     *
     * <p>作者由 fixture 现场创建——早先从库里「捞一条已有的 create_user」来绕过外键，
     * 这会让用例依赖开发库里的残留数据，在干净的测试库上必然失败。</p>
     */
    private Integer insertArticle(int state) {
        Article a = new Article();
        a.setTitle("状态机测试-" + System.currentTimeMillis());
        a.setContent("state-machine-test");
        a.setState(state);
        a.setCreateUser(fixtures.ensureUser(AUTHOR));
        a.setCreateTime(LocalDateTime.now());
        a.setCoverImg("");
        articleMapper.insert(a);
        return a.getId();
    }
}
