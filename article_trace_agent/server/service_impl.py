"""gRPC 服务实现：把 RPC 方法映射到检索/问答内核。"""

import article_agent_pb2 as pb
import article_agent_pb2_grpc as pb_grpc

from tools.article_agent import ask_stream as core_ask_stream
from tools.config_tool import load_config
from tools.log_tool import get_logger
from tools.vector_store import (
    delete_articles,
    ingest_article,
    ingest_articles,
    list_collections_info,
)

logger = get_logger(name="grpc_service")


def _article_to_dict(a) -> dict:
    """proto Article → 内核使用的 dict。"""
    return {
        "id": a.id,
        "title": a.title,
        "content": a.content,
        "state": a.state,
        "category_id": a.category_id,
        "category_name": a.category_name,
        "cover_url": a.cover_url,
        "views": a.views,
        "create_time": a.create_time,
        "update_time": a.update_time,
        "author": a.author,
    }


class ArticleAgentServicer(pb_grpc.ArticleAgentServiceServicer):
    def IngestArticle(self, request, context):
        result = ingest_article(_article_to_dict(request.article))
        ok = result.get("status") == "ok"
        return pb.IngestReply(
            ok=ok,
            article_id=request.article.id,
            message=result.get("message", ""),
        )

    def BatchIngestArticles(self, request, context):
        result = ingest_articles([_article_to_dict(a) for a in request.articles])
        return pb.BatchIngestReply(
            ok=(result.get("failed", 0) == 0),
            ingested=result.get("ingested", 0),
            failed=result.get("failed", 0),
            message=result.get("message", ""),
        )

    def DeleteArticles(self, request, context):
        result = delete_articles(list(request.article_ids))
        return pb.DeleteReply(
            ok=True,
            deleted=result.get("deleted", 0),
            message=result.get("message", ""),
        )

    def SyncArticles(self, request_iterator, context):
        articles = [_article_to_dict(a) for a in request_iterator]
        result = ingest_articles(articles)
        return pb.SyncReply(
            ok=(result.get("failed", 0) == 0),
            ingested=result.get("ingested", 0),
            failed=result.get("failed", 0),
            message=result.get("message", ""),
        )

    def Ask(self, request, context):
        try:
            for item in core_ask_stream(
                request.query,
                session_id=request.session_id or None,
                category_id=request.category_id or None,
                top_k=request.top_k or None,
            ):
                matched = [
                    pb.MatchedArticle(
                        id=a["id"],
                        title=a["title"],
                        category_name=a["category_name"],
                        snippet=a["snippet"],
                        score=a["score"],
                    )
                    for a in item.get("articles", [])
                ]
                yield pb.AskStreamChunk(
                    ok=True,
                    articles=matched,
                    delta=item.get("delta", ""),
                    message="",
                )
        except Exception as e:
            logger.error("Ask stream failed: %s", e)
            yield pb.AskStreamChunk(ok=False, message=str(e))

    def Health(self, request, context):
        info = list_collections_info()
        agent_cfg = load_config("agent")
        chroma_cfg = load_config("chroma")
        return pb.HealthReply(
            ok=True,
            article_count=info.get("article_count", 0),
            chunk_count=info.get("chunk_count", 0),
            chat_model=agent_cfg.get("llm", {}).get("model", ""),
            embedding_model=chroma_cfg.get("embedding", {}).get("model", ""),
        )
