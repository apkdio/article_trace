package com.articleTraceBack.Utils;

/**
 * 违禁词匹配前的文本归一化。
 *
 * <p>Aho-Corasick 是**逐字符精确匹配**：只要在词中间插一个空格、换成全角、或塞一个零宽字符，
 * 「赌 博」「赌　博」「赌&#8203;博」就全都命中不了——这是词表最容易被人绕过的一环。</p>
 *
 * <p>只用于**匹配**，不改动落库的原文。命中记录的是词本身、不依赖位置，
 * 所以把文本压平不会影响命中结果的表达（以及给作者/站长的展示）。</p>
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * 归一化：去掉空白与零宽字符 → 全角 ASCII 压成半角 → 统一小写。
     *
     * <p>刻意不做的两件事：不做拼音/形近字映射（误伤率高，交给词表与后续的 LLM 层），
     * 也不动标点——「赌·博」这种连字符变形留给词表覆盖。</p>
     */
    public static String forMatch(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 零宽字符与软连字符：肉眼看不见，但足以切断一次匹配
            if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF' || c == '\u00AD') {
                continue;
            }
            // 空白（Character.isWhitespace 已含全角空格 U+3000；U+00A0 需单独判断）
            if (Character.isWhitespace(c) || c == '\u00A0') {
                continue;
            }
            // 全角 ASCII（！～）压成半角
            if (c >= '\uFF01' && c <= '\uFF5E') {
                c = (char) (c - 0xFEE0);
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
