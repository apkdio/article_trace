package com.articleTraceBack.Utils;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

/**
 * 上传文件的基础校验。
 *
 * <p>此前封面与头像各有一套判断，且都有漏洞：只匹配 contentType 就直接放行、不再看扩展名，
 * 而 contentType 是客户端可伪造的——文件名写成 {@code x.jsp} 配 {@code image/png} 照样能过，
 * 最终以 .jsp 作为对象名存进存储。这里统一为「两者都要满足」，两个上传接口共用同一份判断。</p>
 */
public final class FileCheckUtil {

    /** 允许的图片扩展名（小写，含点） */
    private static final List<String> IMAGE_EXTENSIONS =
            List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");

    /** 允许的图片 MIME 前缀 */
    private static final List<String> IMAGE_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");

    private FileCheckUtil() {
    }

    /**
     * 是否为可接受的图片文件：**MIME 与扩展名都要通过**。
     *
     * <p>只查其一都不够——MIME 可伪造，扩展名决定最终落库的对象名。</p>
     */
    public static boolean isAcceptableImage(MultipartFile file) {
        return file != null && hasImageContentType(file) && hasImageExtension(file);
    }

    /** contentType 是否属于图片类型 */
    public static boolean hasImageContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return IMAGE_CONTENT_TYPES.stream().anyMatch(lower::startsWith);
    }

    /** 原始文件名是否带允许的图片扩展名 */
    public static boolean hasImageExtension(MultipartFile file) {
        String ext = extensionOf(file);
        return ext != null && IMAGE_EXTENSIONS.contains(ext);
    }

    /**
     * 取原始文件名的小写扩展名（含点）。
     *
     * @return 形如 {@code ".png"}；无文件名或无扩展名时返回 {@code null}
     */
    public static String extensionOf(MultipartFile file) {
        if (file == null) {
            return null;
        }
        String original = file.getOriginalFilename();
        if (original == null) {
            return null;
        }
        int dot = original.lastIndexOf('.');
        if (dot < 0 || dot == original.length() - 1) {
            return null;
        }
        // 只取扩展名部分，避免把路径里的点也带进来
        String ext = original.substring(dot).toLowerCase(Locale.ROOT);
        return ext.contains("/") || ext.contains("\\") ? null : ext;
    }
}
