package com.articleTraceBack;

import com.articleTraceBack.Utils.EmailTemplateUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件模板渲染测试。
 *
 * <p>验证两个载体都能渲染、占位符被正确替换、缺失变量时不静默丢失痕迹。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EmailTemplateUtilTest {

    @Autowired
    private EmailTemplateUtil emailTemplateUtil;

    private Map<String, String> vars() {
        Map<String, String> vars = new HashMap<>();
        vars.put("code", "421928");
        vars.put("minutes", "5");
        vars.put("year", "2026");
        vars.put("logoUrl", "https://example.com/logo2.png");
        return vars;
    }

    @Test
    public void rendersBothCarriers() {
        String html = emailTemplateUtil.render("email-code", "html", vars());
        String text = emailTemplateUtil.render("email-code", "txt", vars());

        assertNotNull(html, "HTML 模板应能渲染");
        assertNotNull(text, "纯文本模板应能渲染");

        // 验证码、有效期、版权年份都要落到两种载体里
        assertTrue(html.contains("421928"), "HTML 应含验证码");
        assertTrue(text.contains("421928"), "纯文本应含验证码");
        assertTrue(html.contains("2026"), "HTML 应含版权年份");
        assertTrue(text.contains("2026"), "纯文本应含版权年份");

        // logo 只出现在 HTML 载体
        assertTrue(html.contains("https://example.com/logo2.png"), "HTML 应含 logo 地址");
        assertFalse(text.contains("https://example.com/logo2.png"), "纯文本不应含 logo 地址");

        // 渲染后不应残留占位符
        assertFalse(html.contains("{{"), "HTML 不应残留占位符：" + html);
        assertFalse(text.contains("{{"), "纯文本不应残留占位符：" + text);
    }

    @Test
    public void keepsUnfilledPlaceholderVisible() {
        Map<String, String> partial = new HashMap<>();
        partial.put("code", "123456");

        String text = emailTemplateUtil.render("email-code", "txt", partial);

        assertNotNull(text);
        assertTrue(text.contains("123456"), "已提供的变量应被替换");
        // 缺值时不静默替换成空串——保留原文便于发现漏配
        assertTrue(text.contains("{{minutes}}"), "未提供的占位符应原样保留，实际：" + text);
    }

    @Test
    public void returnsNullForMissingTemplate() {
        assertNull(emailTemplateUtil.render("no-such-template", "html", vars()),
                "模板不存在应返回 null 而不是抛异常");
    }
}
