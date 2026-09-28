"""The judge prompt and schema are read from eval/, not copied.

Rule: one thing in one place. eval/judge_prompt.md is what the 100-question
battery measured; 박진웅 owns it and writes the canonical version (09-30).
If the server kept its own copy, the measured prompt and the served prompt
would drift apart without anyone noticing.
"""
import json
from functools import lru_cache

from ..config import REPO
from ..schemas.judge import JudgeRequest

EVAL = REPO / "eval"


@lru_cache(maxsize=1)
def schema() -> dict:
    s = json.loads((EVAL / "judge_schema.json").read_text(encoding="utf-8"))
    # the file carries name/description for humans; the API wants the bare schema
    return {k: v for k, v in s.items() if k not in ("name", "description")}


@lru_cache(maxsize=1)
def system() -> str:
    base = (EVAL / "judge_prompt.md").read_text(encoding="utf-8").strip()
    return f"{base}\n\n[공통 JSON 스키마]\n{json.dumps(schema(), ensure_ascii=False, separators=(',', ':'))}\n"


def user(req: JudgeRequest) -> str:
    """Same lines eval/run_judge.py sends, plus mode and the question just asked."""
    slots = json.dumps(req.slots, ensure_ascii=False, separators=(",", ":"))
    return (
        f"mode:{req.mode}\n"
        f"slots:{slots}\n"
        f"asked:{req.asked_slot or ''}\n"
        f"template:{req.template or ''}\n"
        f"question:{req.question}\n"
        f"utterance:{req.utterance}\n"
        "현재 슬롯 전체와 아이 발화를 함께 보고 판정하세요. JSON 외에는 출력하지 마세요."
    )
