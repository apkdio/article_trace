package com.articleTraceBack;

import com.articleTraceBack.Service.ArticleService;
import com.articleTraceBack.Utils.RustFsUtil;
import com.articleTraceBack.mapper.ArticleMapper;
import com.articleTraceBack.pojo.Article;
import com.articleTraceBack.rpc.ArticleAgentClient;
import com.articleTraceBack.rpc.gen.BatchIngestReply;
import com.articleTraceBack.rpc.gen.ListIdsReply;
import com.articleTraceBack.scheduledTask.AgentSyncTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 全量对账的幽灵条目清理（P1-7 后半，依赖 agent 侧新增的 ListIds RPC）：
 * 索引里存在、但库里已不是「已发布」的 id 必须被删掉，否则已删除/下架的文章会长期留在知识库被问答命中。
 *
 * <pre>mvn test -Dtest=AgentGhostCleanupTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        // 测试配置默认把 agent 全关（不碰真服务）；本用例要驱动对账任务，显式打开两个开关
        properties = {"rpc.agent.enabled=true", "rpc.agent.sync.enabled=true"})
public class AgentGhostCleanupTest {

    @Autowired
    private AgentSyncTask agentSyncTask;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TestFixtures fixtures;

    @MockitoBean
    private ArticleAgentClient agentClient;

    @MockitoBean
    private RustFsUtil rustFsUtil;

    private int insertArticle(int userId, int state) {
        Article a = new Article();
        a.setTitle("zz-test-ghost-" + System.nanoTime());
        a.setContent("zz-test-ghost-content.json");
        a.setState(state);
        a.setCreateUser(userId);
        a.setCreateTime(LocalDateTime.now());
        a.setCoverImg("");
        articleMapper.insert(a);
        return a.getId();
    }

    @Test
    public void fullSyncDeletesIndexedIdsNoLongerPublished() {
        int userId = fixtures.ensureUser(1);
        int publishedId = insertArticle(userId, ArticleService.STATE_PUBLISHED);
        int draftId = insertArticle(userId, ArticleService.STATE_DRAFT);
        long unknownId = 987654321L;

        given(agentClient.batchIngestArticles(any()))
                .willReturn(BatchIngestReply.newBuilder().setOk(true).build());
        // agent 侧索引里：已发布的一篇 + 草稿一篇（幽灵）+ 库里根本没有的 id（幽灵）
        given(agentClient.listIds()).willReturn(ListIdsReply.newBuilder().setOk(true)
                .addAllArticleIds(List.of((long) publishedId, (long) draftId, unknownId)).build());
        given(agentClient.deleteArticles(any())).willReturn(true);
        given(rustFsUtil.getContent(anyString())).willReturn("<p>正文</p>");

        agentSyncTask.fullSync();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(agentClient).deleteArticles(captor.capture());
        assertEquals(Set.of((long) draftId, unknownId), Set.copyOf(captor.getValue()),
                "只应删除「库里已不是已发布」的索引条目，已发布的那篇不能误删");
    }
}
