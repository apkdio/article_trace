package com.articleTraceBack;

import com.articleTraceBack.Service.MailService;
import com.articleTraceBack.Service.NotificationService;
import com.articleTraceBack.mapper.NotificationMapper;
import com.articleTraceBack.mapper.UserMapper;
import com.articleTraceBack.pojo.Notification;
import com.articleTraceBack.pojo.User;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 带模板的邮件通知：双载体渲染与 HTML 转义。
 *
 * <p>这是「拒绝理由」这类用户可控内容的必经关口——{@code EmailTemplateUtil} 自身**不做转义**，
 * 转义由 {@code NotificationServiceImpl} 在渲染 HTML 载体前完成。</p>
 *
 * <p>关键约定：<b>HTML 载体转义、纯文本载体不转义</b>。两个载体若共用一份变量表，
 * 纯文本客户端就会看到 {@code &lt;b&gt;} 这类字面量。</p>
 *
 * <p>{@link MailService} 被替换为 mock，用例不真发信。</p>
 *
 * <pre>mvn test -Dtest=NotificationMailTemplateTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "notification.mail.enabled=true")
public class NotificationMailTemplateTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @MockitoBean
    private MailService mailService;

    private Integer testUserId;
    private String testEmail;
    private String testUsername;

    @BeforeEach
    public void setUp() {
        testUsername = "nm" + (System.currentTimeMillis() % 100000000L);
        testEmail = testUsername + "@example.invalid";
        User user = new User();
        user.setUsername(testUsername);
        user.setNickname("模板测试");
        user.setPassword("x");
        user.setEmail(testEmail);
        user.setType(2);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        testUserId = user.getId();
    }

    @AfterEach
    public void tearDown() {
        if (testUserId == null) {
            return;
        }
        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", testUserId);
        notificationMapper.delete(nw);
        userMapper.deleteById(testUserId);
    }

    @Test
    public void testRejectMailEscapesHtmlButKeepsPlainTextRaw() {
        String evilReason = "<script>alert(1)</script> & \"引号\"";
        notificationService.notify(testUserId, "avatar-rejected", "头像审核未通过",
                "你提交的头像未通过审核。", "avatar-rejected",
                Map.of("reason", evilReason, "nickname", "a&b"));

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq(testEmail), eq("头像审核未通过"),
                textCaptor.capture(), htmlCaptor.capture());

        String html = htmlCaptor.getValue();
        String text = textCaptor.getValue();

        // HTML 载体必须转义：否则拒绝理由里的标签会被邮件客户端当结构解析
        assertFalse(html.contains("<script>"), "HTML 载体不应含未转义的 script 标签");
        assertTrue(html.contains("&lt;script&gt;"), "HTML 载体应含转义后的实体");
        assertTrue(html.contains("a&amp;b"), "HTML 载体应转义昵称里的 &");
        assertTrue(html.contains("ArticleTrace Team"), "HTML 载体应由模板渲染");
        assertTrue(html.contains("/logo2.png"), "通用变量 logoUrl 应由服务补齐");
        assertTrue(html.contains(String.valueOf(java.time.Year.now().getValue())), "通用变量 year 应由服务补齐");

        // 纯文本载体不能转义：否则客户端会看到 &lt; 这类字面量
        assertTrue(text.contains(evilReason), "纯文本载体应原样输出用户内容");
        assertFalse(text.contains("&lt;"), "纯文本载体不应含 HTML 实体");
    }

    @Test
    public void testMissingTemplateFallsBackToPlainText() {
        notificationService.notify(testUserId, "avatar-rejected", "标题", "纯文本兜底内容",
                "not-exist-template", Map.of("reason", "x"));

        // 模板缺失属于部署问题：退回纯文本单载体，而不是发一封空邮件
        verify(mailService).send(eq(testEmail), eq("标题"), eq("纯文本兜底内容"));
    }

    @Test
    public void testAvatarSceneResolvesToAvatarType() {
        notificationService.notify(testUserId, "avatar-rejected", "标题", "内容");

        QueryWrapper<Notification> nw = new QueryWrapper<>();
        nw.eq("receiver_id", testUserId);
        Notification saved = notificationMapper.selectOne(nw);
        assertTrue(saved != null, "站内信应已入库");
        assertTrue(Notification.TYPE_AUDIT.equals(saved.getType()),
                "avatar-* 场景应归入审核类型，实际=" + saved.getType());
    }
}
