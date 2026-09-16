package com.articleTraceBack.Utils;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

// 富文本清洗工具

public class RichTextCleaner {

    // 匹配 data:image/base64 的完整 img 标签（可选，Jsoup 可以直接删除）
    private static final Pattern BASE64_IMG_PATTERN = Pattern.compile(
            "<img[^>]+src\\s*=\\s*['\"]data:image/[^;]+;base64,[^'\"]+['\"][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * 落库/回显用的白名单。
     *
     * <p>在 {@link Safelist#relaxed()} 基础上补两件事：</p>
     * <ul>
     *   <li><b>放行 class</b>——Quill 的对齐/缩进都靠 class 表达，不放行会把排版清掉；
     *       class 本身不具备执行能力，放行是安全的</li>
     *   <li><b>限定图片与链接的协议</b>——挡掉 {@code javascript:} 这类可执行伪协议</li>
     * </ul>
     *
     * <p>脚本、事件属性（on*）、style 里的表达式等不在白名单内，一律被剔除。</p>
     */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("figure", "figcaption", "hr")
            .addAttributes(":all", "class")
            .addAttributes("img", "alt", "width", "height")
            .addAttributes("a", "target", "rel")
            .addProtocols("img", "src", "http", "https")
            .addProtocols("a", "href", "http", "https", "mailto");

    /**
     * 清洗富文本并保留 HTML 排版：按白名单剔除脚本、事件属性与危险协议。
     *
     * <p>正文会在前端用 {@code v-html} 直接渲染，脏 HTML 就是存储型 XSS——
     * 这个方法是那条路径上唯一的关卡，因此保存与回显都要过一遍：
     * 保存时保证新数据干净，回显时覆盖修复之前就已存在的历史数据。</p>
     *
     * <p><b>不要指望前端转义</b>：富文本要么全转义（排版全毁）、要么不转义（XSS），
     * 没有中间态，所以清洗只能在服务端做。</p>
     */
    public static String cleanToSafeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        // 与纯文本提取保持一致：先剃掉 base64 图，减轻解析负担
        String cleanedHtml = BASE64_IMG_PATTERN.matcher(html).replaceAll("");
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(cleanedHtml, "", SAFELIST, outputSettings);
    }

    public static String cleanToPlainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        // 预先移除 base64 图片标签，减轻 Jsoup 解析负担
        String cleanedHtml = BASE64_IMG_PATTERN.matcher(html).replaceAll("");

        // 用 Jsoup 解析后手动移除 script/style/link/meta/noscript 与 img，最后取纯文本
        Document.OutputSettings outputSettings = new Document.OutputSettings()
                .prettyPrint(false)   // 不格式化输出，减少空白
                .escapeMode(Entities.EscapeMode.xhtml); // 可选

        Document doc = Jsoup.parse(cleanedHtml);
        doc.outputSettings(outputSettings);

        // 移除所有脚本和样式标签
        doc.select("script, style, link, meta, noscript").remove();

        // 移除所有图片标签（如果图片内容不需要保留）
        doc.select("img").remove();

        // 可选：将 <br> 和块级标签替换为换行符（便于阅读）
        doc.select("br").after("\n");
        doc.select("p, div, h1, h2, h3, h4, h5, h6, li").after("\n");

        // 提取纯文本
        String text = doc.text();

        // 3. 后处理：合并多余空白、去掉首尾空格
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }
}