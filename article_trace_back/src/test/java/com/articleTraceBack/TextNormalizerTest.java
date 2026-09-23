package com.articleTraceBack;

import com.articleTraceBack.Utils.TextNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 违禁词匹配前的归一化。
 *
 * <p>Aho-Corasick 逐字符精确匹配，所以「赌 博」「赌　博」「赌&#8203;博」这类插了干扰字符的写法，
 * 在归一化之前一律命中不了——用例把这些绕过路径逐条钉住。</p>
 *
 * <pre>mvn test -Dtest=TextNormalizerTest</pre>
 */
public class TextNormalizerTest {

    @Test
    public void testRemovesSpacesAndZeroWidth() {
        assertEquals("赌博", TextNormalizer.forMatch("赌 博"), "半角空格应被压掉");
        assertEquals("赌博", TextNormalizer.forMatch("赌　博"), "全角空格应被压掉");
        assertEquals("赌博", TextNormalizer.forMatch("赌\t博  "), "制表符与首尾空白应被压掉");
        assertEquals("赌博", TextNormalizer.forMatch("赌\u200b博"), "零宽空格应被去掉");
        assertEquals("赌博", TextNormalizer.forMatch("赌\uFEFF博"), "BOM 应被去掉");
    }

    @Test
    public void testFullWidthAndCase() {
        assertEquals("微信abc123", TextNormalizer.forMatch("微信ＡＢＣ１２３"), "全角字母数字应压成半角");
        assertEquals("qq123456", TextNormalizer.forMatch("ＱＱ１２３４５６"), "全角大写应压成小写");
    }

    @Test
    public void testKeepsNormalTextAndNullSafe() {
        assertEquals("就是一段普通文字", TextNormalizer.forMatch("就是一段普通文字"), "普通文本不应被改动");
        assertEquals("", TextNormalizer.forMatch(null));
        assertEquals("", TextNormalizer.forMatch(""));
        assertEquals("", TextNormalizer.forMatch("   \u200b "), "全是干扰字符时压成空串");
    }
}
