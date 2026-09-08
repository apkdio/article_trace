"""基于 OpenAI 兼容端点的 LLM / embedding 工厂。

默认连接本地 Ollama（http://localhost:11434/v1），也支持任意 OpenAI 兼容
服务商（云端或自建）：只需在 config/agent.yaml 与 config/chroma.yaml 里
修改 base_url / api_key / model 即可，无需改代码。
"""

import os

from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from tools.log_tool import get_logger

logger = get_logger(name="llm_tool")

# 默认连接配置（本地 Ollama，无需真实 API key）
_DEFAULT_BASE_URL = os.environ.get("LLM_BASE_URL", "http://localhost:11434/v1")
_DEFAULT_API_KEY = os.environ.get("LLM_API_KEY", "ollama")
_DEFAULT_CHAT_MODEL = os.environ.get("LLM_CHAT_MODEL", "qwen2.5:7b")
_DEFAULT_EMBED_MODEL = os.environ.get("LLM_EMBED_MODEL", "bge-m3")


def get_chat_model(
    model: str = _DEFAULT_CHAT_MODEL,
    base_url: str = _DEFAULT_BASE_URL,
    api_key: str = _DEFAULT_API_KEY,
    temperature: float = 0.3,
    **kwargs,
) -> ChatOpenAI:
    """返回一个连接到 OpenAI 兼容端点（默认 Ollama）的 ChatOpenAI 实例。"""
    logger.info(f"[Chat] init model={model}")
    return ChatOpenAI(
        model=model,
        base_url=base_url,
        api_key=api_key,
        temperature=temperature,
        **kwargs,
    )


def get_embedding_model(
    model: str = _DEFAULT_EMBED_MODEL,
    base_url: str = _DEFAULT_BASE_URL,
    api_key: str = _DEFAULT_API_KEY,
    **kwargs,
) -> OpenAIEmbeddings:
    """返回一个连接到 OpenAI 兼容端点（默认 Ollama）的 OpenAIEmbeddings 实例。"""
    logger.info(f"[Embed] init model={model}")
    return OpenAIEmbeddings(
        model=model,
        base_url=base_url,
        api_key=api_key,
        # Ollama 的 embedding 端点期望原始文本而非 token id，关闭长度检查。
        check_embedding_ctx_length=False,
        **kwargs,
    )


def stream_chat(messages: list, model: str = "", temperature: float = 0.3) -> any:
    """流式对话：生成器，随生成过程逐段产出文本块。"""
    m = model or _DEFAULT_CHAT_MODEL
    llm = get_chat_model(model=m, temperature=temperature, streaming=True)
    logger.info(f"[Stream] start model={m}")
    try:
        for chunk in llm.stream(messages):
            if chunk.content:
                yield chunk.content
    except Exception as e:
        logger.error(f"[Stream] failed: {e}")
        yield ""


def chat_once(messages: list, model: str = "", temperature: float = 0.3) -> str:
    """非流式对话：一次性返回完整回答文本。"""
    m = model or _DEFAULT_CHAT_MODEL
    llm = get_chat_model(model=m, temperature=temperature)
    logger.info(f"[Chat] start model={m}")
    try:
        resp = llm.invoke(messages)
        return getattr(resp, "content", "") or ""
    except Exception as e:
        logger.error(f"[Chat] failed: {e}")
        return ""
