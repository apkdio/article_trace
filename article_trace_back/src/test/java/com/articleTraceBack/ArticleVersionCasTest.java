package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Utils.ArticleConcurrentEditException;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.ArticleSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 版本号并发判据：保存与审核都基于「进入接口时读到的版本」，任何后到的写入都会落空。
 *
 * <pre>mvn test -Dtest=ArticleVersionCasTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ArticleVersionCasTest {

    private static final int AUTHOR = 1;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @MockitoBean
    private RustFsUtil rustFsUtil;

    /** 标题限 30 字符：取纳秒尾部 10 位足够唯一 */
    private String uniqueTitle() {
        String tag = String.valueOf(System.nanoTime());
        return "zz-test-ver-" + tag.substring(tag.length() - 10);
    }

    private int insertArticle(int userId, int categoryId, int state) {
        Article a = new Article();
        a.setTitle(uniqueTitle());
        a.setContent("zz-test-ver-content.json");
        a.setState(state);
        a.setCategoryId(categoryId);
        a.setCreateUser(userId);
        a.setCreateTime(LocalDateTime.now());
        a.setCoverImg("");
        articleMapper.insert(a);
        return a.getId();
    }

    private ArticleSnapshot snapshotOf(int id) {
        Article raw = articleMapper.selectById(id);
        return new ArticleSnapshot(raw.getState(), raw.getContent(), raw.getCoverImg(), raw.getVersion());
    }

    /** 模拟作者保存：自己读一份最新快照 */
    private boolean save(int id, int userId, int state, String body) {
        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle(uniqueTitle());
        edit.setContent(body);
        edit.setState(state);
        return articleService.articleAddOrUpdate(edit, 1, null, "zz-test-ver", snapshotOf(id));
    }

    private Article editWithStaleSnapshot(int id, int userId, int state, String body) {
        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle(uniqueTitle());
        edit.setContent(body);
        edit.setState(state);
        return edit;
    }

    /** C-1：作者在待审期间改了正文，站长用审阅时那一版点通过必须落空 */
    @Test
    public void approveFailsWhenAuthorEditedPendingArticle() {
        int userId = fixtures.ensureUser(AUTHOR);
        int categoryId = fixtures.ensureCategory(userId);
        int id = insertArticle(userId, categoryId, ArticleService.STATE_PENDING);
        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        ArticleSnapshot reviewer = snapshotOf(id);
        assertTrue(save(id, userId, ArticleService.STATE_PENDING, "作者改后的正文"), "作者保存应成功");

        assertFalse(articleService.updateState(id, ArticleService.STATE_PUBLISHED, null, reviewer),
                "正文已换、版本已变，审核必须落空");
        Article after = articleMapper.selectById(id);
        assertEquals(ArticleService.STATE_PENDING, after.getState(), "落空的审核不能改变状态");
        assertTrue(after.getVersion() > reviewer.version(), "每次写入都应推高版本");
    }

    /** C-3：B 先完成、A 后到达——A 的旧快照必须落空，而不是覆盖 B */
    @Test
    public void saveFailsWhenAnotherWriterCompletedFirst() {
        int userId = fixtures.ensureUser(AUTHOR);
        int categoryId = fixtures.ensureCategory(userId);
        int id = insertArticle(userId, categoryId, ArticleService.STATE_DRAFT);
        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        ArticleSnapshot aSnapshot = snapshotOf(id);          // A 进入接口时的读数
        assertTrue(save(id, userId, ArticleService.STATE_DRAFT, "B 的正文"), "B 先完成");
        Article afterB = articleMapper.selectById(id);

        Article aEdit = editWithStaleSnapshot(id, userId, ArticleService.STATE_DRAFT, "A 的正文");
        aEdit.setVersion(999L);                              // 请求体里的版本不得参与写入
        assertThrows(ArticleConcurrentEditException.class,
                () -> articleService.articleAddOrUpdate(aEdit, 1, null, "zz-test-ver", aSnapshot),
                "基于旧快照的保存必须落空");

        Article afterA = articleMapper.selectById(id);
        assertEquals(afterB.getContent(), afterA.getContent(), "A 不能覆盖 B 的正文");
        assertEquals(afterB.getVersion(), afterA.getVersion(), "落空的写入不推高版本");
    }

    /** ABA：2→3→2 之后，用最初那一版审核仍然必须落空 */
    @Test
    public void oldVersionCannotBeReusedAfterStateReturns() {
        int userId = fixtures.ensureUser(AUTHOR);
        int categoryId = fixtures.ensureCategory(userId);
        int id = insertArticle(userId, categoryId, ArticleService.STATE_PENDING);
        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        ArticleSnapshot initial = snapshotOf(id);
        assertTrue(articleService.updateState(id, ArticleService.STATE_REJECTED, "先驳回", initial));
        assertTrue(save(id, userId, ArticleService.STATE_PENDING, "改完重投"), "3→2 重新送审");
        assertEquals(ArticleService.STATE_PENDING, articleMapper.selectById(id).getState());

        assertFalse(articleService.updateState(id, ArticleService.STATE_PUBLISHED, null, initial),
                "状态虽然回到了待审，但版本已经变了，旧审核结果不能复用");
        assertEquals(ArticleService.STATE_PENDING, articleMapper.selectById(id).getState());
    }

    /** 新增从版本 0 开始，客户端传什么版本都不生效 */
    @Test
    public void insertStartsAtZeroAndIgnoresClientVersion() {
        int userId = fixtures.ensureUser(AUTHOR);
        int categoryId = fixtures.ensureCategory(userId);
        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);

        Article article = new Article();
        article.setTitle(uniqueTitle());
        article.setContent("新增正文");
        article.setState(ArticleService.STATE_DRAFT);
        article.setCategoryId(categoryId);
        article.setCreateUser(userId);
        article.setCreateTime(LocalDateTime.now());
        article.setVersion(999L);

        assertTrue(articleService.articleAddOrUpdate(article, 0, null, "zz-test-ver", null));
        assertEquals(0L, articleMapper.selectById(article.getId()).getVersion(), "新增必须从 0 开始");
    }
}
