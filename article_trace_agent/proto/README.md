# 接口契约（唯一共享物）

`article_agent.proto` 是 `article_trace`(Java) 与 `article_agent`(Python) 之间**唯一**的代码耦合点。
两边各复制本文件，各自生成 stub，除此之外不共享任何代码。

## 契约要点

- 版本化命名空间 `article.agent.v1`，将来不兼容变更走 `v2`。
- 入库按 `article.id` **幂等**（重推即覆盖，不产生重复数据）。
- `Ask` 为同步请求-响应；`SyncArticles` 为客户端流式（全量/增量灌数据）。
- `MatchedArticle` 只回摘要片段，不回正文（正文由 Java 按 id 从 RustFS 自取）。

## 代码生成

### Python 侧（article_agent）

```bash
pip install grpcio grpcio-tools
python -m grpc_tools.protoc \
    -I proto \
    --python_out=generated \
    --grpc_python_out=generated \
    proto/article_agent.proto
```

生成产物：`generated/article_agent_pb2.py`、`generated/article_agent_pb2_grpc.py`（加入 `.gitignore`）。

### Java 侧（article_trace_back）

推荐 `protobuf-maven-plugin`（`org.xolstice.maven.plugins`，配合 `os-maven-plugin`），
把本 proto 放到 `src/main/proto/` 下即可在 `mvn compile` 时自动生成到
`com.articleTraceBack.rpc.gen` 包。或手动：

```bash
protoc --java_out=... --grpc-java_out=... proto/article_agent.proto
```

> 两端各自独立生成，各自 gitignore 生成产物，仅 proto 文件以复制方式同步。
