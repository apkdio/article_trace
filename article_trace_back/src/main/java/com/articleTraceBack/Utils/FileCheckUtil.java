package com.articleTraceBack.Utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** 上传文件的基础校验：MIME、扩展名、魔数三者必须同时满足。contentType 与扩展名都可被客户端伪造，只查其一会让 {@code evil.svg} 改名 {@code .gif} 蒙混过关。 */
public final class FileCheckUtil {

    /** 允许的图片扩展名（小写，含点） */
    private static final List<String> IMAGE_EXTENSIONS =
            List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");

    /** 允许的图片 MIME 前缀 */
    private static final List<String> IMAGE_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");

    private FileCheckUtil() {
    }

    /** 是否为可接受的图片文件：MIME、扩展名与内容魔数都要通过（三者都可伪造，缺一不可）。 */
    public static boolean isAcceptableImage(MultipartFile file) {
        return file != null && hasImageContentType(file) && hasImageExtension(file) && hasImageMagic(file);
    }

    /**
     * 内容魔数是否与声明的图片类型一致。
     *
     * @param contentType 归一化后的 MIME（不带参数，如 {@code image/png}）
     */
    static boolean magicMatches(String contentType, InputStream in) throws IOException {
        byte[] head = new byte[12];
        int read = in.readNBytes(head, 0, head.length);
        if (read < 3) {
            return false;
        }
        return switch (contentType) {
            // PNG: 89 50 4E 47
            case "image/png" -> (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
            // JPEG: FF D8 FF
            case "image/jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            // GIF: GIF87a / GIF89a
            case "image/gif" -> head[0] == 0x47 && head[1] == 0x49 && head[2] == 0x46;
            // RIFF....WEBP
            case "image/webp" -> read >= 12 && head[0] == 0x52 && head[1] == 0x49
                    && head[2] == 0x46 && head[3] == 0x46 && head[8] == 0x57 && head[9] == 0x45;
            // BM
            case "image/bmp" -> head[0] == 0x42 && head[1] == 0x4D;
            default -> false;
        };
    }

    /** 抛出型魔数校验：MultipartFile 的流只能读一次，这里在校验时完整旁路读出，校验后由调用方用字节重建流 */
    static boolean hasImageMagic(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return magicMatches(normalizeContentType(file), in);
        } catch (IOException e) {
            return false;
        }
    }

    /** 客户端 Content-Type 归一化：去参数、压小写；不属于白名单类型时返回 null */
    static String normalizeContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return null;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        int semi = normalized.indexOf(';');
        if (semi >= 0) {
            normalized = normalized.substring(0, semi);
        }
        normalized = normalized.trim();
        return IMAGE_CONTENT_TYPES.contains(normalized) ? normalized : null;
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
