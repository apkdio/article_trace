package com.articleTraceBack.Controller;

import com.articleTraceBack.pojo.AgentAskRequest;
import com.articleTraceBack.pojo.AgentMatchedArticle;
import com.articleTraceBack.pojo.Result;
import com.articleTraceBack.rpc.ArticleAgentClient;
import com.articleTraceBack.rpc.gen.AskRequest;
import com.articleTraceBack.rpc.gen.AskStreamChunk;
import com.articleTraceBack.rpc.gen.HealthReply;
import com.articleTraceBack.rpc.gen.MatchedArticle;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 检索问答 / agent 健康探活接口。
 * 问答采用 SSE 流式输出，把 agent 的 LLM 增量逐段转发给前端。
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ArticleAgentClient agentClient;
    private final ExecutorService askExecutor = Executors.newCachedThreadPool();

    public AgentController(ArticleAgentClient agentClient) {
        this.agentClient = agentClient;
    }

    /**
     * 检索 + LLM 问答（SSE 流式）。
     * 事件序列：articles（命中文章，可选）→ 多个 delta（答案增量）→ done / error。
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody AgentAskRequest req) {
        if (req == null || req.getQuery() == null || req.getQuery().isBlank()) {
            throw new IllegalArgumentException("问题（query）不能为空！");
        }
        SseEmitter emitter = new SseEmitter(120_000L);
        askExecutor.execute(() -> streamAsk(emitter, req));
        return emitter;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        HealthReply reply = agentClient.health();
        Map<String, Object> data = new HashMap<>();
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

    private void streamAsk(SseEmitter emitter, AgentAskRequest req) {
        try {
            Iterator<AskStreamChunk> chunks = agentClient.askStream(buildRequest(req));
            while (chunks.hasNext()) {
                AskStreamChunk chunk = chunks.next();
                if (!chunk.getOk()) {
                    emitter.send(SseEmitter.event().name("error").data(chunk.getMessage()));
                    emitter.complete();
                    return;
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
}
