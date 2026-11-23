package com.articleTraceBack.Utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正文图片限额：正文里的图片是 base64 内联，体积会直接进保存请求（multipart 上限 10MB）。
 * 口径与前端 {@code utils/bodyImages.js} 保持一致，改数字时两边一起改。
 */
public final class BodyImageLimits {

    /** 单张图片的 base64 上限（解码后约 300KB） */
    public static final int MAX_SINGLE_BASE64 = 400 * 1024;
    /** 单篇正文所有图片的 base64 总量上限 */
    public static final long MAX_TOTAL_BASE64 = 5L * 1024 * 1024;
    /** 单篇正文的图片张数上限 */
    public static final int MAX_COUNT = 15;

    /** 只统计位图 data: 图片：外链不计，SVG 会在正文清洗时被剔掉 */
    private static final Pattern RASTER_DATA_IMAGE = Pattern.compile(
            "<img\\b[^>]*\\bsrc\\s*=\\s*[\"']data:image/(?:png|jpe?g|gif|webp|bmp);base64,([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);

    private BodyImageLimits() {
    }

    /**
     * 校验正文图片，返回第一条超限提示；未超限返回 {@code null}。
     * 必须在正文清洗之后调用：清洗会剔除非位图 data:，那部分不该按图片计费。
     */
    public static String check(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher matcher = RASTER_DATA_IMAGE.matcher(html);
        int count = 0;
        long total = 0;
        while (matcher.find()) {
            count++;
            int size = matcher.group(1).length();
            if (size > MAX_SINGLE_BASE64) {
                return "单张图片不能超过 " + (MAX_SINGLE_BASE64 / 1024) + "KB";
            }
            if (count > MAX_COUNT) {
                return "正文最多 " + MAX_COUNT + " 张图片";
            }
            total += size;
            if (total > MAX_TOTAL_BASE64) {
                return "正文图片总量不能超过 " + (MAX_TOTAL_BASE64 / 1024 / 1024) + "MB";
            }
        }
        return null;
    }
}
