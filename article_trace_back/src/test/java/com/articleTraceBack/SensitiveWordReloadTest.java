package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.config.SensitiveWordHolder;
import com.articleTraceBack.scheduledTask.SyncSensitiveWordLoader;
import org.junit.jupiter.api.AfterAll;
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
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词热更新回归测试。
 *
 * <p><b>此前的问题</b>：定时任务每 30 分钟重载词表、构建新匹配器，写进自己类的字段；
 * 而业务读的是另一个由 {@code SensitiveWordConfig} 注入的 bean。两者不相干，
 * 「热更新」从未生效——改词表必须重启，日志却一路打印 reload complete。</p>
 *
 * <p>这里断言的是**业务侧能感知重载**（{@code containsSensitive} 的结果会变），
 * 而不只是 holder 内部换了实例——后者即使成立，业务用不上也等于没修。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SensitiveWordReloadTest {

    private static final String WORD_A = "zztest违禁词甲";
    private static final String WORD_B = "zztest违禁词乙";

    private static final Path WORD_FILE;

    static {
        try {
            WORD_FILE = Files.createTempFile("zz-test-sensitive-words", ".txt");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 词表路径指向临时文件，不动项目里的真实词表 */
    @DynamicPropertySource
    static void wordFileProperty(DynamicPropertyRegistry registry) {
        registry.add("sensitive_word.filePath", WORD_FILE::toString);
    }

    @Autowired
    private ArticleService articleService;

    @Autowired
    private SensitiveWordHolder holder;

    @Autowired
    private SyncSensitiveWordLoader loader;

    @BeforeEach
    public void prepareWords() throws IOException {
        Files.writeString(WORD_FILE, WORD_A + "\n", StandardCharsets.UTF_8);
        assertTrue(holder.reload(), "重载应当成功");
    }

    @AfterAll
    public static void cleanup() throws IOException {
        Files.deleteIfExists(WORD_FILE);
    }

    @Test
    public void reloadedWordsTakeEffectImmediately() throws IOException {
        // 初始词表只含 A
        assertFalse(articleService.containsSensitive("这段话包含" + WORD_A).isEmpty(),
                "初始词表里的词应当被识别");
        assertTrue(articleService.containsSensitive("这段话包含" + WORD_B).isEmpty(),
                "尚未加入词表的词不该被识别");

        // 词表新增 B 后重载
        Files.writeString(WORD_FILE, WORD_A + "\n" + WORD_B + "\n", StandardCharsets.UTF_8);
        assertTrue(holder.reload(), "重载应当成功");

        // 关键断言：业务侧立即感知新词，无需重启
        assertFalse(articleService.containsSensitive("这段话包含" + WORD_B).isEmpty(),
                "重载后新词应当立即生效——这正是此前失效的地方");
        assertFalse(articleService.containsSensitive("这段话包含" + WORD_A).isEmpty(),
                "重载不应丢掉原有词");
    }

    @Test
    public void scheduledTaskPropagatesFileChangeToBusiness() throws IOException {
        // 让 loader 先记录当前时间戳
        loader.checkAndReload();

        // 新增词并把修改时间推到未来，确保晚于 loader 记录的时间戳
        // （部分文件系统时间戳精度低，同一秒内的两次写入可能无法区分）
        Files.writeString(WORD_FILE, WORD_A + "\n" + WORD_B + "\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(WORD_FILE, FileTime.fromMillis(System.currentTimeMillis() + 2000));

        loader.checkAndReload();

        assertFalse(articleService.containsSensitive("这段话包含" + WORD_B).isEmpty(),
                "定时任务检测到词表变更后，业务侧应当立即用上新词");
    }

    @Test
    public void blankAndMissingFileDoNotBreakMatching() throws IOException {
        // 空词表：不应当抛异常，只是匹配不到任何词
        Files.writeString(WORD_FILE, "\n\n   \n", StandardCharsets.UTF_8);
        assertTrue(holder.reload(), "空词表也应当算加载成功");
        assertTrue(articleService.containsSensitive("任意内容都不该命中").isEmpty(),
                "空词表下不应命中任何词");

        // 恢复词表，并确认能力回来了（证明空词表不是把 holder 弄坏了）
        Files.writeString(WORD_FILE, WORD_A + "\n", StandardCharsets.UTF_8);
        assertTrue(holder.reload());
        assertFalse(articleService.containsSensitive(WORD_A).isEmpty(),
                "恢复词表后匹配能力应当回来");
    }
}
