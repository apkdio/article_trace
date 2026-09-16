import os

import yaml

from tools.path_tool import get_abs_path

# 配置名 → 相对项目根目录的路径
_CONFIG_PATHS = {
    "rag": "config/rag.yaml",
    "chroma": "config/chroma.yaml",
    "prompts": "config/prompts.yaml",
    "agent": "config/agent.yaml",
}

# 环境变量覆盖表：(配置名, 段, 键) → (首选变量, 备用变量)
#
# 用途：把「模型端点」这类随部署环境变化、又常需保密的参数从 yaml 里解放出来，
# 两机部署时改 .env 即可指向另一台机器上的 Ollama，不必进容器改 yaml。
#
# 备用变量的存在是因为 embedding 与生成模型通常同端点，没必要配两遍：
# 只设 LLM_BASE_URL 时，embedding 也跟着走。
_ENV_OVERRIDES = {
    ("agent", "llm", "base_url"): ("LLM_BASE_URL", None),
    ("agent", "llm", "api_key"): ("LLM_API_KEY", None),
    ("agent", "llm", "model"): ("LLM_CHAT_MODEL", None),
    ("agent", "llm", "temperature"): ("LLM_TEMPERATURE", None),
    ("chroma", "embedding", "base_url"): ("EMBED_BASE_URL", "LLM_BASE_URL"),
    ("chroma", "embedding", "api_key"): ("EMBED_API_KEY", "LLM_API_KEY"),
    ("chroma", "embedding", "model"): ("LLM_EMBED_MODEL", None),
}


def _coerce(raw: str, current):
    """按 yaml 里原值的类型转换环境变量，避免 "0.3" 变成字符串。"""
    if isinstance(current, bool):
        return raw.strip().lower() in ("1", "true", "yes", "on")
    if isinstance(current, int):
        return int(raw)
    if isinstance(current, float):
        return float(raw)
    return raw


def _apply_env_overrides(name: str, cfg):
    """就地应用环境变量覆盖。name 是配置文件路径时不做任何覆盖。"""
    if not isinstance(cfg, dict):
        return cfg
    for (cfg_name, section_name, key), (env, fallback) in _ENV_OVERRIDES.items():
        if cfg_name != name:
            continue
        section = cfg.get(section_name)
        if not isinstance(section, dict):
            continue
        raw = os.environ.get(env) or (os.environ.get(fallback) if fallback else None)
        if not raw:
            continue
        section[key] = _coerce(raw, section.get(key))
    return cfg


def load_config(name: str, encoding: str = "utf-8"):
    """读取 YAML 配置。

    name 可以是 rag / chroma / prompts / agent 之一，或直接传配置文件路径。

    读取结果会应用环境变量覆盖（见 _ENV_OVERRIDES），因此调用方拿到的已经是
    「环境变量 > yaml」合并后的最终值。
    """
    path = _CONFIG_PATHS.get(name, name)
    with open(get_abs_path(path), "r", encoding=encoding) as f:
        cfg = yaml.load(f, Loader=yaml.FullLoader)
    return _apply_env_overrides(name, cfg)
