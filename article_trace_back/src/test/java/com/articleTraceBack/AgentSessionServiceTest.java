package com.articleTraceBack;

import com.articleTraceBack.Service.AgentSessionService;
import com.articleTraceBack.rpc.ArticleAgentClient;
import com.articleTraceBack.rpc.gen.AskRequest;
import com.articleTraceBack.rpc.gen.AskStreamChunk;
import com.articleTraceBack.rpc.gen.ListSessionsReply;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话索引服务集成测试。
 *
 * <p>验证两件事：</p>
 * <ol>
 *   <li>Spring 上下文能装配 {@link AgentSessionService}（它同时被 AgentController 与
 *       UserServiceImpl 依赖，若出现循环依赖此处会失败）；</li>
 *   <li>{@code clearAll} 会联动删除 agent 侧会话数据与 Redis 索引。</li>
 * </ol>
 *
 * <p>运行前提：MySQL / Redis / article_trace_agent 均已启动；agent 未启动时自动跳过。</p>
 * <pre>mvn test -Dtest=AgentSessionServiceTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class AgentSessionServiceTest {

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private ArticleAgentClient agentClient;

    @Test
    public void testClearAllRemovesAgentSessionAndIndex() {
        Assumptions.assumeTrue(agentClient.health().getOk(), "article_trace_agent 未启动，跳过会话管理测试");

        // 1. 造一个真实会话：Ask 首条 chunk 会回传 agent 生成的 session_id
        String sessionId = null;
        Iterator<AskStreamChunk> chunks = agentClient.askStream(
                AskRequest.newBuilder().setQuery("你好").build());
        while (chunks.hasNext()) {
            AskStreamChunk chunk = chunks.next();
            assertTrue(chunk.getOk(), "Ask 失败：" + chunk.getMessage());
            if (chunk.getSessionId() != null && !chunk.getSessionId().isBlank()) {
                sessionId = chunk.getSessionId();
                break;
            }
        }
        assertNotNull(sessionId, "agent 未回传 session_id");

        // 2. 记录归属（模拟用户问答时 AgentController 的动作）
        String userId = "test-user-" + UUID.randomUUID();
        agentSessionService.remember(userId, sessionId);
        assertTrue(agentSessionService.owns(userId, sessionId), "归属校验失败");
        assertTrue(agentSessionService.listSessionIds(userId).contains(sessionId), "索引未写入");

        // 3. 模拟账号注销：清空该用户全部会话
        agentSessionService.clearAll(userId);
        assertFalse(agentSessionService.owns(userId, sessionId), "索引未清除");
        assertTrue(agentSessionService.listSessionIds(userId).isEmpty(), "索引仍有残留");

        // 4. agent 侧会话数据也应被删除
        ListSessionsReply reply = agentClient.listSessions(List.of(sessionId));
        assertTrue(reply.getSessionsList().isEmpty(), "agent 侧会话未删除");
    }
}
