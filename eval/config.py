from __future__ import annotations

import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
CATALOG_PATH = ROOT / "model_catalog.json"


def load_dotenv(path: Path | None = None) -> None:
    """Tiny .env loader so the project stays dependency-free.

    Existing process environment variables win over values in .env.
    """
    path = path or (PROJECT_ROOT / ".env")
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def load_catalog(path: Path = CATALOG_PATH) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def resolve_model(alias: str) -> dict:
    load_dotenv()
    catalog = load_catalog()
    if alias not in catalog["models"]:
        known = ", ".join(sorted(catalog["models"]))
        raise ValueError(f"unknown model alias: {alias}. known: {known}")
    cfg = dict(catalog["models"][alias])
    cfg["alias"] = alias
    env_model = cfg.get("model_env")
    if env_model and os.getenv(env_model):
        cfg["api_model"] = os.environ[env_model]
    reasoning_env = cfg.get("reasoning_effort_env")
    if reasoning_env and os.getenv(reasoning_env):
        cfg["reasoning_effort"] = os.environ[reasoning_env]
    return cfg


def key_env_for(provider: str) -> str | None:
    return {
        "openai": "OPENAI_API_KEY",
        "anthropic": "ANTHROPIC_API_KEY",
        "mistral": "MISTRAL_API_KEY",
        "mock": None,
    }.get(provider)


def has_key(provider: str) -> bool:
    load_dotenv()
    env_name = key_env_for(provider)
    return True if env_name is None else bool(os.getenv(env_name))


def usd_krw(default: float = 1400.0) -> float:
    load_dotenv()
    try:
        return float(os.getenv("USD_KRW", str(default)))
    except ValueError:
        return default
