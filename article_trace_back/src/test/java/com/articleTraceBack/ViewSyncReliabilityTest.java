package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.scheduledTask.SyncRedisToDbTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Redis 浏览量增量同步的可靠性。
 *
 * <p>原实现用 {@code RENAME} 把增量键搬到 {@code :processing}，而 {@code RENAME} 会**覆盖**
 * 已存在的目标键——上一轮消费失败留下的残留，下一轮直接被冲掉，这批浏览量永久丢失。
 * 若期间没有新浏览（原键不存在），残留更会永远无人处理。</p>
 *
 * <p>现在改为原子地把新增量**累加**进 {@code :processing}，消费成功后才删；
 * 失败则原样保留，下一轮连同新数据一起消费（增量累加天然幂等）。</p>
 *
 * <p><b>测试方式</b>：手动构造 task 实例并注入 mock 的 mapper。若用 {@code @MockitoBean}
 * 替换 Spring 上下文里的 mapper，本用例就没有可用的真 mapper 去建测试文章了
 * （那样 {@code insert} 也是空操作，拿不到自增 id）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class ViewSyncReliabilityTest {

    @Autowired
    @Qualifier("stringRedisTemplateArticle")
    private StringRedisTemplate redis;

    /** 真 mapper：只用来造测试文章 */
    @Autowired
    private ArticleMapper realArticleMapper;

    @Autowired
    private TestFixtures fixtures;

    @Value("${spring.data.redis.viewKey}")
    private String viewKey;

    /** 被测任务：用 mock mapper 以便断言"提交了哪些增量"并模拟写库失败 */
    private SyncRedisToDbTask syncTask;
    private ArticleMapper mockMapper;

    private String processingKey;

    @BeforeEach
    public void setUp() {
        processingKey = viewKey + ":processing";
        redis.delete(viewKey);
        redis.delete(processingKey);

        mockMapper = mock(ArticleMapper.class);
        syncTask = new SyncRedisToDbTask(redis, mockMapper);
        // @Value 字段在手动构造的实例上不会自动注入
        ReflectionTestUtils.setField(syncTask, "enabled", true);
        ReflectionTestUtils.setField(syncTask, "viewKey", viewKey);
    }

    @AfterEach
    public void tearDown() {
        redis.delete(viewKey);
        redis.delete(processingKey);
    }

    private int newArticle() {
        int userId = fixtures.ensureUser(1);
        Article a = new Article();
        a.setTitle("zz-test-view-" + System.nanoTime());
        a.setContent("zz-test-content.json");
        a.setState(ArticleService.STATE_DRAFT);
        a.setCreateUser(userId);
        a.setCreateTime(LocalDateTime.now());
        a.setCoverImg("");
        realArticleMapper.insert(a);
        return a.getId();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Long> captureDeltas() {
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockMapper).batchAddViews(captor.capture());
        return captor.getValue();
    }

    @Test
    public void leftoverIsNotOverwrittenAndBothAreCounted() {
        int id = newArticle();

        // 模拟上一轮消费失败留下的残留
        redis.opsForHash().put(processingKey, String.valueOf(id), "5");
        // 本轮新累积的增量
        redis.opsForHash().put(viewKey, String.valueOf(id), "3");

        syncTask.syncIncrementalViews();

        Map<Long, Long> deltas = captureDeltas();
        assertEquals(8L, deltas.get((long) id),
                "残留的 5 与新增的 3 都应被计入——旧实现 rename 会把残留覆盖掉，只剩 3");

        assertFalse(Boolean.TRUE.equals(redis.hasKey(viewKey)), "增量键应当被清空");
        assertFalse(Boolean.TRUE.equals(redis.hasKey(processingKey)), "消费成功后 processing 应当删除");
    }

    @Test
    public void leftoverIsConsumedEvenWithoutNewIncrements() {
        int id = newArticle();

        // 只有残留、没有新数据：旧实现在 hasKey(viewKey)=false 时直接 return，残留永远无人处理
        redis.opsForHash().put(processingKey, String.valueOf(id), "7");

        syncTask.syncIncrementalViews();

        Map<Long, Long> deltas = captureDeltas();
        assertEquals(7L, deltas.get((long) id), "没有新数据时残留仍应被消费");
        assertFalse(Boolean.TRUE.equals(redis.hasKey(processingKey)));
    }

    @Test
    public void failedWriteKeepsPendingDataForNextRound() {
        int id = newArticle();
        redis.opsForHash().put(viewKey, String.valueOf(id), "4");

        // 让第一轮批量写库失败，之后的调用恢复正常
        // （doThrow 会持续生效，必须接 doNothing 才能模拟「下一轮恢复」）
        doThrow(new RuntimeException("db down")).doNothing().when(mockMapper).batchAddViews(any());

        syncTask.syncIncrementalViews();

        // 失败时数据必须留在 processing，下一轮才能补上
        assertTrue(Boolean.TRUE.equals(redis.hasKey(processingKey)),
                "写库失败时增量不能丢，应保留在 processing 等待下一轮");
        assertEquals("4", redis.opsForHash().get(processingKey, String.valueOf(id)));

        // 下一轮恢复后应能补上（batchAddViews 是 void，mock 默认不抛即视为成功）
        syncTask.syncIncrementalViews();

        // 两次调用都要验：第一轮虽然抛了异常，但 invocation 已被记录，
        // 用默认的 verify(...)（期望 1 次）会报 TooManyActualInvocations。
        // 取最后一次的入参，即"补上"那一轮提交的增量。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockMapper, times(2)).batchAddViews(captor.capture());
        assertEquals(4L, captor.getValue().get((long) id), "下一轮应当把上一次失败的数据补上");
    }

    @Test
    public void completesQuietlyWhenNothingPending() {
        syncTask.syncIncrementalViews();

        verify(mockMapper, never()).batchAddViews(any());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(viewKey)));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(processingKey)));
    }

    @Test
    public void mergesIncrementallyAcrossMultipleRounds() {
        int id = newArticle();

        redis.opsForHash().put(viewKey, String.valueOf(id), "2");
        syncTask.syncIncrementalViews();

        redis.opsForHash().put(viewKey, String.valueOf(id), "6");
        syncTask.syncIncrementalViews();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockMapper, times(2)).batchAddViews(captor.capture());
        assertEquals(2L, captor.getAllValues().get(0).get((long) id));
        assertEquals(6L, captor.getAllValues().get(1).get((long) id),
                "第二轮只提交本轮新增量，不与上一轮叠加——数据库侧是累加语义");
    }
}
