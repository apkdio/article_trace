# =============================================================================
# agent 镜像：Python 检索问答微服务（gRPC :50051）
# 双路召回（Chroma + BM25）→ RRF → LLM 流式生成
# =============================================================================

FROM python:3.12-slim
WORKDIR /app
ENV PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai

COPY requirements.txt ./
# 国内服务器直连 PyPI 会拿不到部分 wheel（onnxruntime 等直接报 no matching distribution），
# 默认走腾讯云镜像；海外构建可 --build-arg PIP_INDEX_URL=https://pypi.org/simple/ 覆盖
ARG PIP_INDEX_URL=https://mirrors.cloud.tencent.com/pypi/simple/
RUN pip install --no-cache-dir -i $PIP_INDEX_URL -r requirements.txt

COPY . .

# gRPC stub 是生成产物（.gitignore 忽略），镜像内按 proto 契约重新生成
RUN mkdir -p generated \
 && python -m grpc_tools.protoc -I proto \
        --python_out=generated --grpc_python_out=generated \
        proto/article_agent.proto

# 向量库 / BM25 缓存 / 会话 jsonl 目录（compose 挂载卷持久化）
RUN mkdir -p data/vector_store data/pkl data/context logs

EXPOSE 50051
CMD ["python", "-m", "server.server"]
