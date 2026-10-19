package com.articleTraceBack.rpc;

import com.articleTraceBack.rpc.gen.ArticleAgentServiceGrpc;
import com.articleTraceBack.rpc.gen.HealthReply;
import com.articleTraceBack.rpc.gen.HealthRequest;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java→agent 共享密钥的客户端侧：每个 RPC 都要带上 {@code x-agent-key} metadata
 * （键名与 agent 侧 service_impl 的 _KEY_NAME 对齐，配错一边等于没鉴权）。
 *
 * <pre>mvn test -Dtest=AgentAuthInterceptorTest</pre>
 */
class AgentAuthInterceptorTest {

    /** 只记录 start() 收到的 metadata 的假调用，验证「发出去的头里有没有密钥」 */
    private static class RecordingCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
        final AtomicReference<Metadata> headers = new AtomicReference<>();

        @Override
        public void start(Listener<RespT> listener, Metadata metadata) {
            headers.set(metadata);
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(ReqT message) {
        }
    }

    @SuppressWarnings("unchecked")
    private Metadata headersFor(String sharedKey) {
        RecordingCall<HealthRequest, HealthReply> call = new RecordingCall<>();
        Channel fakeChannel = new Channel() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method,
                                                                CallOptions callOptions) {
                return (ClientCall<ReqT, RespT>) call;
            }

            @Override
            public String authority() {
                return "test";
            }
        };
        MethodDescriptor<HealthRequest, HealthReply> method = ArticleAgentServiceGrpc.getHealthMethod();
        ClientCall<HealthRequest, HealthReply> intercepted =
                new AgentAuthInterceptor(sharedKey).interceptCall(method, CallOptions.DEFAULT, fakeChannel);
        intercepted.start(new ClientCall.Listener<>() {
        }, new Metadata());
        return call.headers.get();
    }

    @Test
    void attachesSharedKeyToEveryCall() {
        Metadata headers = headersFor("s3cret-key");
        assertEquals("s3cret-key", headers.get(AgentAuthInterceptor.AGENT_KEY),
                "RPC 头里必须带 x-agent-key，否则 agent 侧校验直接 UNAUTHENTICATED");
    }

    @Test
    void headerKeyNameMatchesAgentSide() {
        // 键名是两侧唯一的约定点：agent 侧 service_impl.py 里是 _KEY_NAME = "x-agent-key"
        assertEquals("x-agent-key", AgentAuthInterceptor.AGENT_KEY.name());
    }
}
