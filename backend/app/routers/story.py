"""POST /story — once or twice per session. Latency budget is generous.

The prompt is read from eval/, not copied (one thing in one place):
story → story_prompt.md, diary · coop → story_prompt_diary.md.
"""
import json
import re
from functools import lru_cache

from fastapi import APIRouter, HTTPException

from ..config import REPO, settings
from ..filters.blocklist import BLOCK, ALLOW
from ..llm.client import LLMError, complete
from ..schemas.story import Scene, StoryRequest, StoryResult

router = APIRouter()
EVAL = REPO / "eval"

# story: exactly six (spec §3-3). diary · coop: as many as the day filled.
_SCENES = {"story": (6, 6), "diary": (3, 6), "coop": (3, 6)}


def _system_block(path: str) -> str:
    """The text inside the first ``` fence under 「시스템 프롬프트」 — the files wrap it in notes."""
    text = (EVAL / path).read_text(encoding="utf-8")
    body = text.split("## 시스템 프롬프트", 1)[1]
    return body.split("```", 2)[1].strip("\n")


@lru_cache(maxsize=4)
def system(mode: str) -> str:
    if mode == "story":
        # the story prompt ends with its own input block; the server sends input separately
        return _system_block("story_prompt.md").split("[입력 슬롯]", 1)[0].rstrip()
    return _system_block("story_prompt_diary.md")


@lru_cache(maxsize=1)
def schema() -> dict:
    s = json.loads((EVAL / "story_schema.json").read_text(encoding="utf-8"))
    return {k: v for k, v in s.items() if k not in ("name", "description")}


def user(req: StoryRequest) -> str:
    slots = json.dumps(req.slots, ensure_ascii=False, separators=(",", ":"))
    if req.mode == "story":
        return (f"[입력 슬롯]\n{slots}\n"
                f"이야기 템플릿: {req.template or '(없음)'}\n아이 수준: {req.level or '(없음)'}")
    by = json.dumps(req.slot_by, ensure_ascii=False, separators=(",", ":"))
    keep = json.dumps(req.keep, ensure_ascii=False)
    return f"모드: {req.mode}\n채워진 칸: {slots}\n칸마다 by: {by}\n맺음: {keep}"


def _eojeol(text: str) -> list[str]:
    return [w.strip(".,!?~…\"'") for w in text.split()]


def check(result: StoryResult, mode: str) -> str | None:
    """Why this book must not reach the child, or None. A bad book is thrown away, not patched."""
    lo, hi = _SCENES[mode]
    if not lo <= len(result.scenes) <= hi:
        return f"{len(result.scenes)} scenes (want {lo}-{hi})"
    for sc in result.scenes:
        words = _eojeol(sc.caption)
        if any(w in BLOCK for w in words) and not any(w in ALLOW for w in words):
            return f"blocked word in scene {sc.index}"
        # a real name would mean the model invented one: names come in only as {주인공} · {친구n}
        if re.search(r"\{(?!주인공\}|친구\d\})[^}]*\}", sc.caption):
            return f"unknown placeholder in scene {sc.index}"
    return None


def mock(req: StoryRequest) -> StoryResult:
    filled = [v for v in req.slots.values() if v]
    n = _SCENES[req.mode][1]
    caps = [f"{{주인공}}은 {v} 이야기를 했어요." for v in filled][:n]
    caps += ["{주인공}은 오늘 이야기를 마쳤어요."] * (n - len(caps))
    return StoryResult(scenes=[Scene(index=i + 1, caption=c, keywords="paper cutout")
                               for i, c in enumerate(caps)])


@router.post("/story", response_model=StoryResult)
async def story(req: StoryRequest) -> StoryResult:
    if settings.mock:
        return mock(req)
    try:
        raw = await complete(system(req.mode), user(req), schema(), name="story",
                             effort=settings.llm_effort_story, max_output_tokens=6000)
    except LLMError as e:
        raise HTTPException(502, str(e)) from e
    result = StoryResult.model_validate(raw)
    why = check(result, req.mode)
    if why:
        # the phone falls back to its own template book (spec §3-0: the app never stops)
        raise HTTPException(502, f"story rejected: {why}")
    return result
