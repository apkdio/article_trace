package com.articleTraceBack.Utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传校验的魔数与归一化（bugs_report P1-10）：MIME / 扩展名 / 魔数三者都要过，
 * 客户端声明与内容不符的文件（evil.svg 改名 .gif）必须被拒。
 *
 * <pre>mvn test -Dtest=FileCheckUtilTest</pre>
 */
public class FileCheckUtilTest {

    private static final byte[] PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final byte[] GIF = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    /** SVG 文本（伪装成 image/gif + .gif 扩展名） */
    private static final byte[] SVG = "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(StandardCharsets.UTF_8);

    @Test
    public void realPngWithDeclaredTypePasses() {
        MockMultipartFile file = new MockMultipartFile("cover", "a.png", "image/png", PNG);
        assertTrue(FileCheckUtil.isAcceptableImage(file), "真 PNG 应通过");
    }

    @Test
    public void contentTypeParametersAreTolerated() {
        MockMultipartFile file = new MockMultipartFile("cover", "a.png", "image/png;charset=utf-8", PNG);
        assertTrue(FileCheckUtil.isAcceptableImage(file), "带参数的 MIME 应按类型归一化后通过");
    }

    @Test
    public void svgRenamedToGifIsRejected() {
        MockMultipartFile file = new MockMultipartFile("cover", "evil.gif", "image/gif", SVG);
        assertFalse(FileCheckUtil.isAcceptableImage(file), "SVG 内容伪装 gif 必须被魔数校验拦下");
    }

    @Test
    public void jpegContentWithPngNameIsRejected() {
        MockMultipartFile file = new MockMultipartFile("cover", "a.png", "image/png", JPEG);
        assertFalse(FileCheckUtil.isAcceptableImage(file), "声明与内容不一致应被拒");
    }

    @Test
    public void normalizeStripsParametersAndUnknownTypeIsNull() throws Exception {
        MockMultipartFile withParams = new MockMultipartFile("c", "a.png", "image/png; charset=x", PNG);
        assertEquals("image/png", FileCheckUtil.normalizeContentType(withParams));
        MockMultipartFile weird = new MockMultipartFile("c", "a.png", "application/octet-stream", PNG);
        assertEquals(null, FileCheckUtil.normalizeContentType(weird));
    }

    @Test
    public void magicMatchHandlesShortInput() throws Exception {
        InputStream twoBytes = new ByteArrayInputStream(new byte[]{1, 2});
        assertFalse(FileCheckUtil.magicMatches("image/png", twoBytes), "短于魔数长度的输入应被拒");
    }
}
