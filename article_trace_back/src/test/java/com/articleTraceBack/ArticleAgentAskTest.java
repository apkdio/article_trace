package com.articleTraceBack;

import com.articleTraceBack.rpc.gen.ArticleAgentServiceGrpc;
import com.articleTraceBack.rpc.gen.AskRequest;
import com.articleTraceBack.rpc.gen.AskStreamChunk;
import com.articleTraceBack.rpc.gen.HealthRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ask 流式接口集成测试：模拟用户输入问题，验证流式返回 LLM 生成的答案。
 *
 * <p>运行前提：article_trace_agent 的 gRPC server 已在 localhost:50051 启动：</p>
 * <pre>cd article_trace_agent && .venv/Scripts/python -m server.server</pre>
 *
 * <p>若 agent 未启动，本测试会被跳过（assumeTrue），不会导致构建失败。</p>
 *
 * <p>自定义问题：</p>
 * <pre>mvn test -Dtest=ArticleAgentAskTest -Dask.query="你的问题"</pre>
 */
public class ArticleAgentAskTest {

    private static final String HOST = "localhost";
    private static final int PORT = 50051;

    @Test
    public void testAskStreamReturnsLlmAnswer() {
        // 模拟用户输入的问题（可通过 -Dask.query 覆盖）

        String query = System.getProperty("ask.query", "现在库里有多少文章？");

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(HOST, PORT)
                .usePlaintext()
                .build();
        try {
            ArticleAgentServiceGrpc.ArticleAgentServiceBlockingStub stub =
                    ArticleAgentServiceGrpc.newBlockingStub(channel);

            // agent 未启动时跳过，而非失败
            Assumptions.assumeTrue(isAgentAvailable(stub), "article_trace_agent 未启动，跳过 Ask 测试");

            Iterator<AskStreamChunk> chunks = stub
                    .withDeadlineAfter(60, TimeUnit.SECONDS)
                    .ask(AskRequest.newBuilder().setQuery(query).build());

            StringBuilder answer = new StringBuilder();
            int articleCount = 0;
            while (chunks.hasNext()) {
                AskStreamChunk chunk = chunks.next();
                assertTrue(chunk.getOk(), "流式返回失败：" + chunk.getMessage());
                articleCount += chunk.getArticlesCount();
                answer.append(chunk.getDelta());
            }

            assertFalse(answer.toString().isBlank(), "LLM 答案为空");

            System.out.println("【用户问题】" + query);
            System.out.println("【LLM 答案】" + answer);
            System.out.println("【命中文章】" + articleCount + " 篇");
        } finally {
            channel.shutdownNow();
        }
    }

    private boolean isAgentAvailable(ArticleAgentServiceGrpc.ArticleAgentServiceBlockingStub stub) {
        try {
            stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .health(HealthRequest.newBuilder().build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
