package com.articleTraceBack;

import com.articleTraceBack.Service.AgentSessionService;
import com.articleTraceBack.rpc.ArticleAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * agent 禁用态测试：验证 {@code rpc.agent.enabled=false} 时的优雅降级。
 *
 * <p>覆盖三件事：Spring 上下文仍能正常装配（禁用的 bean 不会导致启动失败）、
 * RPC 直接返回失败态而不尝试连接、禁用时不写会话索引。</p>
 *
 * <pre>mvn test -Dtest=AgentDisabledTest</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "rpc.agent.enabled=false")
public class AgentDisabledTest {

    @Autowired
    private ArticleAgentClient agentClient;

    @Autowired
    private AgentSessionService agentSessionService;

    @Test
    public void testDisabledDegradesGracefully() {
        // 1. 开关生效（未建连）
        assertFalse(agentClient.isEnabled(), "rpc.agent.enabled=false 未生效");

        // 2. RPC 直接返回失败态：不抛异常、不尝试连接
        assertFalse(agentClient.health().getOk(), "禁用时 health 应返回失败态");
        assertFalse(agentClient.listSessions(List.of("any")).getOk(), "禁用时 listSessions 应返回失败态");
        assertFalse(agentClient.deleteArticles(List.of(1L)), "禁用时 deleteArticles 应返回失败态");

        // 3. 禁用时不写会话索引（避免无消费者地堆积 Redis 数据）
        String userId = "test-disabled-" + UUID.randomUUID();
        agentSessionService.remember(userId, "some-session-id");
        assertTrue(agentSessionService.listSessionIds(userId).isEmpty(), "禁用时不应写会话索引");
        assertFalse(agentSessionService.owns(userId, "some-session-id"), "禁用时不应记录归属");
    }
}
