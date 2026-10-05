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

# Mode pieces (#121 · 10-05): what a mode reads differently lives in eval/prompt_modes/<kind>_<mode>.md,
# one file per mode, and replaces this line of the common prompt. A request gets only its own mode's
# piece, so one mode's rule no longer leaks into another (#90: a diary rule cost the story's solution).
# coop reads like diary (its piece says so), so it gets both. No mode = every piece, in this order —
# the same text the prompt had before it was split.
MODE_SLOT = "{모드 조각}"
MODE_DIR = Path(__file__).resolve().parent / "prompt_modes"
MODE_PIECES = {"story": ("story",), "diary": ("diary",), "coop": ("diary", "coop")}
ALL_PIECES = ("story", "diary", "coop")


def fill_modes(text: str, kind: str, mode: str | None) -> str:
    """The common prompt with [MODE_SLOT] replaced by [mode]'s pieces. Text without the slot is left as it is."""
    if MODE_SLOT not in text:
        return text
    names = MODE_PIECES.get(mode, ALL_PIECES) if mode else ALL_PIECES
    pieces = [(MODE_DIR / f"{kind}_{n}.md").read_text(encoding="utf-8").rstrip("\n") for n in names]
    return text.replace(MODE_SLOT, "\n".join(pieces))


def _kind(path: Path) -> str:
    return Path(path).stem.replace("_prompt", "")      # judge_prompt.md → judge · line_prompt.md → line


def judge_system(path: Path, mode: str | None = None) -> str:
    """What the judge model gets. Used by eval/run_judge.py and the server alike."""
    if not JUDGE_WHOLE:
        return system_block(path, mode)
    return fill_modes(Path(path).read_text(encoding="utf-8"), _kind(path), mode).strip()


def system_block(path: Path, mode: str | None = None) -> str:
    text = fill_modes(Path(path).read_text(encoding="utf-8"), _kind(path), mode)
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
