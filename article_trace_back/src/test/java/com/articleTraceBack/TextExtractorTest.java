package com.articleTraceBack;

import com.articleTraceBack.Utils.TextExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 摘要提取：去标签、去公式段、按长度截断。
 *
 * <pre>mvn test -Dtest=TextExtractorTest</pre>
 */
public class TextExtractorTest {

    @Test
    public void testStripsTagsAndKeepsText() {
        assertEquals("你好 世界", TextExtractor.extractor("<p>你好</p><p><strong>世界</strong></p>", 100),
                "标签去掉、段落间留一个空格");
    }

    @Test
    public void testDropsMathSegments() {
        assertEquals("解得 结束", TextExtractor.extractor("<p>解得 \\(x=2\\) 结束</p>", 100),
                "行内公式 \\\\(..\\\\) 去掉");
        assertEquals("推导如下", TextExtractor.extractor("<p>推导如下 $$\\int_0^1 x\\,dx$$</p>", 100),
                "块级公式 $$..$$ 去掉");
        assertEquals("块级", TextExtractor.extractor("<p>块级 \\[a+b\\]</p>", 100),
                "块级公式 \\\\[..\\\\] 去掉");
    }

    @Test
    public void testKeepsCurrencyLikeDollarText() {
        assertEquals("价格 $5 到 $10", TextExtractor.extractor("<p>价格 $5 到 $10</p>", 100),
                "单独的 $..$ 不是公式（与展示端同口径），必须原样保留");
    }

    @Test
    public void testTruncates() {
        assertEquals("abcdef", TextExtractor.extractor("<p>abcdefghij</p>", 6), "超长按字符数截断");
        assertEquals("", TextExtractor.extractor(null, 10), "空输入返回空串");
        assertEquals("", TextExtractor.extractor("<p>abc</p>", 0), "maxLength 非正数返回空串");
    }
}
