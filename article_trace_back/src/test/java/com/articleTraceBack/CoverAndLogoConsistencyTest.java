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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 封面与图片校验。
 *
 * <p>封面改为「随文章一起提交」后，两条核心约定：</p>
 * <ul>
 *   <li><b>对象名由服务端生成</b>——客户端传来的 coverImg 不可信，只允许它表达「删除封面」，
 *       其余取值一律回退为库中原值，避免文章引用到别人的对象。</li>
 *   <li><b>DB 的指向永不落在已删除的对象上</b>——写库成功后才删旧封面；写库失败则回收新上传的封面。</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class CoverAndLogoConsistencyTest {

    private static final int AUTHOR = 1;
    private static final String USERNAME = "zz-test-cover-user";

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

    private MockMultipartFile coverImage(String name) {
        return new MockMultipartFile("cover", name, "image/png", "fake-image".getBytes());
    }

    @Test
    public void uploadingNewCoverGeneratesServerSideKeyAndDeletesOld() {
        int userId = fixtures.ensureUser(AUTHOR);
        String oldKey = "zz-test-old-cover-" + System.nanoTime() + ".png";
        int id = insertArticle(userId, "zz-test-换封面-" + System.nanoTime(), oldKey);

        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle("zz-test-换封面-" + System.nanoTime());
        edit.setContent("正文内容");
        edit.setState(ArticleService.STATE_DRAFT);
        // 即便客户端塞了一个可疑的 key，有文件时也一律以服务端生成的对象名为准
        edit.setCoverImg("attacker-supplied-key.png");

        assertTrue(articleService.articleAddOrUpdate(edit, 1, coverImage("new.png"), USERNAME));

        Article after = articleMapper.selectById(id);
        assertNotNull(after.getCoverImg());
        assertTrue(after.getCoverImg().endsWith(".png"), "应当是以服务端生成的对象名");
        assertFalse(after.getCoverImg().contains("attacker"), "不能采用客户端提供的 key");
        // 写库成功后旧封面才被删除
        verify(rustFsUtil).delete(oldKey, "image");
    }

    @Test
    public void emptyCoverImgMeansDeleteCover() {
        int userId = fixtures.ensureUser(AUTHOR);
        String oldKey = "zz-test-del-cover-" + System.nanoTime() + ".png";
        int id = insertArticle(userId, "zz-test-删封面-" + System.nanoTime(), oldKey);

        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle("zz-test-删封面-" + System.nanoTime());
        edit.setContent("正文内容");
        edit.setState(ArticleService.STATE_DRAFT);
        edit.setCoverImg("");            // 空串 = 删除封面

        assertTrue(articleService.articleAddOrUpdate(edit, 1, null, USERNAME));

        assertEquals("", articleMapper.selectById(id).getCoverImg(), "封面指向应当被清空");
        verify(rustFsUtil).delete(oldKey, "image");
    }

    @Test
    public void blankCoverImgKeepsExistingCover() {
        int userId = fixtures.ensureUser(AUTHOR);
        String oldKey = "zz-test-keep-cover-" + System.nanoTime() + ".png";
        int id = insertArticle(userId, "zz-test-不换封面-" + System.nanoTime(), oldKey);

        given(rustFsUtil.upload(any(), anyString(), anyString())).willReturn(true);

        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle("zz-test-不换封面-" + System.nanoTime());
        edit.setContent("正文内容");
        edit.setState(ArticleService.STATE_DRAFT);
        edit.setCoverImg(null);          // 没选新封面

        assertTrue(articleService.articleAddOrUpdate(edit, 1, null, USERNAME));

        assertEquals(oldKey, articleMapper.selectById(id).getCoverImg(),
                "未提交新封面时应当保持原封面，且旧对象不能被删");
        verify(rustFsUtil, never()).delete(oldKey, "image");
    }

    @Test
    public void failedSaveReclaimsNewlyUploadedCover() {
        int userId = fixtures.ensureUser(AUTHOR);
        int id = insertArticle(userId, "zz-test-回收封面-" + System.nanoTime(), "");

        // 只让「正文(json)」上传失败、封面(image)成功。
        // 注意不能用两个 willReturn 分别 stub：后定义的会覆盖前一个，导致正文也返回成功。
        given(rustFsUtil.upload(any(), anyString(), anyString()))
                .willAnswer(inv -> !inv.getArgument(2, String.class).endsWith(".json"));
        given(rustFsUtil.delete(anyString(), anyString())).willReturn(true);

        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle("zz-test-回收封面-" + System.nanoTime());
        edit.setContent("正文内容");
        edit.setState(ArticleService.STATE_DRAFT);

        assertFalse(articleService.articleAddOrUpdate(edit, 1, coverImage("x.png"), USERNAME),
                "正文上传失败时整次提交应当失败");

        // 刚上传的封面必须被回收，不留下无引用对象
        verify(rustFsUtil).delete(contains("zz-test-cover-user"), anyString());
    }

    @Test
    public void imageValidationRequiresBothMimeAndExtension() {
        // MIME 正确但扩展名可疑：此前只查 MIME 就放行，最终会以 .jsp 作为对象名存进存储
        MockMultipartFile disguised = new MockMultipartFile(
                "cover", "payload.jsp", "image/png", "x".getBytes());
        assertFalse(FileCheckUtil.isAcceptableImage(disguised),
                "MIME 是图片但扩展名不在白名单，必须拒绝");
        assertTrue(FileCheckUtil.hasImageContentType(disguised), "MIME 本身确实是图片类型");

        MockMultipartFile normal = new MockMultipartFile(
                "cover", "photo.PNG", "image/png", "x".getBytes());
        assertTrue(FileCheckUtil.isAcceptableImage(normal), "大小写不同的扩展名应当接受");

        MockMultipartFile wrongMime = new MockMultipartFile(
                "cover", "photo.png", "application/octet-stream", "x".getBytes());
        assertFalse(FileCheckUtil.isAcceptableImage(wrongMime), "MIME 不符也应当拒绝");

        MockMultipartFile noExt = new MockMultipartFile(
                "cover", "photo", "image/png", "x".getBytes());
        assertFalse(FileCheckUtil.isAcceptableImage(noExt), "没有扩展名无法确定对象名，应当拒绝");
    }

    @Test
    public void coverWithDisallowedExtensionIsRejectedBeforeUpload() {
        int userId = fixtures.ensureUser(AUTHOR);
        int id = insertArticle(userId, "zz-test-非法封面-" + System.nanoTime(), "");

        Article edit = new Article();
        edit.setId(id);
        edit.setCreateUser(userId);
        edit.setTitle("zz-test-非法封面-" + System.nanoTime());
        edit.setContent("正文内容");
        edit.setState(ArticleService.STATE_DRAFT);

        MockMultipartFile bad = new MockMultipartFile(
                "cover", "evil.jsp", "image/png", "x".getBytes());

        assertFalse(articleService.articleAddOrUpdate(edit, 1, bad, USERNAME),
                "扩展名不在白名单时应当直接失败");
        verify(rustFsUtil, never()).upload(any(), anyString(), anyString());
        String cover = articleMapper.selectById(id).getCoverImg();
        assertTrue(cover == null || cover.isEmpty(),
                "封面被拒后不应写入任何指向，实际：" + cover);
    }
}
