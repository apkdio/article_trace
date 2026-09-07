"""向量库模块：文章入库 / 删除 / 稠密检索（Chroma + bge-m3）。

文章正文由 Java 侧推送（纯文本或 HTML），本模块负责：
- HTML 清洗 → 分块
- 按 article_id 幂等入库（重推覆盖）
- 删除指定文章
- 稠密检索（DenseRetriever）
- 从 Chroma 重建 BM25 稀疏索引
"""

import os
import time

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from config_tool import load_config
from html_util import html_to_text
from llm_tool import get_embedding_model
from log_tool import get_logger
from path_tool import get_abs_path

logger = get_logger(name="vector_store")

_chroma_cfg = load_config("chroma")
_rag_cfg = load_config("rag")

# 索引脏标记：入库/删除后置 True，供双路召回在下次检索前重建稀疏索引
_index_dirty = False


def _get_splitter() -> RecursiveCharacterTextSplitter:
    chunk_cfg = _rag_cfg.get("chunk", {})
    return RecursiveCharacterTextSplitter(
        chunk_size=chunk_cfg.get("chunk_size", 600),
        chunk_overlap=chunk_cfg.get("chunk_overlap", 60),
        separators=chunk_cfg.get("separators", ["\n\n", "\n", "。", "！", "？", "；", " ", ""]),
        add_start_index=True,
    )


def _get_persist_dir() -> str:
    path = get_abs_path(_chroma_cfg.get("persist_dir", "data/vector_store"))
    os.makedirs(path, exist_ok=True)
    return path


def _get_collection_name() -> str:
    return _chroma_cfg.get("collection_name", "article_kb")


def get_vector_store() -> Chroma:
    """返回持久化的 Chroma 实例（本地 embedding）。"""
    embedding = get_embedding_model(
        model=_chroma_cfg["embedding"]["model"],
        base_url=_chroma_cfg["embedding"]["base_url"],
        api_key=_chroma_cfg["embedding"]["api_key"],
    )
    return Chroma(
        collection_name=_get_collection_name(),
        embedding_function=embedding,
        persist_directory=_get_persist_dir(),
        collection_metadata={"hnsw:space": "cosine"},
    )


# ---------------------------------------------------------------------------
# 索引脏标记
# ---------------------------------------------------------------------------

def mark_index_dirty() -> None:
    global _index_dirty
    _index_dirty = True


def is_index_dirty() -> bool:
    return _index_dirty


def _clear_index_dirty() -> None:
    global _index_dirty
    _index_dirty = False


# ---------------------------------------------------------------------------
# 文章 → chunks
# ---------------------------------------------------------------------------

def _clean_article(article: dict) -> dict:
    """清洗文章内容（HTML → 纯文本），返回规整后的文章 dict。"""
    art = dict(article)
    art["content"] = html_to_text(art.get("content") or "")
    return art


def _article_to_chunks(article: dict):
    """把一篇文章切成 chunks，返回 (documents, ids)。"""
    art = _clean_article(article)
    article_id = art.get("id")
    title = (art.get("title") or "").strip()
    content = (art.get("content") or "").strip()

    texts = _get_splitter().split_text(content) if content else [""]
    docs, ids = [], []
    for i, chunk_text in enumerate(texts):
        page = f"{title}\n{chunk_text}" if title else chunk_text
        meta = {
            "article_id": int(article_id),
            "title": title,
            "category_name": art.get("category_name") or "",
            "chunk_index": i,
            "chunk_id": f"{article_id}:{i}",
            "create_time": art.get("create_time") or "",
            "update_time": art.get("update_time") or "",
            "views": int(art.get("views") or 0),
            "author": art.get("author") or "",
        }
        if art.get("category_id") is not None:
            meta["category_id"] = int(art["category_id"])
        docs.append(Document(page_content=page, metadata=meta))
        ids.append(f"article-{article_id}-{i}")
    return docs, ids


# ---------------------------------------------------------------------------
# 入库 / 删除
# ---------------------------------------------------------------------------

