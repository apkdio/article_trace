package com.articleTraceBack.rpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * 给每个 RPC 附上共享密钥 metadata（{@code x-agent-key}）。
 *
 * <p>键名与 agent 侧 {@code server/service_impl.py} 的 {@code _KEY_NAME} 对齐——两边都配好
 * 才启用校验，任一侧留空都不拦（便于灰度：先发 Java 侧，再给 agent 配密钥）。</p>
 */
class AgentAuthInterceptor implements ClientInterceptor {

    /** 与 agent 侧约定的 metadata 键 */
    static final Metadata.Key<String> AGENT_KEY =
            Metadata.Key.of("x-agent-key", Metadata.ASCII_STRING_MARSHALLER);

    private final String sharedKey;

    AgentAuthInterceptor(String sharedKey) {
        this.sharedKey = sharedKey;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                              CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(AGENT_KEY, sharedKey);
                super.start(responseListener, headers);
            }
        };
    }
}
