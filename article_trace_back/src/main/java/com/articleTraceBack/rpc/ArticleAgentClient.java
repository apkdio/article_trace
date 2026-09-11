package com.articleTraceBack.rpc;

import com.articleTraceBack.rpc.gen.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * article_trace_agent 的 gRPC 客户端封装。
 * 对应契约 proto/article_agent.proto 中的 ArticleAgentService。
 * 所有方法均做容错：RPC 失败只记录日志并返回失败态，不向上抛异常，
 * 保证 agent 服务不可用时不影响主业务。
 */
@Slf4j
@Component
public class ArticleAgentClient {

    private static final long RPC_TIMEOUT_SECONDS = 5L;

    @Value("${rpc.agent.host:localhost}")
    private String host;

    @Value("${rpc.agent.port:50051}")
    private int port;

    /** agent 总开关：关闭时不建连，所有调用直接返回失败态（主业务不受影响） */
    @Value("${rpc.agent.enabled:true}")
    private boolean enabled;

    private ManagedChannel channel;
    private ArticleAgentServiceGrpc.ArticleAgentServiceBlockingStub blockingStub;

    /** agent 是否启用（供各调用方做降级判断） */
    public boolean isEnabled() {
        return enabled;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("article agent disabled (rpc.agent.enabled=false), skip gRPC channel init");
            return;
        }
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = ArticleAgentServiceGrpc.newBlockingStub(channel);
        log.info("ArticleAgentClient initialized: {}:{}", host, port);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }

    /** 批量推送/覆盖 */
    public BatchIngestReply batchIngestArticles(List<Article> articles) {
        if (!enabled) {
            return BatchIngestReply.newBuilder().setOk(false).setMessage("agent disabled").build();
        }
        try {
            return blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .batchIngestArticles(
                            BatchIngestRequest.newBuilder().addAllArticles(articles).build());
        } catch (Exception e) {
            log.error("batchIngestArticles rpc failed", e);
            return BatchIngestReply.newBuilder()
                    .setOk(false).setMessage(e.getMessage()).build();
        }
    }

    /** 删除若干篇文章 */
    public boolean deleteArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return true;
        }
        if (!enabled) {
            return false;
        }
        try {
            DeleteReply reply = blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .deleteArticles(
                            DeleteRequest.newBuilder().addAllArticleIds(articleIds).build());
            return reply.getOk();
        } catch (Exception e) {
            log.error("deleteArticles rpc failed: {}", articleIds, e);
            return false;
        }
    }

    /** 检索 + LLM 流式生成答案（服务端流式，返回增量迭代器） */
    public Iterator<AskStreamChunk> askStream(AskRequest request) {
        if (!enabled) {
            return Collections.singletonList(
                    AskStreamChunk.newBuilder().setOk(false).setMessage("agent disabled").build()
            ).iterator();
        }
        try {
            return blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS * 6, TimeUnit.SECONDS)
                    .ask(request);
        } catch (Exception e) {
            log.error("askStream rpc failed", e);
            return Collections.singletonList(
                    AskStreamChunk.newBuilder().setOk(false).setMessage(e.getMessage()).build()
            ).iterator();
        }
    }

    /** 列出会话（可选按 sessionIds 过滤，空则返回全部） */
    public ListSessionsReply listSessions(List<String> sessionIds) {
        if (!enabled) {
            return ListSessionsReply.newBuilder().setOk(false).setMessage("agent disabled").build();
        }
        try {
            ListSessionsRequest.Builder builder = ListSessionsRequest.newBuilder();
            if (sessionIds != null && !sessionIds.isEmpty()) {
                builder.addAllSessionIds(sessionIds);
            }
            return blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .listSessions(builder.build());
        } catch (Exception e) {
            log.error("listSessions rpc failed", e);
            return ListSessionsReply.newBuilder().setOk(false).setMessage(e.getMessage()).build();
        }
    }

    /** 获取会话历史消息（limit<=0 表示全部） */
    public GetSessionMessagesReply getSessionMessages(String sessionId, int limit) {
        if (!enabled) {
            return GetSessionMessagesReply.newBuilder().setOk(false).setMessage("agent disabled").build();
        }
        try {
            GetSessionMessagesRequest.Builder builder =
                    GetSessionMessagesRequest.newBuilder().setSessionId(sessionId);
            if (limit > 0) {
                builder.setLimit(limit);
            }
            return blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .getSessionMessages(builder.build());
        } catch (Exception e) {
            log.error("getSessionMessages rpc failed: {}", sessionId, e);
            return GetSessionMessagesReply.newBuilder().setOk(false).setMessage(e.getMessage()).build();
        }
    }

    /** 删除会话 */
    public DeleteSessionReply deleteSession(String sessionId) {
        if (!enabled) {
            return DeleteSessionReply.newBuilder().setOk(false).setMessage("agent disabled").build();
        }
        try {
            return blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .deleteSession(DeleteSessionRequest.newBuilder().setSessionId(sessionId).build());
        } catch (Exception e) {
            log.error("deleteSession rpc failed: {}", sessionId, e);
            return DeleteSessionReply.newBuilder().setOk(false).setMessage(e.getMessage()).build();
        }
    }

    /** 健康/统计 */
    public HealthReply health() {
        if (!enabled) {
            return HealthReply.newBuilder().setOk(false).build();
        }
        try {
            return blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .health(HealthRequest.newBuilder().build());
        } catch (Exception e) {
            log.error("health rpc failed", e);
            return HealthReply.newBuilder().setOk(false).build();
        }
    }
}
