package com.articleTraceBack.rpc;

import com.articleTraceBack.rpc.gen.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

    private ManagedChannel channel;
    private ArticleAgentServiceGrpc.ArticleAgentServiceBlockingStub blockingStub;
    private ArticleAgentServiceGrpc.ArticleAgentServiceStub asyncStub;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = ArticleAgentServiceGrpc.newBlockingStub(channel);
        asyncStub = ArticleAgentServiceGrpc.newStub(channel);
        log.info("ArticleAgentClient initialized: {}:{}", host, port);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }

    /** 单篇推送/覆盖（按 article.id 幂等） */
    public boolean ingestArticle(Article article) {
        try {
            IngestReply reply = blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .ingestArticle(IngestRequest.newBuilder().setArticle(article).build());
            if (!reply.getOk()) {
                log.warn("ingestArticle rejected: articleId={}, message={}",
                        article.getId(), reply.getMessage());
            }
            return reply.getOk();
        } catch (Exception e) {
            log.error("ingestArticle rpc failed: articleId={}", article.getId(), e);
            return false;
        }
    }

    /** 批量推送/覆盖 */
    public BatchIngestReply batchIngestArticles(List<Article> articles) {
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

    /** 健康/统计 */
    public HealthReply health() {
        try {
            return blockingStub
                    .withDeadlineAfter(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .health(HealthRequest.newBuilder().build());
        } catch (Exception e) {
            log.error("health rpc failed", e);
            return HealthReply.newBuilder().setOk(false).build();
        }
    }

    /** 全量/增量同步（客户端流式推送） */
    public SyncReply syncArticles(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return SyncReply.newBuilder().setOk(true).setIngested(0).setFailed(0).build();
        }
        CountDownLatch latch = new CountDownLatch(1);
        SyncReply[] result = new SyncReply[1];
        StreamObserver<SyncReply> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(SyncReply value) {
                result[0] = value;
            }

            @Override
            public void onError(Throwable t) {
                log.error("syncArticles stream error", t);
                result[0] = SyncReply.newBuilder()
                        .setOk(false).setMessage(t.getMessage()).build();
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };

        StreamObserver<Article> requestObserver = asyncStub.syncArticles(responseObserver);
        try {
            for (Article article : articles) {
                requestObserver.onNext(article);
            }
            requestObserver.onCompleted();
            if (!latch.await(60, TimeUnit.SECONDS)) {
                log.warn("syncArticles timeout after 60s");
                return SyncReply.newBuilder().setOk(false).setMessage("timeout").build();
            }
        } catch (Exception e) {
            log.error("syncArticles failed", e);
            return SyncReply.newBuilder().setOk(false).setMessage(e.getMessage()).build();
        }
        return result[0] != null
                ? result[0]
                : SyncReply.newBuilder().setOk(false).setMessage("no response").build();
    }
}
