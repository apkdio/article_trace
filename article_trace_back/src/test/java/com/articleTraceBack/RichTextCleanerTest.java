package com.articleTraceBack;

import com.articleTraceBack.Utils.RichTextCleaner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 富文本白名单清洗。
 *
 * <p>这是文章正文通往 {@code v-html} 的唯一关卡——脏 HTML 会变成存储型 XSS，
 * 而前端无法补救（富文本要么全转义、排版全毁，要么不转义、有 XSS）。</p>
 *
 * <pre>mvn test -Dtest=RichTextCleanerTest</pre>
 */
public class RichTextCleanerTest {

    @Test
    public void testStripsScriptAndEventHandlers() {
        String dirty = "<p>正文</p><script>alert(1)</script>"
                + "<img src=\"https://a.com/x.png\" onerror=\"alert(2)\">"
                + "<div onclick=\"alert(3)\">点我</div>";

        String clean = RichTextCleaner.cleanToSafeHtml(dirty);

        assertFalse(clean.contains("<script"), "script 标签应被剔除");
        assertFalse(clean.toLowerCase().contains("alert(1)"), "脚本内容不应残留");
        assertFalse(clean.contains("onerror"), "onerror 应被剔除");
        assertFalse(clean.contains("onclick"), "onclick 应被剔除");
        // 正文本身要留住
        assertTrue(clean.contains("正文"));
        assertTrue(clean.contains("点我"));
    }

    @Test
    public void testBlocksJavascriptProtocol() {
        String dirty = "<a href=\"javascript:alert(1)\">链接</a>"
                + "<img src=\"javascript:alert(2)\">";

        String clean = RichTextCleaner.cleanToSafeHtml(dirty);

        assertFalse(clean.contains("javascript:"), "javascript: 伪协议应被挡掉");
        assertTrue(clean.contains("链接"), "链接文字应保留");
    }

    @Test
    public void testKeepsFormattingAndClass() {
        // Quill 的对齐/缩进靠 class 表达，清洗不能把它抹掉
        String rich = "<p class=\"ql-align-center\"><strong>加粗</strong></p>"
                + "<img src=\"https://a.com/x.png\" alt=\"图\" width=\"100\">"
                + "<a href=\"https://a.com\" target=\"_blank\">站外</a>"
                + "<ul><li>条目</li></ul>";

        String clean = RichTextCleaner.cleanToSafeHtml(rich);

        assertTrue(clean.contains("<strong>"), "加粗应保留");
        assertTrue(clean.contains("ql-align-center"), "class 应保留，否则排版丢失");
        assertTrue(clean.contains("https://a.com/x.png"), "正常图片地址应保留");
        assertTrue(clean.contains("alt=\"图\""), "img 的 alt 应保留");
        assertTrue(clean.contains("target=\"_blank\""), "a 的 target 应保留");
        assertTrue(clean.contains("<li>"), "列表应保留");
    }

    @Test
    public void testKeepsRasterBase64Image() {
        // 编辑器插入的图片就是这个形态：一刀切删掉，作者看到的就是「编辑时看得见、保存后没了」
        String withImage = "<p>前</p><img src=\"data:image/png;base64,AAAA\"><p>后</p>";

        String clean = RichTextCleaner.cleanToSafeHtml(withImage);

        assertTrue(clean.contains("data:image/png;base64,AAAA"), "位图内联图应保留");
        assertTrue(clean.contains("前") && clean.contains("后"), "正文其余部分不受影响");
    }

    @Test
    public void testDropsNonRasterDataImage() {
        // SVG 能内嵌脚本，而正文是 v-html 直渲染：放进来就是一个存储型 XSS 的口子
        String svg = "<p>前</p><img src=\"data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=\"><p>后</p>";
        String htmlData = "<img src=\"data:text/html;base64,PHNjcmlwdD4=\">";

        String cleanSvg = RichTextCleaner.cleanToSafeHtml(svg);

        assertFalse(cleanSvg.contains("svg"), "SVG 内联图应剔除");
        assertTrue(cleanSvg.contains("前") && cleanSvg.contains("后"), "正文其余部分应保留");
        assertFalse(RichTextCleaner.cleanToSafeHtml(htmlData).contains("text/html"), "非图片 data URI 应剔除");
    }

    @Test
    public void testNullAndEmptyAreSafe() {
        assertEquals("", RichTextCleaner.cleanToSafeHtml(null));
        assertEquals("", RichTextCleaner.cleanToSafeHtml(""));
    }

    @Test
    public void testPlainTextPassesThrough() {
        assertEquals("就是一段普通文字", RichTextCleaner.cleanToSafeHtml("就是一段普通文字"));
    }
}
