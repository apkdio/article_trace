package com.articleTraceBack.Utils;

import org.jsoup.Jsoup;

import java.util.regex.Pattern;

public class TextExtractor {

    private TextExtractor() {
    }
    /**
     * 公式段：与前端展示端同一套分隔符（见 article_trace_front/src/utils/mathRender.js）。
     *
     * 只认 {@code $$..$$}、{@code \[..\]}、{@code \(..\)}，**不认单独的 {@code $..$}**——
     * 那会把「价格 $5 到 $10」这种正文一起吃掉。
     */
    private static final Pattern MATH_SEGMENT = Pattern.compile(
            "\\$\\$[\\s\\S]+?\\$\\$|\\\\\\[[\\s\\S]+?\\\\\\]|\\\\\\([\\s\\S]+?\\\\\\)");

    /** 摘要结果：文本 + 是否被截断。调用方据此决定加不加省略号——按 {@code length()} 复判会在含 emoji 时误判 */
    public record Summary(String text, boolean truncated) {
    }

    /** 纯文本摘要（只要文本、不关心是否截断时的入口） */
    public static String extractor(String html, int maxLength) {
        return summarize(html, maxLength).text();
    }

    /** 提取纯文本摘要：去标签、**去公式段**（LaTeX 原文在列表卡上是噪声）、按码点长度截断 */
    public static Summary summarize(String html, int maxLength) {
        if (html == null || html.isEmpty() || maxLength <= 0) {
            return new Summary("", false);
        }
        String plainText = Jsoup.parse(html).text();
        // 公式被去掉后会留下连续空白，压成一个空格再截断
        plainText = MATH_SEGMENT.matcher(plainText).replaceAll("").replaceAll("\\s{2,}", " ").trim();

        // 按码点截取：substring 按 char 落在代理对中间会把 emoji 截成乱码。
        // 截断与否由码点数判定，别让调用方拿 length() 猜（非 BMP 字符下那必然猜错）
        if (plainText.codePointCount(0, plainText.length()) > maxLength) {
            int end = plainText.offsetByCodePoints(0, maxLength);
            return new Summary(plainText.substring(0, end), true);
        }
        return new Summary(plainText, false);
    }
}
