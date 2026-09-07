"""混合检索器：稠密（向量）+ 稀疏（BM25）→ RRF 融合。

用法：
    hr = HybridRetriever()
    hr.ensure_sparse_index()
    fused = hr.search("有哪些关于机器学习的文章")  # [(doc, rrf_score, meta), ...]
"""

from __future__ import annotations

from typing import List, Tuple

from langchain_core.documents import Document

from log_tool import get_logger
from rrf_fusion import reciprocal_rank_fusion
from sparse_retriever import SparseRetriever
from vector_store import DenseRetriever, build_hybrid_index, is_index_dirty

logger = get_logger(name="hybrid_retriever")


class HybridRetriever:
    """编排双路召回：稠密 + 稀疏 → RRF 融合。"""

    def __init__(self) -> None:
        self.dense = DenseRetriever()
        self.sparse = SparseRetriever()
        self._sparse_ready = False

    # ------------------------------------------------------------------
    # 索引
    # ------------------------------------------------------------------

    def ensure_sparse_index(self, force: bool = False) -> int:
        """构建/刷新 BM25 稀疏索引（入库后索引被标脏，会强制重建）。"""
        n = build_hybrid_index(self.sparse, force=force)
        self._sparse_ready = True
        logger.info("[Hybrid] Sparse index ready: %d chunks", n)
        return n

    # ------------------------------------------------------------------
    # 检索
    # ------------------------------------------------------------------

    def search(
        self, query: str, filter: dict | None = None
    ) -> List[Tuple[Document, float, dict]]:
        """执行稠密 + 稀疏检索，经 RRF 融合。

        返回：
            [(doc, rrf_score, meta), ...]，按 RRF 分数降序。
            meta = {"dense_rank", "sparse_rank", "rrf_score"}
        """
        if not self._sparse_ready or is_index_dirty():
            self.ensure_sparse_index(force=is_index_dirty())

        dense_results = self.dense.search(query, filter=filter)
        sparse_results = self.sparse.search(query, filter=filter)
        fused = reciprocal_rank_fusion(dense_results, sparse_results)
        logger.info("[Hybrid] query='%.40s' → %d fused result(s)", query, len(fused))
        return fused
