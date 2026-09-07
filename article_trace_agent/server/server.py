"""gRPC 服务入口。

运行方式（在项目根目录）：
    python -m server.server
或：
    python server/server.py
"""

import os
import sys
from concurrent import futures

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for _p in (
    _PROJECT_ROOT,
    os.path.join(_PROJECT_ROOT, "tools"),
    os.path.join(_PROJECT_ROOT, "generated"),
):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import grpc  # noqa: E402

try:
    import article_agent_pb2_grpc as pb_grpc  # noqa: E402
except ImportError as e:
    raise SystemExit(
        "缺少 gRPC stub，请先生成（见 proto/README.md）：\n"
        "  python -m grpc_tools.protoc -I proto "
        "--python_out=generated --grpc_python_out=generated proto/article_agent.proto"
    ) from e

from server.service_impl import ArticleAgentServicer  # noqa: E402
from tools.log_tool import get_logger  # noqa: E402

logger = get_logger(name="grpc_server")


def serve(port=None):
    port = int(port or os.environ.get("AGENT_PORT", "50051"))
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    pb_grpc.add_ArticleAgentServiceServicer_to_server(ArticleAgentServicer(), server)
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    logger.info("[gRPC] article_agent listening on :%d", port)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
