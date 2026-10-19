package com.articleTraceBack.Utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC 匹配器的归一化双轨：模式串与文本同口径归一化后匹配，命中回显词表原词。
 * 这是对 bugs_report P1-1 的回归——词表只 trim 时，含空格 / 大写 / 全角的词条永远命不中。
 */
class AhoCorasickNormalizationTest {

    private List<AhoCorasickUtil.Match> search(String text, String... words) {
        AhoCorasickUtil ac = new AhoCorasickUtil();
        for (String w : words) {
            ac.addKeyword(TextNormalizer.forMatch(w), w);
        }
        ac.build();
        return ac.search(TextNormalizer.forMatch(text));
    }

    @Test
    void wordsWithSpacesStillMatchNormalizedText() {
        // 词表里「54 式」带空格：正文里无论怎么写都该命中
        assertTrue(!search("这把54 式很好", "54 式").isEmpty(), "词表原样（带空格）应命中");
        assertTrue(!search("这把54式很好", "54 式").isEmpty(), "正文无空格应命中");
        assertTrue(!search("这把5 4式很好", "54 式").isEmpty(), "正文插空格应命中");
        assertTrue(!search("这把５４式很好", "54 式").isEmpty(), "正文全角应命中");
    }

    @Test
    void caseAndWidthInKeywordAreNormalized() {
        assertTrue(!search("加我QQ聊聊", "QQ").isEmpty(), "大写词条应命中大写正文");
        assertTrue(!search("加我qq聊聊", "QQ").isEmpty(), "大写词条应命中小写正文");
        assertTrue(!search("亚砷（酸）酐有毒", "亚砷（酸）酐").isEmpty(), "全角括号词条应命中");
    }

    @Test
    void matchReportsOriginalWordForDisplay() {
        // 站长排查要按词表原词回查，回显不能是归一化后的形态
        List<AhoCorasickUtil.Match> matches = search("这把５４式很好", "54 式");
        assertEquals(1, matches.size());
        assertEquals("54 式", matches.get(0).keyword, "命中回显词表原词");
    }

    @Test
    void sameShapeKeywordsAreDeduplicated() {
        // holder 负责去重；这里验证同形词不会破坏匹配器
        assertTrue(!search("加我qq聊聊", "QQ", "qq", "ＱＱ").isEmpty());
    }
}
