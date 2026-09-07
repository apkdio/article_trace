package com.articleTraceBack.Controller;

import com.articleTraceBack.pojo.AgentAskRequest;
import com.articleTraceBack.pojo.AgentAskResult;
import com.articleTraceBack.pojo.AgentMatchedArticle;
import com.articleTraceBack.pojo.Result;
import com.articleTraceBack.rpc.ArticleAgentClient;
import com.articleTraceBack.rpc.gen.AskReply;
import com.articleTraceBack.rpc.gen.AskRequest;
import com.articleTraceBack.rpc.gen.HealthReply;
import com.articleTraceBack.rpc.gen.MatchedArticle;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索问答 / agent 健康探活接口。
 * 转发前端请求到 article_trace_agent，返回结果给前端。
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ArticleAgentClient agentClient;

    public AgentController(ArticleAgentClient agentClient) {
        this.agentClient = agentClient;
    }

    @PostMapping("/ask")
    public Result<AgentAskResult> ask(@RequestBody AgentAskRequest req) {
        if (req == null || req.getQuery() == null || req.getQuery().isBlank()) {
            return Result.error("问题（query）不能为空！");
        }

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

        AskReply reply = agentClient.ask(builder.build());
        return Result.success(toResult(reply));
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

    private AgentAskResult toResult(AskReply reply) {
        AgentAskResult result = new AgentAskResult();
        result.setOk(reply.getOk());
        result.setAnswer(reply.getAnswer());
        result.setMessage(reply.getMessage());

        List<AgentMatchedArticle> articles = new ArrayList<>();
        for (MatchedArticle m : reply.getArticlesList()) {
            AgentMatchedArticle a = new AgentMatchedArticle();
            a.setId(m.getId());
            a.setTitle(m.getTitle());
            a.setCategoryName(m.getCategoryName());
            a.setSnippet(m.getSnippet());
            a.setScore(m.getScore());
            articles.add(a);
        }
        result.setArticles(articles);
        return result;
    }
}
