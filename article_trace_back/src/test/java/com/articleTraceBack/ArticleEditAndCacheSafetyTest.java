package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.constant.RedisKeys;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 两处缺陷的回归测试。
 *
 * <p><b>① 文章编辑不得丢正文</b>：原实现在写库之前就删掉了旧正文文件。
 * 一旦写库那一步失败（DB 抖动、超时，或并发下撞唯一索引），DB 会指向一个已被删除的对象，
 * 该文章的正文就静默变空——不报错，只有用户自己再编辑一次才会恢复。
 * 现在改为先写库、成功后才删旧文件，写库失败则回收刚上传的新文件。</p>
 *
 * <p>注意此处直接调用 Service：Controller 在串行的重名场景下会提前 return，
 * 根本走不到这里，所以只有并发（或绕过 Controller）才可能触发索引冲突。
 * 本用例正是要覆盖那条路径。</p>
 *
 * <p><b>② 热门文章缓存损坏不得让接口 500</b>：原实现把缓存当逗号拼接字符串解析，
 * 空串与含逗号的标题都会导致异常；现在用 JSON 存储并对损坏内容回退查库。</p>
 *
 * <p>对象存储用 Mock：本用例要验证的是「删旧文件与写库的先后顺序」，
 * Mock 能直接断言"撞名时压根没调用过 delete"，比连真实存储更确定。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ArticleEditAndCacheSafetyTest {

    private static final int AUTHOR = 1;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    @Qualifier("stringRedisTemplateArticle")
    private StringRedisTemplate redis;

    @MockitoBean
    private RustFsUtil rustFsUtil;

    /** 造一篇直接指定正文对象名的文章（绕过上传，便于断言） */
    private int insertArticle(int userId, String title, String contentKey) {
        Article a = new Article();
        a.setTitle(title);
        a.setContent(contentKey);
        a.setState(ArticleService.STATE_DRAFT);
        a.setCreateUser(userId);
        a.setCreateTime(LocalDateTime.now());
        a.setCoverImg("");
        articleMapper.insert(a);
        return a.getId();
    }

    /**
     * 写库因唯一索引冲突失败时，旧正文必须完好。
     *
     * <p>Controller 在串行重名时会提前拦截，所以这条路径只在并发下出现——
     * 这里直接调 Service 来复现它。</p>
     */
    @Test
    public void editKeepsOldContentWhenTitleConflicts() {
        int userId = fixtures.ensureUser(AUTHOR);
        String oldKey = "zz-test-old-" + System.nanoTime() + ".json";
        int id = insertArticle(userId, "zz-test-标题A-" + System.nanoTime(), oldKey);
        insertArticle(userId, "zz-test-标题B-" + System.nanoTime(), "zz-test-other.json");
        // 取库里那篇「标题B」的标题，制造唯一索引冲突
        String conflictTitle = articleMapper.selectObjs(
                        new QueryWrapper<Article>().select("title")
                                .eq("create_user", userId).ne("id", id).last("limit 1"))
                .stream().findFirst().map(Object::toString).orElseThrow();

        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle(conflictTitle);
        edit.setContent("被编辑后的正文内容");
        edit.setState(ArticleService.STATE_DRAFT);

        assertThrows(Exception.class, () -> articleService.articleAddOrUpdate(edit, 1, null, "zz-test-edit"),
                "标题撞唯一索引应当抛异常（由上层转友好提示）");

        // 核心断言：旧正文对象从未被删除
        verify(rustFsUtil, never()).delete(eq(oldKey), eq("json"));
        // DB 仍指向旧对象，正文未丢
        assertEquals(oldKey, articleMapper.selectById(id).getContent(),
                "写库失败时 DB 必须仍指向旧的正文对象");
    }

    @Test
    public void editDeletesOldContentOnlyAfterSuccessfulSave() {
        int userId = fixtures.ensureUser(AUTHOR);
        String oldKey = "zz-test-old2-" + System.nanoTime() + ".json";
        int id = insertArticle(userId, "zz-test-正常编辑-" + System.nanoTime(), oldKey);

        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle("zz-test-改后标题-" + System.nanoTime());
        edit.setContent("新正文");
        edit.setState(ArticleService.STATE_DRAFT);

        assertTrue(articleService.articleAddOrUpdate(edit, 1, null, "zz-test-edit"), "正常编辑应当成功");

        // 写库成功后旧对象才被清理
        verify(rustFsUtil).delete(eq(oldKey), eq("json"));
        Article after = articleMapper.selectById(id);
        assertNotNull(after.getContent());
        assertTrue(after.getContent().endsWith(".json"), "content 应指向新的对象名");
    }

    @Test
    public void hotArticlesSurvivesDamagedCache() {
        String cacheKey = RedisKeys.ARTICLE_HOT_TOP10;

        // 空串：旧实现写空库时留下 ""，之后 parseInt("") 直接抛异常
        redis.opsForValue().set(cacheKey, "");
        assertDoesNotThrow(() -> articleService.getViewsTop10(),
                "缓存为空串时应回退查库，而不是抛异常");

        // 完全损坏的内容同样不应让接口崩
        redis.opsForValue().set(cacheKey, "这不是JSON{{");
        assertDoesNotThrow(() -> articleService.getViewsTop10(),
                "缓存内容损坏时应回退查库，而不是抛异常");

        // 正常 JSON 空数组：命中缓存返回空列表
        redis.opsForValue().set(cacheKey, "[]");
        List<Article> hot = articleService.getViewsTop10();
        assertNotNull(hot);
        assertEquals(0, hot.size(), "缓存为空列表时应返回空列表");

        redis.delete(cacheKey);
    }

    @Test
    public void hotArticlesHandlesTitleWithComma() {
        // 标题允许含逗号（Article.title 的正则不排除逗号），
        // 旧的逗号拼接格式会因此错位；JSON 不受影响
        String cacheKey = RedisKeys.ARTICLE_HOT_TOP10;
        redis.opsForValue().set(cacheKey,
                "[{\"id\":1,\"title\":\"标题,含逗号\",\"views\":42}]");

        List<Article> hot = assertDoesNotThrow(() -> articleService.getViewsTop10());
        assertNotNull(hot);
        assertEquals(1, hot.size());
        assertEquals("标题,含逗号", hot.get(0).getTitle());

        redis.delete(cacheKey);
    }
}
