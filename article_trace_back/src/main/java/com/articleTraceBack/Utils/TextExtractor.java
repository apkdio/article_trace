package com.articleTraceBack.Utils;
import org.jsoup.Jsoup;

public class TextExtractor {
    public static String extractor(String html, int maxLength) {
        if (html == null || html.isEmpty() || maxLength <= 0) {
            return "";
        }
        String plainText = Jsoup.parse(html).text();

        // 按字符数截取
        if (plainText.length() > maxLength) {
            return plainText.substring(0, maxLength);
        }
        return plainText;
    }
}