"""文迹文章问答编排：双路召回 → 按文章分组 → LLM 生成答案。

对外暴露两个函数：
- search_articles(query, ...)  → 只检索，返回命中文章列表
- ask(query, ...)              → 检索 + LLM 生成，返回 {answer, articles}
"""

from langchain_core.messages import HumanMessage, SystemMessage

from config_tool import load_config
from context_store import append_message, get_recent
from llm_tool import chat_once
from log_tool import get_logger
from prompts_tool import load_main_prompts

logger = get_logger(name="article_agent")

_agent_cfg = load_config("agent")
_rag_cfg = load_config("rag")
_llm_cfg = _agent_cfg.get("llm", {})
_behavior = _agent_cfg.get("behavior", {})
_article_cfg = _rag_cfg.get("article", {})

_hybrid_retriever = None

_GREETING_WORDS = (
    "你好", "您好", "在吗", "嗨", "哈喽", "hello", "hey",
    "早上好", "中午好", "晚上好",
)
_THANKS_WORDS = ("谢谢", "感谢", "多谢", "辛苦了", "thank")


def _get_retriever():
    """懒加载双路召回器（稠密 + 稀疏 → RRF）。"""
    global _hybrid_retriever
    if _hybrid_retriever is None:
        from hybrid_retriever import HybridRetriever
        _hybrid_retriever = HybridRetriever()
        _hybrid_retriever.ensure_sparse_index()
    return _hybrid_retriever


def _is_greeting(query: str) -> bool:
    q = query.strip().lower()
    return any(w in q for w in _GREETING_WORDS) or any(w in q for w in _THANKS_WORDS)


def _chunk_snippet(doc) -> str:
    """从 chunk 提取正文摘要（去掉开头标题行，截断 120 字）。"""
    text = doc.page_content or ""
    title = doc.metadata.get("title") or ""
    if title and text.startswith(title):
        text = text[len(title):].lstrip("\n").strip()
    return text[:120]


def _group_articles(fused, top_k=None) -> list:
    """把融合后的 chunk 按 article_id 分组，返回文章列表（按最佳分块分数降序）。"""
    articles = {}
    for doc, score, _meta in fused:
        aid = doc.metadata.get("article_id")
        if aid is None:
            continue
        aid = int(aid)
        if aid not in articles or score > articles[aid]["score"]:
            articles[aid] = {
                "id": aid,
                "title": doc.metadata.get("title", ""),
                "category_name": doc.metadata.get("category_name", ""),
                "snippet": _chunk_snippet(doc),
                "score": round(float(score), 6),
            }
    ranked = sorted(articles.values(), key=lambda a: a["score"], reverse=True)
    top_k = top_k or _article_cfg.get("top_k", 5)
    return ranked[:top_k]


def _context_block(fused, limit: int) -> str:
    """把 top 分块拼成 LLM 上下文（带文章标题/分类）。"""
    blocks = []
    for doc, _score, _meta in fused[:limit]:
        title = doc.metadata.get("title", "")
        cat = doc.metadata.get("category_name", "")
        header = f"【《{title}》】分类：{cat}" if title else "【未命名文章】"
        blocks.append(f"{header}\n{doc.page_content}")
    return "\n\n---\n\n".join(blocks)


def search_articles(query: str, top_k=None, category_id=None) -> list:
    """双路召回并按文章分组，返回命中文章列表（不做生成）。"""
    hr = _get_retriever()
    filter_ = {"category_id": {"$eq": int(category_id)}} if category_id else None
    fused = hr.search(query, filter=filter_)
    return _group_articles(fused, top_k=top_k)


def ask(query: str, session_id=None, category_id=None, top_k=None) -> dict:
    """问答入口：检索 → LLM 生成答案。

    返回：
        {"answer": str, "articles": [{"id","title","category_name","snippet","score"}]}
    """
    q = (query or "").strip()
    if not q:
        return {"answer": "请告诉我你想找什么文章～", "articles": []}
    session_id = session_id or "default"

    # 闲聊/道谢：直接 LLM 回应，不走检索
    if _is_greeting(q):
        answer = chat_once(
            [SystemMessage(content=load_main_prompts()), HumanMessage(content=q)],
            model=_llm_cfg.get("model"),
            temperature=_llm_cfg.get("temperature", 0.3),
        )
        append_message(session_id, "user", q)
        append_message(session_id, "assistant", answer)
        return {"answer": answer, "articles": []}

    append_message(session_id, "user", q)

    hr = _get_retriever()
    filter_ = {"category_id": {"$eq": int(category_id)}} if category_id else None
    fused = hr.search(q, filter=filter_)
    articles = _group_articles(fused, top_k=top_k)

    history_block = "\n".join(
        f"{'用户' if m.get('role') == 'user' else '助手'}：{(m.get('content') or '')[:200]}"
        for m in get_recent(session_id)
    )

    # 纯检索模式：直接返回命中片段原文
    if _behavior.get("retrieval_only", False):
        answer = _context_block(fused, _article_cfg.get("context_chunks", 4)) or "暂无相关文章。"
        append_message(session_id, "assistant", answer)
        return {"answer": answer, "articles": articles}

    if not articles:
        answer = chat_once(
            [
                SystemMessage(content=load_main_prompts()),
                HumanMessage(content=(
                    f"对话历史：\n{history_block}\n\n"
                    f"用户问题：{q}\n\n"
                    f"（参考资料为空，请据实说明暂未检索到相关文章。）"
                )),
            ],
            model=_llm_cfg.get("model"),
            temperature=_llm_cfg.get("temperature", 0.3),
        )
        append_message(session_id, "assistant", answer)
        return {"answer": answer, "articles": []}

    context_block = _context_block(fused, _article_cfg.get("context_chunks", 4))
    user_msg = (
        f"对话历史：\n{history_block}\n\n"
        f"参考资料：\n{context_block}\n\n"
        f"用户问题：{q}"
    )
    answer = chat_once(
        [SystemMessage(content=load_main_prompts()), HumanMessage(content=user_msg)],
        model=_llm_cfg.get("model"),
        temperature=_llm_cfg.get("temperature", 0.3),
    )
    append_message(session_id, "assistant", answer)
    logger.info("[Ask] query='%.50s' → %d article(s)", q, len(articles))
    return {"answer": answer, "articles": articles}
