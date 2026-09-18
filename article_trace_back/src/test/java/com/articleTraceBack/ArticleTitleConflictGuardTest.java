package com.articleTraceBack;

import com.articleTraceBack.Controller.ArticleController;
import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

/**
 * 串行重名在 Controller 层就被拦截。
 *
 * <p>改动标题撞上同作者的另一篇文章时，{@code ArticleController.update} 的查重会直接返回
 * 「你已经写过同名文章了！」，**不会进入 {@code articleAddOrUpdate}**——
 * 因此正文文件完全不会被触碰。这条路径之所以要测，是因为"先查后写"的重名保护一旦被挪动或删掉，
 * 后果就是文章正文静默变空（见 {@code ArticleEditAndCacheSafetyTest} 覆盖的那条并发路径）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ArticleTitleConflictGuardTest {

    private static final int AUTHOR = 1;

    @Autowired
    private ArticleController articleController;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @MockitoBean
    private RustFsUtil rustFsUtil;

    @AfterEach
    public void clearThreadLocal() {
        ThreadLocalUtil.remove();
    }

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

    private void loginAs(int uid, int roleType) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", uid);
        userInfo.put("type", roleType);
        userInfo.put("name", "zz-test-user-" + uid);
        ThreadLocalUtil.set(userInfo);
    }

    @Test
    public void serialTitleConflictRejectedBeforeTouchingStorage() {
        int userId = fixtures.ensureUser(AUTHOR);
        String oldKey = "zz-test-guard-" + System.nanoTime() + ".json";
        int target = insertArticle(userId, "zz-test-目标文章-" + System.nanoTime(), oldKey);
        String occupied = "zz-test-已占用标题-" + System.nanoTime();
        insertArticle(userId, occupied, "zz-test-other.json");

        // 给一个真实分类：Controller 在校验开头就做了 int categoryId = article.getCategoryId()，
        // 传 null 会先触发拆箱 NPE，测不到我们关心的重名分支
        int categoryId = fixtures.ensureCategory(userId);

        loginAs(userId, AUTHOR);
        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        Article edit = new Article();
        edit.setTitle(occupied);                 // 撞上同作者的另一篇
        edit.setContent("新的正文内容");
        edit.setCategoryId(categoryId);
        edit.setState(ArticleService.STATE_DRAFT);

        Result<Map<String, Object>> result = articleController.update(target, edit, null);

        // 返回重名提示（Controller 的失败响应把字段错误装在 Map 里返回）
        assertEquals(1, result.getCode(), "应当返回失败码");
        assertTrue(result.getMessage() instanceof Map, "失败响应应当携带字段错误");
        Map<?, ?> errors = (Map<?, ?>) result.getMessage();
        assertTrue(errors.containsKey("title"), "应当提示 title 字段冲突，实际：" + errors);
        assertTrue(String.valueOf(errors.get("title")).contains("同名"),
                "应当是重名提示，实际：" + errors.get("title"));

        // 核心：对象存储一个操作都没发生（既没上传也没删旧）
        verify(rustFsUtil, never()).upload(any(), anyString(), anyString());
        verify(rustFsUtil, never()).delete(anyString(), anyString());

        // DB 里的正文指向也没变
        assertEquals(oldKey, articleMapper.selectById(target).getContent(),
                "被拒绝的请求不应改动正文指向");
    }
}
