package com.articleTraceBack;

import com.articleTraceBack.Utils.TextNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 归一化：插空格 / 全角 / 零宽字符这些绕过写法，归一化后同样能命中。 */
public class TextNormalizerTest {

    @Test
    public void testRemovesSpacesAndZeroWidth() {
        assertEquals("赌博", TextNormalizer.forMatch("赌 博"), "半角空格");
        assertEquals("赌博", TextNormalizer.forMatch("赌　博"), "全角空格");
        assertEquals("赌博", TextNormalizer.forMatch("赌\t博  "), "制表符与首尾空白");
        assertEquals("赌博", TextNormalizer.forMatch("赌\u200b博"), "零宽空格");
        assertEquals("赌博", TextNormalizer.forMatch("赌\uFEFF博"), "BOM");
    }

    @Test
    public void testFullWidthAndCase() {
        assertEquals("微信abc123", TextNormalizer.forMatch("微信ＡＢＣ１２３"), "全角字母数字压半角");
        assertEquals("qq123456", TextNormalizer.forMatch("ＱＱ１２３４５６"), "全角大写转小写");
    }

    @Test
    public void testKeepsNormalTextAndNullSafe() {
        assertEquals("就是一段普通文字", TextNormalizer.forMatch("就是一段普通文字"), "普通文本不动");
        assertEquals("", TextNormalizer.forMatch(null));
        assertEquals("", TextNormalizer.forMatch(""));
        assertEquals("", TextNormalizer.forMatch("   \u200b "), "全是干扰字符压成空串");
    }
}
