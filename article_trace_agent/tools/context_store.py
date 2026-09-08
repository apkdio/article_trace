"""对话上下文存储：按 session_id 分隔的 jsonl 文件 + 内存缓存。

每轮追加一条消息（role + content + ts），文件保留全量，内存缓存与读取时
保留最近 6 轮（12 条消息）供指代消解与 LLM 生成拼接。
"""

import json
import os
import re
import uuid
from datetime import datetime

from tools.path_tool import get_abs_path

_CONTEXT_DIR = get_abs_path("data/context")
_CONTEXT_META_DATA_DIR = get_abs_path("data/context_meta")
_MAX_TURNS = 6
_MAX_MESSAGES = _MAX_TURNS * 2

_cache = {}


def _file_path(session_id: str) -> str:
    return os.path.join(_CONTEXT_DIR, f"{session_id}.jsonl")


def _meta_path(session_id: str) -> str:
    return os.path.join(_CONTEXT_META_DATA_DIR, f"{session_id}.meta.json")


def _get_meta_field(session_id: str, field: str):
    fp = _meta_path(session_id)
    if not os.path.exists(fp):
        return None
    try:
        with open(fp, encoding="utf-8") as f:
            return json.load(f).get(field)
    except Exception:
        return None


def get_session_title(session_id: str) -> str | None:
    return _get_meta_field(session_id, "title")


def _update_meta(session_id: str, **fields) -> dict:
    os.makedirs(_CONTEXT_DIR, exist_ok=True)
    os.makedirs(_CONTEXT_META_DATA_DIR, exist_ok=True)
    file_path = _meta_path(session_id)
    data = {}
    if os.path.exists(file_path):
        try:
            with open(file_path, encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            data = {}
    data.update(fields)
    try:
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
    except Exception:
        pass
    return data


def set_session_title(session_id: str, title: str):
    _update_meta(session_id, title=title)


def append_message(session_id: str, role: str, content: str, **meta):
    """追加一条消息：写内存缓存 + 追加 jsonl 文件。"""
    os.makedirs(_CONTEXT_DIR, exist_ok=True)
    os.makedirs(_CONTEXT_META_DATA_DIR, exist_ok=True)
    msg = {"role": role, "content": content, "ts": datetime.now().isoformat(timespec="seconds")}
    msg.update(meta)

    cache = _cache.setdefault(session_id, [])
    cache.append(msg)
    if len(cache) > _MAX_MESSAGES:
        cache.pop(0)

    with open(_file_path(session_id), "a", encoding="utf-8") as f:
        f.write(json.dumps(msg, ensure_ascii=False) + "\n")


def get_recent(session_id: str, n: int = None) -> list:
    """取最近 n 条消息（默认 6 轮）。优先内存缓存，未缓存则读文件。"""
    n = n or _MAX_MESSAGES
    cache = _cache.get(session_id)
    if cache is not None:
        return cache[-n:]

    msgs = []
    fp = _file_path(session_id)
    if os.path.exists(fp):
        with open(fp, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    msgs.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    msgs = msgs[-n:]
    _cache[session_id] = msgs
    return msgs


def delete_session(session_id: str) -> bool:
    """删除指定会话：清理 jsonl 数据文件、meta 文件与内存缓存。"""
    _cache.pop(session_id, None)
    fp = _file_path(session_id)
    meta_fp = _meta_path(session_id)
    deleted = False
    if os.path.exists(fp):
        try:
            os.remove(fp)
            deleted = True
        except OSError:
            pass
    if os.path.exists(meta_fp):
        try:
            os.remove(meta_fp)
        except OSError:
            pass
    return deleted


_UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    re.IGNORECASE,
)


def ensure_session_id(session_id):
    """校验 session_id：非合法 UUID 则重新生成。"""
    if isinstance(session_id, str) and _UUID_RE.match(session_id):
        return session_id
    return str(uuid.uuid4())


def list_sessions() -> list:
    """列出所有会话：session_id + 标题 + 消息数 + 更新时间。"""
    os.makedirs(_CONTEXT_DIR, exist_ok=True)
    os.makedirs(_CONTEXT_META_DATA_DIR, exist_ok=True)
    sessions = []
    for fn in os.listdir(_CONTEXT_DIR):
        if not fn.endswith(".jsonl"):
            continue
        sid = fn[:-6]
        fp = os.path.join(_CONTEXT_DIR, fn)
        msgs = []
        with open(fp, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    msgs.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
        if not msgs:
            continue

        title = get_session_title(sid)
        if not title:
            for m in msgs:
                if m.get("role") == "user":
                    title = m.get("content", "")[:20]
                    break
        title = title or "（空会话）"

        ts = msgs[-1].get("ts", "")
        sessions.append({
            "session_id": sid,
            "title": title,
            "messages": len(msgs),
            "updated_at": ts,
        })
    sessions.sort(key=lambda s: s.get("updated_at") or "", reverse=True)
    return sessions
