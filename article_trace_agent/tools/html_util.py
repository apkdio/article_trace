"""轻量 HTML → 纯文本，用于剥离文章富文本标签（不依赖第三方库）。"""

import html
import re

# 脚本/样式块整体去掉
_TAG_BLOCK_RE = re.compile(r"<(script|style)[^>]*>.*?</\1>", re.IGNORECASE | re.DOTALL)
# 任意标签
_TAG_RE = re.compile(r"<[^>]+>")
# 块级标签后补换行，避免段落粘连
_BLOCK_RE = re.compile(r"</?(?:p|div|br|h[1-6]|li|tr|section|article)[^>]*>", re.IGNORECASE)
# 连续空白折叠
_WS_RE = re.compile(r"[ \t\r\f\v]+")
_MULTI_NL_RE = re.compile(r"\n{3,}")


def html_to_text(raw: str) -> str:
    """把 HTML 片段转成纯文本。

    对已经是纯文本的输入直接返回（幂等）。
    """
    if not raw:
        return ""
    if "<" not in raw:
        return raw

    text = _TAG_BLOCK_RE.sub("", raw)
    text = _BLOCK_RE.sub("\n", text)
    text = _TAG_RE.sub("", text)
    text = html.unescape(text)
    text = _WS_RE.sub(" ", text)
    text = _MULTI_NL_RE.sub("\n\n", text)
    return text.strip()
