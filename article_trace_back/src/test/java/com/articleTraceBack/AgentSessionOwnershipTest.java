package com.articleTraceBack;

import com.articleTraceBack.Controller.AgentController;
import com.articleTraceBack.Service.AgentSessionService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.AgentAskRequest;
import com.articleTraceBack.rpc.ArticleAgentClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * /agent/ask 的会话归属守卫：别人的 sessionId 不许续聊——一旦转发，remember 会把该会话
 * 绑到调用者名下，连后续 messages / delete 的 owns 校验都会放行（bugs_report P0-4）。
 *
 * <pre>mvn test -Dtest=AgentSessionOwnershipTest</pre>
 */
class AgentSessionOwnershipTest {

    @AfterEach
    void cleanup() {
        ThreadLocalUtil.remove();
    }

    private void loginAs(int id) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", id);
        info.put("name", "u" + id);
        info.put("type", 0);
        ThreadLocalUtil.set(info);
    }

    private AgentAskRequest ask(String query, String sessionId) {
        AgentAskRequest req = new AgentAskRequest();
        req.setQuery(query);
        req.setSessionId(sessionId);
        return req;
    }

    @Test
    void askRejectsSessionOwnedBySomeoneElse() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        ArticleAgentClient client = mock(ArticleAgentClient.class);
        given(sessions.owns("42", "not-mine")).willReturn(false);
        AgentController controller = new AgentController(client, sessions);

        loginAs(42);
        assertNotNull(controller.ask(ask("你好", "not-mine")));

        // 归属判定发生在同步段，必须查过；且请求从未转发给 agent
        verify(sessions).owns("42", "not-mine");
        verify(client, never()).askStream(any());
    }

    @Test
    void askAllowsOwnSession() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        ArticleAgentClient client = mock(ArticleAgentClient.class);
        given(sessions.owns("42", "mine")).willReturn(true);
        given(client.isEnabled()).willReturn(true);
        AgentController controller = new AgentController(client, sessions);

        loginAs(42);
        controller.ask(ask("你好", "mine"));

        verify(client, timeout(2000)).askStream(any());
    }

    @Test
    void askWithoutSessionSkipsOwnershipCheck() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        ArticleAgentClient client = mock(ArticleAgentClient.class);
        given(client.isEnabled()).willReturn(true);
        AgentController controller = new AgentController(client, sessions);

        loginAs(42);
        controller.ask(ask("你好", null));

        // 新会话由服务端生成，不存在归属问题，不应查 owns
        verify(sessions, never()).owns(anyString(), anyString());
        verify(client, timeout(2000)).askStream(any());
    }
}
