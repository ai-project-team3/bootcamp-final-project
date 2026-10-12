"""Does Otto's spoken line keep the name the child just gave? (#301 · mascot line share of #250)

Same pattern as bench_story_given_names.py (#292): the same fixtures, each prompt version run three
times, through the server's own code — turn.user() builds the input, turn.schema() the shape,
turn.check() the server's rejection (a rejected line is what the phone replaces with its script),
turn.shape() the rules. The verdict is fixed per case so only the line model varies.

Groups: given_name — the child says the name in this utterance (Otto should mirror it);
so_far — the name was given earlier and is only in story_so_far, the child now says 「걔」 or nothing
(does Otto call it by its name or by 「광대」?); so_far_control — the same with an ordinary name, to tell
a brand-shy model from one that simply drops names; control — ordinary name and {친구1} said now.

A line KEEPS the name when every target name appears in what Otto says (ack · expand · question).
Options are not counted as retention, but they are scanned for invented brands like the rest.
An INVENTED brand is a brand/character/real name from the watch list that is in the line but not
anywhere in this case's input (slots · utterance) — the AI naming one first.

Run once per prompt version (copy the old prompt to <dir>/line_prompt.md so mode pieces still load):

    py eval/bench_line_given_names.py --out runs.jsonl [--prompt <dir>/line_prompt.md] [--env <file>]

Never prints credentials. Stops on the first quota / rate-limit error instead of retrying.
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import statistics
import sys
import time
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))
sys.path.insert(0, str(EVAL))

from app.config import Settings, settings  # noqa: E402
from app.llm.client import LLMError, complete  # noqa: E402
from app.routers import turn  # noqa: E402
from app.schemas.judge import JudgeResult  # noqa: E402
from app.schemas.turn import Line, TurnRequest  # noqa: E402
from prompt_block import system_block  # noqa: E402

# Brands · characters · real people a five-year-old might name, plus guidelines/8 §2's examples.
# Only used to count names the model introduced itself; a given name is never counted.
WATCH = ("뽀로로", "디즈니", "포켓몬", "피카츄", "맥도날드", "롯데월드", "코카콜라", "손흥민", "엘사",
         "타요", "핑크퐁", "아기상어", "티니핑", "헬로키티", "스파이더맨", "아이언맨", "레고", "카카오",
         "버거킹", "에버랜드", "신비아파트", "미키마우스", "겨울왕국", "도라에몽", "짱구")


def request(case: dict) -> TurnRequest:
    return TurnRequest(mode=case["mode"], slots=case["slots"], question=case["question"],
                       utterance=case["utterance"], turn=4)


def verdict(case: dict) -> JudgeResult:
    return JudgeResult(reason="fixed for the bench", **case["verdict"])


def invented(line: Line, case: dict) -> list[str]:
    given = " ".join([case["utterance"], *(str(v) for v in case["slots"].values() if v),
                      str(case["verdict"].get("value_1") or "")])
    said = " ".join(x for x in (line.ack, line.expand, line.question, *(line.options or ())) if x)
    return [w for w in WATCH if w in said and w not in given]


def score(line: Line, case: dict) -> dict:
    spoken = " ".join(x for x in (line.ack, line.expand, line.question) if x)
    missing = [n for n in case["names"] if n not in spoken]
    return {"missing": missing, "kept": not missing, "invented": invented(line, case)}


def _quota(error: Exception) -> bool:
    text = str(error).lower()
    return "429" in text or "quota" in text or "rate limit" in text or "rate_limit" in text


async def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompt", type=Path, default=EVAL / "line_prompt.md")
    ap.add_argument("--fixtures", type=Path, default=EVAL / "fixtures_line_given_names.jsonl")
    ap.add_argument("--env", type=Path, help="Existing local settings file; values are never printed")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--runs", type=int, default=3)
    a = ap.parse_args()
    if a.env:
        other = Settings(_env_file=a.env)
        for key in ("openai_api_key", "openai_base_url", "llm_provider", "llm_model", "llm_effort_line"):
            setattr(settings, key, getattr(other, key))
    if not settings.openai_api_key:
        print("Evaluation cannot run: API credential is not configured.")
        return 2
    cases = [json.loads(l) for l in a.fixtures.read_text(encoding="utf-8").splitlines() if l.strip()]
    meta = {"model": settings.llm_model, "effort": settings.llm_effort_line,
            "prompt_sha256": hashlib.sha256(a.prompt.read_bytes()).hexdigest()[:12]}
    rows: list[dict] = []
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with a.out.open("w", encoding="utf-8") as out:
        for run in range(1, a.runs + 1):
            for case in cases:
                req, v = request(case), verdict(case)
                row = {**meta, "id": case["id"], "group": case["group"], "run": run}
                started = time.monotonic()
                try:
                    raw = await complete(system_block(a.prompt, req.mode), turn.user(req, v), turn.schema(),
                                         name="mascot_line", effort=settings.llm_effort_line, timeout_s=25.0)
                    line = Line.model_validate(raw)
                    why = turn.check(line)
                    line = turn.shape(line, req, v)
                    row.update(score(line, case), rejection=why, line=line.model_dump())
                except LLMError as error:
                    if _quota(error):
                        print(f"stopped: quota/rate limit on {case['id']} run {run}", flush=True)
                        return 3
                    row.update(kept=False, invented=[], error=str(error)[:200])
                except ValueError:
                    row.update(kept=False, invented=[], error="invalid line response")
                row["seconds"] = round(time.monotonic() - started, 3)
                rows.append(row)
                out.write(json.dumps(row, ensure_ascii=False) + "\n")
                out.flush()
                spoken = row.get("line") or {}
                print(f"{case['id']} r{run}: kept={row['kept']} inv={row['invented']} "
                      f"rej={row.get('rejection') or row.get('error')} | {spoken.get('ack')} / "
                      f"{spoken.get('expand')} / {spoken.get('question')} / {spoken.get('options')}", flush=True)
    for group in ("given_name", "so_far", "so_far_control", "control"):
        sel = [r for r in rows if r["group"] == group]
        print(f"{group}: kept {sum(r['kept'] for r in sel)}/{len(sel)}")
    times = [r["seconds"] for r in rows]
    print(f"invented={sum(len(r['invented']) for r in rows)} "
          f"rejected={sum(bool(r.get('rejection')) for r in rows)} errors={sum('error' in r for r in rows)} "
          f"p50={statistics.median(times):.2f}s max={max(times):.2f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
