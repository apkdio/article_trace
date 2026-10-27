package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.PageBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 文章列表按发布时间排序：1 升序 / -1 降序，null 保持默认顺序。
 *
 * <pre>mvn test -Dtest=ArticleListSortTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ArticleListSortTest {

    private static final int AUTHOR = 1;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @MockitoBean
    private RustFsUtil rustFsUtil;

    private int insertArticle(int userId, String title, LocalDateTime createTime) {
        Article a = new Article();
        a.setTitle(title);
        a.setContent("zz-test-sort-content.json");
        a.setState(ArticleService.STATE_DRAFT);
        a.setCreateUser(userId);
        a.setCreateTime(createTime);
        a.setCoverImg("");
        articleMapper.insert(a);
        return a.getId();
    }

    private List<Article> page(int userId, Integer timeOrder) {
        return articleService.findAllArticlesWithPage(userId, 1, 10, null, null, null, timeOrder).getItems();
    }

    private String title(String tag) {
        String t = String.valueOf(System.nanoTime());
        return "sort-" + tag + "-" + t.substring(t.length() - 8);
    }

    @Test
    public void authorListSortsByCreateTime() {
        int uid = fixtures.ensureUser(AUTHOR);
        LocalDateTime base = LocalDateTime.now().withNano(0);
        int oldId = insertArticle(uid, title("old"), base.minusHours(2));
        int midId = insertArticle(uid, title("mid"), base.minusHours(1));
        int newId = insertArticle(uid, title("new"), base);
        given(rustFsUtil.getContent(anyString())).willReturn("");

        try {
            List<Article> asc = page(uid, 1);
            assertEquals(List.of(oldId, midId, newId), asc.stream().map(Article::getId).toList(),
                    "timeOrder=1 应按发布时间升序");

            List<Article> desc = page(uid, -1);
            assertEquals(List.of(newId, midId, oldId), desc.stream().map(Article::getId).toList(),
                    "timeOrder=-1 应按发布时间降序");

            List<Article> fallback = page(uid, null);
            assertEquals(newId, fallback.get(0).getId(), "默认排序仍是「最后改动」倒序，新建的排最前");
        } finally {
            articleMapper.deleteBatchIds(List.of(oldId, midId, newId));
        }
    }

    @Test
    public void masterListSortsByCreateTime() {
        int userId = fixtures.ensureUser(AUTHOR);
        LocalDateTime base = LocalDateTime.now().withNano(0);
        int oldId = insertArticle(userId, title("m-old"), base.minusHours(2));
        int newId = insertArticle(userId, title("m-new"), base);
        given(rustFsUtil.getContent(anyString())).willReturn("");

        try {
            PageBean<Article> asc = articleService.findAllArticlesInMaster(
                    1, 10, null, 0, userId, null, null, null, 1);
            List<Integer> ids = asc.getItems().stream().map(Article::getId).toList();
            assertTrue(ids.indexOf(oldId) < ids.indexOf(newId),
                    "站长列表 timeOrder=1 时应先出旧文章，实际 ids=" + ids + ", old=" + oldId + ", new=" + newId);
        } finally {
            articleMapper.deleteBatchIds(List.of(oldId, newId));
        }
    }
}
