"""What of a prompt .md file actually goes to the model. One function, used by both
the measurement (eval/run_judge.py) and the server (backend/app/llm/judge_prompt.py,
backend/app/routers/story.py) — if they read the file differently, the served prompt
is not the measured one.

The .md files wrap the prompt in notes for people: a header, a variable table, caching
notes. Only the first ``` fence under 「## 시스템 프롬프트」 is the prompt. Inside it, the
input block (「[지금 상태]」 for the judge, 「[입력 슬롯]」 for the story) is dropped too:
the caller sends the input as the user message, so a second, unfilled copy with
literal {slots} would only confuse the model (09-28, 박진웅).

A file with no such fence is sent whole, as before.

⚠️ 09-28 measurement (results.md): for the JUDGE the block lost to the whole file —
three runs each, no overlap on multi-fill F1 (0.663~0.677 vs 0.700~0.763) and
next_slot (70.7~74.3% vs 78.7~82.7%). So the judge is sent WHOLE ([JUDGE_WHOLE]) until
진웅's canonical prompt is measured better in three runs. The story keeps the block.
"""
from __future__ import annotations

from pathlib import Path

INPUT_MARKERS = ("[지금 상태]", "[입력 슬롯]")

# The judge goes whole — see the measurement note above. Flip only on three-run numbers.
JUDGE_WHOLE = True


def judge_system(path: Path) -> str:
    """What the judge model gets. Used by eval/run_judge.py and the server alike."""
    return Path(path).read_text(encoding="utf-8").strip() if JUDGE_WHOLE else system_block(path)


def system_block(path: Path) -> str:
    text = Path(path).read_text(encoding="utf-8")
    if "## 시스템 프롬프트" not in text:
        return text.strip()
    after = text.split("## 시스템 프롬프트", 1)[1]
    parts = after.split("```", 2)
    if len(parts) < 3:
        return text.strip()
    block = parts[1].split("\n", 1)[1] if "\n" in parts[1] else parts[1]   # drop a fence language tag line
    for marker in INPUT_MARKERS:
        if marker in block:
            block = block.split(marker, 1)[0]
    return block.strip()
