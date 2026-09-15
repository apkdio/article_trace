package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Utils.FileCheckUtil;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 封面与头像的对象存储一致性。
 *
 * <p>核心约定：**DB 的指向永远不落在已删除的对象上**。因此所有"删除对象"都必须排在
 * "DB 不再引用它"之后。反过来写（先删对象再改 DB）一旦中间失败，DB 就会指向不存在的对象，
 * 表现为封面/头像裂图，且用户自己恢复不了。</p>
 *
 * <p>另一条约定：扩展名决定最终对象名，必须走白名单——MIME 是客户端可伪造的。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class CoverAndLogoConsistencyTest {

    private static final int AUTHOR = 1;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @MockitoBean
    private RustFsUtil rustFsUtil;

    private int insertArticle(int userId, String title, String coverKey) {
        Article a = new Article();
        a.setTitle(title);
        a.setContent("zz-test-content.json");
        a.setState(ArticleService.STATE_DRAFT);
        a.setCreateUser(userId);
        a.setCreateTime(LocalDateTime.now());
        a.setCoverImg(coverKey);
        articleMapper.insert(a);
        return a.getId();
    }

    @Test
    public void removeCoverClearsDbPointerAndDeletesObject() {
        int userId = fixtures.ensureUser(AUTHOR);
        String coverKey = "zz-test-cover-" + System.nanoTime() + ".png";
        int id = insertArticle(userId, "zz-test-封面文章-" + System.nanoTime(), coverKey);

        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        assertTrue(articleService.removeCover(coverKey, userId), "自己的封面应当允许删除");

        assertEquals("", articleMapper.selectById(id).getCoverImg(),
                "DB 的封面指向必须被清空，否则会指向已删除的对象");
        verify(rustFsUtil).delete(coverKey, "image");
    }

    @Test
    public void removeCoverRejectsOthersKey() {
        int owner = fixtures.ensureUser(AUTHOR);
        int other = fixtures.ensureUser(AUTHOR);
        String coverKey = "zz-test-cover2-" + System.nanoTime() + ".png";
        int id = insertArticle(owner, "zz-test-他人封面-" + System.nanoTime(), coverKey);

        assertFalse(articleService.removeCover(coverKey, other),
                "不能删除别人的封面对象");

        // 别人的东西一个字节都没动
        assertEquals(coverKey, articleMapper.selectById(id).getCoverImg());
        verify(rustFsUtil, never()).delete(anyString(), anyString());
    }

    @Test
    public void imageValidationRequiresBothMimeAndExtension() {
        // MIME 正确但扩展名可疑：此前只查 MIME 就放行，最终会以 .jsp 作为对象名存进存储
        MockMultipartFile disguised = new MockMultipartFile(
                "cover", "payload.jsp", "image/png", "x".getBytes());
        assertFalse(FileCheckUtil.isAcceptableImage(disguised),
                "MIME 是图片但扩展名不在白名单，必须拒绝");
        assertTrue(FileCheckUtil.hasImageContentType(disguised),
                "MIME 本身确实是图片类型");

        // 正常图片通过
        MockMultipartFile normal = new MockMultipartFile(
                "cover", "photo.PNG", "image/png", "x".getBytes());
        assertTrue(FileCheckUtil.isAcceptableImage(normal), "大小写不同的扩展名应当接受");

        // 扩展名对但 MIME 不是图片
        MockMultipartFile wrongMime = new MockMultipartFile(
                "cover", "photo.png", "application/octet-stream", "x".getBytes());
        assertFalse(FileCheckUtil.isAcceptableImage(wrongMime), "MIME 不符也应当拒绝");

        // 无扩展名
        MockMultipartFile noExt = new MockMultipartFile(
                "cover", "photo", "image/png", "x".getBytes());
        assertFalse(FileCheckUtil.isAcceptableImage(noExt), "没有扩展名无法确定对象名，应当拒绝");
    }

    @Test
    public void normalImagePassesExtensionWhitelist() {
        for (String ext : new String[]{".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"}) {
            MockMultipartFile f = new MockMultipartFile(
                    "cover", "a" + ext, "image/jpeg", "x".getBytes());
            assertTrue(FileCheckUtil.isAcceptableImage(f), ext + " 应当被接受");
            assertEquals(ext, FileCheckUtil.extensionOf(f));
        }
    }
}
