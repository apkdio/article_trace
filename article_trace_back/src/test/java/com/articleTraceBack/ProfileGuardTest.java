package com.articleTraceBack;

import com.articleTraceBack.Service.ProfileGuard;
import com.articleTraceBack.config.SensitiveWordHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 昵称 / 个签规则：格式类直接拒、内容类进待审，以及各种绕写写法。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ProfileGuardTest {

    private static final String WORD = "zztest违禁词甲";
    private static final Path WORD_FILE;

    static {
        try {
            WORD_FILE = Files.createTempFile("zz-test-profile-words", ".txt");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void wordFileProperty(DynamicPropertyRegistry registry) {
        registry.add("sensitive_word.filePath", WORD_FILE::toString);
    }

    @Autowired
    private ProfileGuard guard;

    @Autowired
    private SensitiveWordHolder holder;

    @BeforeEach
    public void prepareWords() throws IOException {
        Files.writeString(WORD_FILE, WORD + "\n", StandardCharsets.UTF_8);
        assertTrue(holder.reload(), "重载应当成功");
    }

    private void assertPass(ProfileGuard.Verdict verdict, String message) {
        assertEquals(ProfileGuard.Verdict.Kind.PASS, verdict.kind(), message);
    }

    @Test
    public void normalNicknamesPass() {
        assertPass(guard.checkNickname("小张"), "中文昵称");
        assertPass(guard.checkNickname("zz_author-01"), "字母数字下划线连字符");
        assertPass(guard.checkNickname("李 雷"), "中间允许空格");
        assertPass(guard.checkNickname("李　雷"), "中间允许全角空格");
        assertPass(guard.checkNickname("阿伟·张"), "允许中点");
    }

    @Test
    public void formatProblemsAreRejectedDirectly() {
        assertEquals(ProfileGuard.Verdict.Kind.FORMAT, guard.checkNickname("a").kind(), "长度不足");
        assertEquals(ProfileGuard.Verdict.Kind.FORMAT, guard.checkNickname("超".repeat(21)).kind(), "超过 20 字");
        assertEquals(ProfileGuard.Verdict.Kind.FORMAT, guard.checkNickname(" 张三").kind(), "首尾空格");
        assertEquals(ProfileGuard.Verdict.Kind.FORMAT, guard.checkNickname("张😀三").kind(), "emoji 不在白名单");
        assertEquals(ProfileGuard.Verdict.Kind.FORMAT, guard.checkNickname("微信:13812345678").kind(),
                "冒号不在白名单：格式类先判，不给无效输入排审核队列");
        assertEquals(ProfileGuard.Verdict.Kind.FORMAT, guard.checkNickname("张\u200b三").kind(),
                "零宽字符在原文里就应当被拒，不能靠归一化抹掉后放行");
    }

    @Test
    public void contactsAreContentHits() {
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("微信abc123").kind(), "微信 + 任意后缀");
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("微 信13812345678").kind(), "中间插空格也要命中");
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("ＱＱ１２３４５６").kind(), "全角 QQ");
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("加v138-1234-5678").kind(), "加 v");
        // 邮箱里的 @ 不在字符集白名单内，格式类先判就挡住了——目的（昵称里塞不进邮箱）已达到
        assertNotEquals(ProfileGuard.Verdict.Kind.PASS, guard.checkNickname("abc@example.com").kind(), "邮箱");
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("看我主页 www.spam.cn").kind(), "链接");
    }

    @Test
    public void impersonationIsContentHit() {
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("文迹站长").kind(), "直接写站长");
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("官方客服").kind());
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("站 长").kind(),
                "插空格也应当命中（归一化后同形）");
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("文迹").kind(), "站名本身（site.display-name）");
    }

    @Test
    public void sensitiveWordsAreContentHits() {
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkNickname("我是" + WORD).kind());
        assertEquals(ProfileGuard.Verdict.Kind.CONTENT, guard.checkSignature("签名里带" + WORD + "也不行").kind());
    }

    @Test
    public void signatureRulesDifferFromNickname() {
        assertPass(guard.checkSignature(""), "清空个签合法");
        assertPass(guard.checkSignature("   "), "全空白等同清空");
        assertPass(guard.checkSignature("爱写代码的人"), "正常个签");
        assertPass(guard.checkSignature("官方客服的小号"), "个签不查冒充——这是正常表达");
        assertEquals(ProfileGuard.Verdict.Kind.FORMAT, guard.checkSignature("长".repeat(31)).kind(), "个签上限 30");
    }
}
