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

    /** 允许留在正文里的内联图片：只认位图（{@code data:image/svg+xml} 能内嵌脚本，而正文是 {@code v-html} 直渲染）。 */
    private static final Pattern RASTER_DATA_IMAGE = Pattern.compile(
            "^data:image/(png|jpe?g|gif|webp|bmp);(?:charset=[^;]+;)?base64,");

    /**
    * 落库/回显用的白名单：在 {@link Safelist#relaxed()} 基础上放行 class（Quill 排版依赖它）并限定图片与链接协议。
    * 脚本、事件属性（on*）与 style 表达式不在白名单内，一律剔除。
    */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("figure", "figcaption", "hr")
            .addAttributes(":all", "class")
            .addAttributes("img", "alt", "width", "height")
            .addAttributes("a", "target", "rel")
            .addProtocols("img", "src", "http", "https", "data")
            .addProtocols("a", "href", "http", "https", "mailto");

    /** 按白名单清洗富文本并保留排版；正文会经 {@code v-html} 直渲染，因此保存与回显都必须调用。 */
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

    /** 删掉 src 是 {@code data:} 但非位图的 img：只认 png / jpeg / gif / webp / bmp 的 base64 位图，防止内嵌脚本。 */
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

    /** 清洗富文本为纯文本（去标签 / 脚本 / 图片），用于敏感词校验 */
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