def ingest_article(article: dict) -> dict:
    """单篇入库（幂等：先删该文章旧 chunk 再写）。"""
    art = _clean_article(article)
    article_id = art.get("id")
    if article_id is None:
        return {"status": "error", "article_id": None, "message": "article.id 缺失"}

    start = time.time()
    docs, ids = _article_to_chunks(art)
    store = get_vector_store()

    # 幂等：删除该文章旧 chunk（按 article_id）
    store._collection.delete(where={"article_id": {"$eq": int(article_id)}})
    if docs:
        store.add_documents(docs, ids=ids)

    mark_index_dirty()
    elapsed = round(time.time() - start, 2)
    logger.info("[Ingest] article_id=%s title=%.20s chunks=%d (%.2fs)",
                article_id, art.get("title", ""), len(docs), elapsed)
    return {"status": "ok", "article_id": article_id, "chunks": len(docs), "elapsed": elapsed}


def ingest_articles(articles: list) -> dict:
    """批量入库。逐篇幂等覆盖，返回成功/失败计数。"""
    ok = failed = 0
    messages = []
    for art in articles:
        r = ingest_article(art)
        if r.get("status") == "ok":
            ok += 1
        else:
            failed += 1
            messages.append(r.get("message", ""))
    return {"status": "ok", "ingested": ok, "failed": failed, "message": "; ".join(messages)}


def delete_articles(article_ids: list) -> dict:
    """删除指定文章的所有 chunk。"""
    ids = [int(a) for a in article_ids if a is not None]
    if not ids:
        return {"status": "ok", "deleted": 0}
    store = get_vector_store()
    store._collection.delete(where={"article_id": {"$in": ids}})
    mark_index_dirty()
    logger.info("[Delete] removed %d article(s)", len(ids))
    return {"status": "ok", "deleted": len(ids)}


# ---------------------------------------------------------------------------
# 稠密检索
# ---------------------------------------------------------------------------

class DenseRetriever:
    """基于 Chroma 的稠密（向量）检索器。"""

    def search(self, query: str, top_k: int | None = None, filter: dict | None = None):
        k = top_k if top_k is not None else _rag_cfg.get("retrieval", {}).get("dense_top_k", 10)
        store = get_vector_store()
        results = store.similarity_search_with_relevance_scores(query, k=k, filter=filter)
        logger.info("[Dense] query='%.50s' filter=%s → %d results", query, filter, len(results))
        return results


# ---------------------------------------------------------------------------
# 稀疏索引重建
# ---------------------------------------------------------------------------

def build_hybrid_index(sparse_retriever=None, force: bool = False) -> int:
    """读取 Chroma 里所有 chunk，构建 BM25（稀疏）索引。

    force=True 时跳过 pickle 缓存强制重建（用于入库/删除后的即时刷新）。
    """
    if sparse_retriever is None:
        from sparse_retriever import SparseRetriever
        sparse_retriever = SparseRetriever()

    if not force and sparse_retriever.load():
        return len(sparse_retriever.chunks)

    store = get_vector_store()
    data = store._collection.get(include=["metadatas", "documents"])
    docs = [
        Document(page_content=text, metadata=meta or {})
        for text, meta in zip(data["documents"], data["metadatas"])
    ]
    sparse_retriever.index_documents(docs)
    sparse_retriever.save()
    _clear_index_dirty()
    return len(docs)


# ---------------------------------------------------------------------------
# 统计 / 重置
# ---------------------------------------------------------------------------

def list_collections_info() -> dict:
    """返回 collection 基本统计（chunk 数、去重文章数）。"""
    store = get_vector_store()
    count = store._collection.count()
    data = store._collection.get(include=["metadatas"])
    article_ids = {
        m.get("article_id")
        for m in data["metadatas"]
        if m and "article_id" in m
    }
    return {
        "collection_name": _get_collection_name(),
        "chunk_count": count,
        "article_count": len(article_ids),
    }


def reset_collection() -> int:
    """清空当前 collection 的所有 chunk。返回清空前的数量。"""
    store = get_vector_store()
    count = store._collection.count()
    store._collection.delete(where={"chunk_id": {"$ne": "__never__"}})
    mark_index_dirty()
    logger.warning("[Reset] Cleared %d chunk(s) from collection.", count)
    return count
