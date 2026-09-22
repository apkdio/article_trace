package com.articleTraceBack.Utils;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

// 富文本清洗工具

public class RichTextCleaner {

    // 匹配 data:image/base64 的完整 img 标签（纯文本提取时整段剥掉）
    private static final Pattern BASE64_IMG_PATTERN = Pattern.compile(
            "<img[^>]+src\\s*=\\s*['\"]data:image/[^;]+;base64,[^'\"]+['\"][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * 允许留在正文里的内联图片：**只认位图**。
     *
     * <p>{@code data:image/svg+xml} 能内嵌脚本，正文又是 {@code v-html} 直渲染，放进来就是存储型 XSS。</p>
     */
    private static final Pattern RASTER_DATA_IMAGE = Pattern.compile(
            "^data:image/(png|jpe?g|gif|webp|bmp);base64,");

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
            .addProtocols("img", "src", "http", "https", "data")
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
        // 内联图片（base64）**保留**：编辑器插入的图就是这个形态，早先一刀切删掉，
        // 结果就是「编辑时看得见、保存后没了」。正文最终落在 RustFS 对象里、库里只有对象名，
        // 所以胖的是请求体而不是数据库；非位图的 data: 由下面那步剔掉。
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        return dropNonRasterDataImages(Jsoup.clean(html, "", SAFELIST, outputSettings), outputSettings);
    }

    /**
     * 把 src 是 {@code data:} 但**不是位图**的 img 删掉。
     *
     * <p>白名单放行 {@code data:} 是为内联图片，不是为了放行任意 data URI：
     * {@code data:image/svg+xml}（乃至 {@code data:text/html}）能内嵌脚本，
     * 而正文在前端是 {@code v-html} 直渲染——放进来就是一个存储型 XSS 的口子。
     * 这里只认 png / jpeg / gif / webp / bmp 的 base64 位图。</p>
     */
    private static String dropNonRasterDataImages(String html, Document.OutputSettings outputSettings) {
        if (html == null || html.isEmpty() || !html.contains("data:")) {
            return html;
        }
        Document doc = Jsoup.parse(html);
        doc.outputSettings(outputSettings);
        boolean changed = false;
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src").trim().toLowerCase();
            if (src.startsWith("data:") && !RASTER_DATA_IMAGE.matcher(src).find()) {
                img.remove();
                changed = true;
            }
        }
        return changed ? doc.body().html() : html;
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