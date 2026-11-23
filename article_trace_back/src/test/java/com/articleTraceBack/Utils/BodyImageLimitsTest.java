package com.articleTraceBack.Utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 正文图片限额（阶段 0）：只统计位图 data: 图片，单张 / 总量 / 张数三个上限分别兜底。
 *
 * <pre>mvn test -Dtest=BodyImageLimitsTest</pre>
 */
public class BodyImageLimitsTest {

    private static String dataImage(String mime, int base64Length) {
        return "data:" + mime + ";base64," + "A".repeat(base64Length);
    }

    private static String img(String src) {
        return "<p>正文</p><img src=\"" + src + "\">";
    }

    @Test
    public void plainContentPasses() {
        assertNull(BodyImageLimits.check(null));
        assertNull(BodyImageLimits.check(""));
        assertNull(BodyImageLimits.check("<p>只有文字，没有图片</p>"));
    }

    @Test
    public void singleImageAtLimitPasses() {
        assertNull(BodyImageLimits.check(img(dataImage("image/png", BodyImageLimits.MAX_SINGLE_BASE64))));
    }

    @Test
    public void singleImageOverLimitIsRejected() {
        assertEquals("单张图片不能超过 400KB",
                BodyImageLimits.check(img(dataImage("image/jpeg", BodyImageLimits.MAX_SINGLE_BASE64 + 1))));
    }

    @Test
    public void imagesUpToCountLimitPass() {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < BodyImageLimits.MAX_COUNT; i++) {
            html.append(img(dataImage("image/png", 1024)));
        }
        assertNull(BodyImageLimits.check(html.toString()));
    }

    @Test
    public void oneImageOverCountLimitIsRejected() {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < BodyImageLimits.MAX_COUNT + 1; i++) {
            html.append(img(dataImage("image/png", 1024)));
        }
        assertEquals("正文最多 15 张图片", BodyImageLimits.check(html.toString()));
    }

    @Test
    public void totalOverLimitIsRejected() {
        // 13 张各 400KB：单张不超、张数不超，只有总量超
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            html.append(img(dataImage("image/png", BodyImageLimits.MAX_SINGLE_BASE64)));
        }
        assertEquals("正文图片总量不能超过 5MB", BodyImageLimits.check(html.toString()));
    }

    @Test
    public void externalAndSvgImagesAreIgnored() {
        String html = "<img src=\"https://example.com/a.png\">"
                + "<img src=\"data:image/svg+xml;base64,AAAA\">"
                + img(dataImage("image/webp", 1024));
        assertNull(BodyImageLimits.check(html));
    }

    @Test
    public void singleQuotesAndUppercaseMimeAreCounted() {
        String html = "<img src='" + dataImage("IMAGE/PNG", BodyImageLimits.MAX_SINGLE_BASE64 + 1) + "'>";
        assertEquals("单张图片不能超过 400KB", BodyImageLimits.check(html));
    }
}
