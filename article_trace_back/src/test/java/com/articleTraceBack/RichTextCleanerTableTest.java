package com.articleTraceBack;

import com.articleTraceBack.Utils.RichTextCleaner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只读表格块（`<div class="ql-table-embed"><table>…`）要能整块过白名单。
 *
 * Quill 1.3 没有表格格式，Markdown 导入的表格就是以这个形态存进正文的；白名单一旦削掉表格标签，
 * 文章保存后表格就没了——所以这条盯的是「后端零改动」这个前提。
 *
 * <pre>mvn test -Dtest=RichTextCleanerTableTest</pre>
 */
public class RichTextCleanerTableTest {

    @Test
    public void tableEmbedSurvivesWhitelist() {
        String html = "<div class=\"ql-table-embed\"><table><thead><tr><th>类别</th></tr></thead>"
                + "<tbody><tr><td>框架</td></tr></tbody></table></div>";
        String cleaned = RichTextCleaner.cleanToSafeHtml(html);
        assertTrue(cleaned.contains("<table"), "表格标签不能被削掉：" + cleaned);
        assertTrue(cleaned.contains("<th") && cleaned.contains("<td"), "表头与单元格要留着：" + cleaned);
        assertTrue(cleaned.contains("ql-table-embed"), "类名要留着，编辑器靠它把嵌入块认回来：" + cleaned);
        assertTrue(cleaned.contains("框架"), "单元格文字不能丢：" + cleaned);
    }

    @Test
    public void tableEmbedDropsContenteditableButKeepsContent() {
        // contenteditable 不在白名单里；编辑器侧由 blot 的 create() 自己加回来，丢了不影响展示
        String html = "<div class=\"ql-table-embed\" contenteditable=\"false\"><table><tbody>"
                + "<tr><td>1</td></tr></tbody></table></div>";
        String cleaned = RichTextCleaner.cleanToSafeHtml(html);
        assertFalse(cleaned.contains("contenteditable"), "属性该被白名单削掉：" + cleaned);
        assertTrue(cleaned.contains("<table") && cleaned.contains("<td>1</td>"), "但内容要完好：" + cleaned);
    }
}
