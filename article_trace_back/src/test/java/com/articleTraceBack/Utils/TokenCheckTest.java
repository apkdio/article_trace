package com.articleTraceBack.Utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拦截矩阵：规则从测试配置原样加载（与主配置、容器配置同值），URI 取前端 src/api 的真实调用面。
 * 改拦截面要同步三份配置与这里的期望。
 */
class TokenCheckTest {
    private static String[] writerRules;
    private static String[] readerRules;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadRules() throws Exception {
        try (InputStream in = TokenCheckTest.class.getResourceAsStream("/application.yml")) {
            assertNotNull(in, "测试配置 application.yml 必须在 classpath 上");
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> notAllow =
                    (Map<String, Object>) ((Map<String, Object>) spring.get("tokenCheck")).get("notAllowUrl");
            writerRules = ((String) notAllow.get("writer")).split(",");
            readerRules = ((String) notAllow.get("reader")).split(",");
        }
    }

    /** 作者的写作与自助接口，一条都不能被拦 */
    @Test
    void writersCanUseAuthorEndpoints() {
        for (String uri : new String[]{
                "/article/list", "/article/add", "/article/update/12", "/article/delete",
                "/category", "/category/add", "/category/update/3", "/category/delete/3",
                "/applyAuthor", "/applyAuthor/mine", "/avatar/mine", "/report",
                "/notification/list", "/notification/unreadCount", "/notification/readAll",
                "/user/userInfo", "/user/update", "/user/updatePass",
                "/user/nowLogo", "/user/updateUserLogo", "/user/removeUserLogo"}) {
            assertFalse(TokenCheck.blocked(uri, 1, writerRules, readerRules), "作者应放行: " + uri);
        }
    }

    /** 站长专属接口对作者全关（含各类 manage 子路径与 /review/summary） */
    @Test
    void writersAreBlockedFromMasterEndpoints() {
        for (String uri : new String[]{
                "/user/accountManage", "/user/articleManage", "/user/delete", "/user/changeType",
                "/article/assess", "/article/manageArticles", "/article/count",
                "/applyAuthor/manage/list", "/applyAuthor/manage/pendingCount", "/applyAuthor/manage/review/3",
                "/avatar/manage/list", "/avatar/manage/pendingCount", "/avatar/manage/review/2",
                "/profile/manage/list", "/profile/manage/pendingCount", "/profile/manage/review/7",
                "/report/manage/list", "/report/manage/pendingCount", "/report/manage/handle/9",
                "/review/summary"}) {
            assertTrue(TokenCheck.blocked(uri, 1, writerRules, readerRules), "作者应被拦: " + uri);
        }
    }

    /** 读者被拦的范围 = 作者的拦截面 ∪ 写作接口 */
    @Test
    void readersAreBlockedFromAuthorAndMasterEndpoints() {
        for (String uri : new String[]{
                "/category", "/category/add", "/category/detail/2",
                "/article/list", "/article/add", "/article/update/12",
                "/article/delete", "/article/count",
                "/article/assess", "/article/manageArticles",
                "/user/accountManage", "/user/delete", "/user/changeType",
                "/review/summary", "/report/manage/list", "/applyAuthor/manage/list"}) {
            assertTrue(TokenCheck.blocked(uri, 2, writerRules, readerRules), "读者应被拦: " + uri);
        }
    }

    /** 读者的阅读与自助接口不受影响（/reader 只读匿名放行，登录动作走 excludeUrls） */
    @Test
    void readersCanUsePublicAndSelfServiceEndpoints() {
        for (String uri : new String[]{
                "/reader/getArticles", "/reader/article/3", "/reader/article/hotArticles", "/reader/comments",
                "/reader/writerInfo", "/reader/masterInfo", "/reader/addComment", "/reader/deleteComment",
                "/user/userInfo", "/user/update", "/user/updatePass",
                "/user/nowLogo", "/user/updateUserLogo", "/user/removeUserLogo",
                "/applyAuthor", "/applyAuthor/mine", "/avatar/mine", "/report",
                "/notification/list", "/notification/unreadCount",
                "/user/captcha", "/user/login", "/user/register"}) {
            assertFalse(TokenCheck.blocked(uri, 2, writerRules, readerRules), "读者应放行: " + uri);
        }
    }

    /** 站长不受任何拦截 */
    @Test
    void mastersAreNotBlocked() {
        for (String uri : new String[]{
                "/user/accountManage", "/user/delete", "/user/changeType",
                "/article/assess", "/article/manageArticles", "/article/count",
                "/article/add", "/article/list",
                "/category", "/category/add", "/review/summary",
                "/applyAuthor/manage/review/1", "/avatar/manage/review/2"}) {
            assertFalse(TokenCheck.blocked(uri, 0, writerRules, readerRules), "站长应放行: " + uri);
        }
    }

    /** 通配符语义：/xxx/** 必须同时命中 /xxx 本身；字面量拦不住子路径 */
    @Test
    void wildcardRulesMatchRealUris() {
        AntPathMatcher matcher = new AntPathMatcher();
        assertTrue(matcher.match("/user/delete/**", "/user/delete"));
        assertTrue(matcher.match("/category/**", "/category"));
        assertFalse(matcher.match("/review", "/review/summary"));
        assertTrue(matcher.match("/review/**", "/review/summary"));
        assertTrue(matcher.match("/applyAuthor/manage/**", "/applyAuthor/manage/review/3"));
    }
}
