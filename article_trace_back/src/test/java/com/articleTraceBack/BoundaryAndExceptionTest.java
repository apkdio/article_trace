package com.articleTraceBack;

import com.articleTraceBack.Controller.ArticleController;
import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Utils.GlobalExceptionHandler;
import com.articleTraceBack.Utils.PageUtil;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.pojo.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 边界与异常处理的回归测试。
 *
 * <p>覆盖三处此前会直接 500 或给出误导性提示的地方：</p>
 * <ol>
 *   <li>未兜底的数据完整性异常 → 现在有统一友好提示，且格式异常提示不再掩盖真因</li>
 *   <li>分页参数未校验 → 负值会生成非法 SQL，过大则可能一次拉全表</li>
 *   <li>{@code categoryId} 拆箱 NPE → 请求不带分类时 500，且发生在重名校验之前</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class BoundaryAndExceptionTest {

    private static final int AUTHOR = 1;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    @Autowired
    private ArticleController articleController;

    @Autowired
    private TestFixtures fixtures;

    @AfterEach
    public void clearThreadLocal() {
        ThreadLocalUtil.remove();
    }

    // ---------- ① 异常处理 ----------

    @Test
    public void duplicateKeyReturnsFriendlyMessageInsteadOfStackTrace() {
        Result<Map<String, Object>> result = exceptionHandler.handleDuplicateKeyException(
                new DuplicateKeyException("Duplicate entry 'x' for key 'uk_user_title'"));

        assertEquals(1, result.getCode(), "应当是失败响应");
        assertNotNull(result.getMessage());
        // 不谎称是某个具体字段冲突——异常里只有约束名，指不出调用方该改哪个字段
        String message = String.valueOf(result.getMessage());
        assertFalse(message.contains("Exception"), "不应把异常类型暴露给调用方：" + message);
        assertFalse(message.contains("uk_user_title"), "不应把库内部约束名暴露出去：" + message);
        assertTrue(message.contains("冲突") || message.contains("重复"),
                "应当给出可理解的提示：" + message);
    }

    @Test
    public void malformedBodyErrorKeepsRootCause() {
        // 模拟 Spring 抛出的解析异常：真正有用的信息在冒号「之后」
        String raw = "JSON parse error: Cannot deserialize value of type `int` from String \"abc\"";
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                raw, new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8)));

        Result<Map<String, Object>> result = assertDoesNotThrow(
                () -> exceptionHandler.handleHttpMessageNotReadableException(ex));
        String message = String.valueOf(result.getMessage());

        // 此前只截取冒号前那段，调用方拿到的永远是「JSON parse error」，等于没说
        assertTrue(message.contains("Cannot deserialize") || message.contains("int"),
                "应当保留真正的原因，而不是只回一句 JSON parse error：" + message);
    }

    @Test
    public void malformedBodyWithoutColonDoesNotThrow() {
        // 此前 indexOf(":") 返回 -1 时 substring(0,-1) 会越界，在异常处理器里再抛一次
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "broken message without colon", new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8)));

        assertDoesNotThrow(() -> exceptionHandler.handleHttpMessageNotReadableException(ex),
                "提示信息里没有冒号时也不能抛异常");
    }

    // ---------- ② 分页归一化 ----------

    @Test
    public void pageParamsAreNormalized() {
        assertEquals(1, PageUtil.normalizePageNum(0), "0 页归一到第 1 页");
        assertEquals(1, PageUtil.normalizePageNum(-5), "负数页码归一到第 1 页");
        assertEquals(3, PageUtil.normalizePageNum(3), "正常页码保持不变");

        assertEquals(PageUtil.DEFAULT_PAGE_SIZE, PageUtil.normalizePageSize(0), "0 条/页取默认值");
        assertEquals(PageUtil.DEFAULT_PAGE_SIZE, PageUtil.normalizePageSize(-1),
                "负数每页条数取默认值——此前会算出 limit x,-1 的非法 SQL");
        assertEquals(PageUtil.MAX_PAGE_SIZE, PageUtil.normalizePageSize(100000),
                "过大的每页条数应当截到上限，避免一次拉全表");
        assertEquals(20, PageUtil.normalizePageSize(20), "正常取值保持不变");
    }

    // ---------- ③ categoryId 拆箱 ----------

    @Test
    public void addArticleWithoutCategoryReturnsMessageNotNpe() {
        int userId = fixtures.ensureUser(AUTHOR);
        loginAs(userId, AUTHOR);

        Article article = new Article();
        article.setTitle("zz-test-缺分类-" + System.nanoTime());
        article.setContent("正文内容");
        article.setState(ArticleService.STATE_DRAFT);
        article.setCategoryId(null);       // 请求里没带分类

        // 此前这里是 int categoryId = article.getCategoryId() → 拆箱 NPE → 500，
        // 而且该行位于重名校验之前，连内容类报错都走不到
        Result<Map<String, Object>> result = assertDoesNotThrow(() -> articleController.addArticle(article, null),
                "缺少分类时应当返回提示而不是抛 NPE");

        assertEquals(1, result.getCode(), "应当返回失败");
        assertTrue(String.valueOf(result.getMessage()).contains("categoryId"),
                "应当明确指出缺少的是分类，实际：" + result.getMessage());
    }

    private void loginAs(int uid, int roleType) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", uid);
        userInfo.put("type", roleType);
        userInfo.put("name", "zz-test-boundary-" + uid);
        ThreadLocalUtil.set(userInfo);
    }
}
