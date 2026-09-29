package com.articleTraceBack.Utils;

import org.jsoup.Jsoup;

import java.util.regex.Pattern;

public class TextExtractor {
    /**
     * 公式段：与前端展示端同一套分隔符（见 article_trace_front/src/utils/mathRender.js）。
     *
     * 只认 {@code $$..$$}、{@code \[..\]}、{@code \(..\)}，**不认单独的 {@code $..$}**——
     * 那会把「价格 $5 到 $10」这种正文一起吃掉。
     */
    private static final Pattern MATH_SEGMENT = Pattern.compile(
            "\\$\\$[\\s\\S]+?\\$\\$|\\\\\\[[\\s\\S]+?\\\\\\]|\\\\\\([\\s\\S]+?\\\\\\)");

    /** 提取纯文本摘要：去标签、**去公式段**（LaTeX 原文在列表卡上是噪声）、按长度截断 */
    public static String extractor(String html, int maxLength) {
        if (html == null || html.isEmpty() || maxLength <= 0) {
            return "";
        }
        String plainText = Jsoup.parse(html).text();
        // 公式被去掉后会留下连续空白，压成一个空格再截断
        plainText = MATH_SEGMENT.matcher(plainText).replaceAll("").replaceAll("\\s{2,}", " ").trim();

        // 按字符数截取
        if (plainText.length() > maxLength) {
            return plainText.substring(0, maxLength);
        }
        return plainText;
    }
}
