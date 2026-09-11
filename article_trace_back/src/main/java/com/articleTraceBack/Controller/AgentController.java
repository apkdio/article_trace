package com.articleTraceBack.Controller;

import com.articleTraceBack.Service.AgentSessionService;
import com.articleTraceBack.Utils.ThreadLocalUtil;
import com.articleTraceBack.pojo.AgentAskRequest;
import com.articleTraceBack.pojo.AgentChatMessage;
import com.articleTraceBack.pojo.AgentMatchedArticle;
import com.articleTraceBack.pojo.AgentSession;
import com.articleTraceBack.pojo.Result;
import com.articleTraceBack.rpc.ArticleAgentClient;
import com.articleTraceBack.rpc.gen.AskRequest;
import com.articleTraceBack.rpc.gen.AskStreamChunk;
import com.articleTraceBack.rpc.gen.ChatMessage;
import com.articleTraceBack.rpc.gen.DeleteSessionReply;
import com.articleTraceBack.rpc.gen.GetSessionMessagesReply;
import com.articleTraceBack.rpc.gen.HealthReply;
import com.articleTraceBack.rpc.gen.ListSessionsReply;
import com.articleTraceBack.rpc.gen.MatchedArticle;
import com.articleTraceBack.rpc.gen.SessionSummary;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 检索问答 / 会话管理 / agent 健康探活接口。
 * <p>问答采用 SSE 流式输出，把 agent 的 LLM 增量逐段转发给前端；
 * 会话历史由 agent 侧持久化，会话归属索引由 {@link AgentSessionService} 维护。</p>
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ArticleAgentClient agentClient;
    private final AgentSessionService agentSessionService;
    private final ExecutorService askExecutor = Executors.newCachedThreadPool();

    public AgentController(ArticleAgentClient agentClient, AgentSessionService agentSessionService) {
        this.agentClient = agentClient;
        this.agentSessionService = agentSessionService;
    }

    /**
     * 检索 + LLM 问答（SSE 流式）。
     * 事件序列：session（会话 ID，首条）→ articles（命中文章，可选）→ 多个 delta（答案增量）→ done / error。
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody AgentAskRequest req) {
        if (req == null || req.getQuery() == null || req.getQuery().isBlank()) {
            throw new IllegalArgumentException("问题（query）不能为空！");
        }
        // 在同步线程取用户身份（异步线程里 ThreadLocal 已清理）
        String userId = currentUserId();
        SseEmitter emitter = new SseEmitter(120_000L);
        askExecutor.execute(() -> streamAsk(emitter, req, userId));
        return emitter;
    }

    /** 列出当前用户的会话（按更新时间倒序） */
    @GetMapping("/sessions")
    public Result<List<AgentSession>> listSessions() {
        String userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        if (!agentClient.isEnabled()) {
            return Result.error("AI 助手未启用");
        }
        List<String> sessionIds = agentSessionService.listSessionIds(userId);
        if (sessionIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        ListSessionsReply reply = agentClient.listSessions(sessionIds);
        if (!reply.getOk()) {
            return Result.error("获取会话列表失败：" + reply.getMessage());
        }
        List<AgentSession> sessions = new ArrayList<>();
        for (SessionSummary s : reply.getSessionsList()) {
            AgentSession as = new AgentSession();
            as.setSessionId(s.getSessionId());
            as.setTitle(s.getTitle());
            as.setMessageCount(s.getMessageCount());
            as.setUpdatedAt(s.getUpdatedAt());
            sessions.add(as);
        }
        return Result.success(sessions);
    }

    /** 获取指定会话的历史消息 */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<AgentChatMessage>> getSessionMessages(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        String userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        if (!agentClient.isEnabled()) {
            return Result.error("AI 助手未启用");
        }
        if (!agentSessionService.owns(userId, sessionId)) {
            return Result.error("会话不存在或无权访问！");
        }
        GetSessionMessagesReply reply = agentClient.getSessionMessages(sessionId, limit);
        if (!reply.getOk()) {
            return Result.error("获取会话消息失败：" + reply.getMessage());
        }
        List<AgentChatMessage> messages = new ArrayList<>();
        for (ChatMessage m : reply.getMessagesList()) {
            AgentChatMessage am = new AgentChatMessage();
            am.setRole(m.getRole());
            am.setContent(m.getContent());
            am.setTs(m.getTs());
            messages.add(am);
        }
        return Result.success(messages);
    }

    /** 删除指定会话 */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<String> deleteSession(@PathVariable String sessionId) {
        String userId = currentUserId();
        if (userId == null) {
            return Result.error("未登录！");
        }
        if (!agentClient.isEnabled()) {
            return Result.error("AI 助手未启用");
        }
        if (!agentSessionService.owns(userId, sessionId)) {
            return Result.error("会话不存在或无权访问！");
        }
        DeleteSessionReply reply = agentClient.deleteSession(sessionId);
        if (!reply.getOk()) {
            return Result.error("删除会话失败：" + reply.getMessage());
        }
        agentSessionService.forget(userId, sessionId);
        return Result.success();
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", agentClient.isEnabled());
        if (!agentClient.isEnabled()) {
            data.put("ok", false);
            return Result.success(data);
        }
        HealthReply reply = agentClient.health();
        data.put("ok", reply.getOk());
        data.put("articleCount", reply.getArticleCount());
        data.put("chunkCount", reply.getChunkCount());
        data.put("chatModel", reply.getChatModel());
        data.put("embeddingModel", reply.getEmbeddingModel());
        return Result.success(data);
    }

    @PreDestroy
    public void shutdown() {
        askExecutor.shutdownNow();
    }

    private void streamAsk(SseEmitter emitter, AgentAskRequest req, String userId) {
        if (!agentClient.isEnabled()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("AI 助手未启用"));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return;
        }
        try {
            Iterator<AskStreamChunk> chunks = agentClient.askStream(buildRequest(req));
            String sessionId = null;
            while (chunks.hasNext()) {
                AskStreamChunk chunk = chunks.next();
                if (!chunk.getOk()) {
                    emitter.send(SseEmitter.event().name("error").data(chunk.getMessage()));
                    emitter.complete();
                    return;
                }
                // 首条 chunk 携带会话 ID：记录映射并转发给前端复用
                if (sessionId == null && chunk.getSessionId() != null && !chunk.getSessionId().isBlank()) {
                    sessionId = chunk.getSessionId();
                    agentSessionService.remember(userId, sessionId);
                    emitter.send(SseEmitter.event().name("session").data(sessionId));
                }
                if (chunk.getArticlesCount() > 0) {
                    emitter.send(SseEmitter.event().name("articles")
                            .data(toArticles(chunk.getArticlesList())));
                }
                if (chunk.getDelta() != null && !chunk.getDelta().isEmpty()) {
                    emitter.send(SseEmitter.event().name("delta").data(chunk.getDelta()));
                }
            }
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (Exception e) {
            log.error("agent ask stream failed: userId={}, sessionId={}", userId, req.getSessionId(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (Exception ignored) {
            }
            emitter.complete();
        }
    }

    private AskRequest buildRequest(AgentAskRequest req) {
        AskRequest.Builder builder = AskRequest.newBuilder().setQuery(req.getQuery());
        if (req.getSessionId() != null && !req.getSessionId().isBlank()) {
            builder.setSessionId(req.getSessionId());
        }
        if (req.getCategoryId() != null) {
            builder.setCategoryId(req.getCategoryId());
        }
        if (req.getTopK() != null) {
            builder.setTopK(req.getTopK());
        }
        return builder.build();
    }

    private List<AgentMatchedArticle> toArticles(List<MatchedArticle> list) {
        List<AgentMatchedArticle> result = new ArrayList<>();
        for (MatchedArticle m : list) {
            AgentMatchedArticle a = new AgentMatchedArticle();
            a.setId(m.getId());
            a.setTitle(m.getTitle());
            a.setCategoryName(m.getCategoryName());
            a.setSnippet(m.getSnippet());
            a.setScore(m.getScore());
            result.add(a);
        }
        return result;
    }

    /** 从 ThreadLocal 取当前登录用户 id（TokenCheck 已校验登录） */
    private String currentUserId() {
        Object info = ThreadLocalUtil.get();
        if (info instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id == null ? null : String.valueOf(id);
        }
        return null;
    }
}
