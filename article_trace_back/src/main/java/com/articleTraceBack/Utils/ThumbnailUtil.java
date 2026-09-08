package com.articleTraceBack.Utils;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 图片缩略图工具：按宽度等比缩放，输出 JPEG。
 */
public class ThumbnailUtil {

    private ThumbnailUtil() {
    }

    /**
     * 生成缩略图字节数组。
     *
     * @param inputStream 原图输入流
     * @param width       目标宽度（像素），等比缩放
     * @return 缩略图字节数组；生成失败返回 null
     */
    public static byte[] thumbnail(InputStream inputStream, int width) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(inputStream)
                    .width(width)
                    .outputFormat("jpg")
                    .outputQuality(0.8)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (Exception e) {
            System.out.println("缩略图生成失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 生成缩略图字节数组（从 MultipartFile）。
     */
    public static byte[] thumbnail(MultipartFile file, int width) {
        try {
            return thumbnail(file.getInputStream(), width);
        } catch (Exception e) {
            System.out.println("缩略图生成失败: " + e.getMessage());
            return null;
        }
    }
}
