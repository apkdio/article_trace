package com.articleTraceBack.Utils;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import java.util.regex.Pattern;

// 富文本清洗工具

public class RichTextCleaner {

    // 匹配 data:image/base64 的完整 img 标签（可选，Jsoup 可以直接删除）
    private static final Pattern BASE64_IMG_PATTERN = Pattern.compile(
            "<img[^>]+src\\s*=\\s*['\"]data:image/[^;]+;base64,[^'\"]+['\"][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    public static String cleanToPlainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        // 预先移除 base64 图片标签，减轻 Jsoup 解析负担
        String cleanedHtml = BASE64_IMG_PATTERN.matcher(html).replaceAll("");

        // 使用 Jsoup 解析并清理
        //    使用 Relaxed 白名单：保留基本文本格式，但我们会转为纯文本，所以其实任何标签都会被移除
        //    但我们需要移除 <script>、<style> 等，所以用 Cleaner + 白名单
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