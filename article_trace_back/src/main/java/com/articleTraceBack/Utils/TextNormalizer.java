package com.articleTraceBack.Utils;

/**
 * 违禁词匹配前的文本归一化：去空白与零宽字符 → 全角 ASCII 压半角 → 统一小写。
 * AC 是逐字符匹配，词中间插个字符就能绕过；只作用于送进匹配的副本，落库原文不动。
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** 归一化；null / 空串返回空串。刻意不做拼音与形近字映射（误伤率高），也不动标点。 */
    public static String forMatch(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF' || c == '\u00AD') {
                continue;
            }
            // isWhitespace 已含全角空格，U+00A0 要单独判断
            if (Character.isWhitespace(c) || c == '\u00A0') {
                continue;
            }
            // 全角 ASCII（！～）转半角
            if (c >= '\uFF01' && c <= '\uFF5E') {
                c = (char) (c - 0xFEE0);
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
