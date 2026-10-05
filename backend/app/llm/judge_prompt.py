"""The judge prompt and schema are read from eval/, not copied.

Rule: one thing in one place. eval/judge_prompt.md is what the 100-question
battery measured; 박진웅 owns it and writes the canonical version (09-30).
If the server kept its own copy, the measured prompt and the served prompt
would drift apart without anyone noticing.
"""
import importlib.util
import json
from functools import lru_cache

from ..config import REPO
from ..schemas.judge import JudgeRequest

EVAL = REPO / "eval"


def _load_block_fn():
    """eval/prompt_block.py by path: the measurement and the server must cut the file the same way."""
    spec = importlib.util.spec_from_file_location("prompt_block", EVAL / "prompt_block.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


_blocks = _load_block_fn()
system_block = _blocks.system_block      # the story's cut
judge_system = _blocks.judge_system      # the judge's cut (whole file — 09-28 measurement)


@lru_cache(maxsize=None)
def load_schema(file: str) -> dict:
    """An eval/*_schema.json as the API wants it. Judge, story, line and image all read theirs here."""
    s = json.loads((EVAL / file).read_text(encoding="utf-8"))
    # the file carries name/description for humans; the API wants the bare schema
    return {k: v for k, v in s.items() if k not in ("name", "description")}


def schema() -> dict:
    return load_schema("judge_schema.json")


@lru_cache(maxsize=None)
def system(mode: str | None = None) -> str:
    """The judge's system prompt for [mode] — only that mode's piece (#121). No mode = every piece."""
    base = judge_system(EVAL / "judge_prompt.md", mode)
    return f"{base}\n\n[공통 JSON 스키마]\n{json.dumps(schema(), ensure_ascii=False, separators=(',', ':'))}\n"


def user(req: JudgeRequest) -> str:
    """Same lines eval/run_judge.py sends, plus mode and the question just asked."""
    slots = json.dumps(req.slots, ensure_ascii=False, separators=(",", ":"))
    return (
        f"mode:{req.mode}\n"
        # coop: why the parent picked the story — a 곧 해요 answer is a plan, not an event (#100 · 10-05)
        + (f"reason:{getattr(req, 'reason', None) or 'done'}\n" if req.mode == "coop" else "")
        + f"slots:{slots}\n"
        f"asked:{req.asked_slot or ''}\n"
        f"template:{req.template or ''}\n"
        f"question:{req.question}\n"
        f"utterance:{req.utterance}\n"
        "현재 슬롯 전체와 아이 발화를 함께 보고 판정하세요. JSON 외에는 출력하지 마세요."
    )
