"""Diary: does Otto's line invent things the child never said? (10-05 · re-run on the split prompt 10-13)

The diary line piece (eval/prompt_modes/line_diary.md) against the one on --before, alternating so
drift hits both arms:
  A  before — the common prompt + diary piece on --before (origin/main)
  B  now    — the working tree (what the server would send)

The app sends no drawing to /turn in a diary, so neither arm does. --drawn sends the drawing as the
template 「그린 것: …」 in both arms — the 10-05 question (does knowing the drawing help?) whose answer
was the app's fixed clue frames (demo/DiaryClue.kt · bench_diary_clue_frames.py).

Scored per line:
  rules      — question present · one 「?」 · no 「언제」 · not future tense · short (22 chars; 40 with choices)
  invents    — a grader call: does the line say something happened that the child never said?
               (「조개가 반짝였구나」 when the child said 「조개 주웠어」). Lower is better
  expand     — the line has an expand at all (an expand of null is allowed)
  clue       — the line names something the child drew but has not said yet (--drawn only)

  py eval/bench_diary_drawing.py [--rounds 3] [--before origin/main] [--drawn] --yes-spend

Costs about fifty won (gpt-6-luna · line + grader × 2 arms × rounds × 12 scenes).
"""
import asyncio
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
from app.llm.client import complete  # noqa: E402
from app.routers import turn  # noqa: E402
from app.schemas.judge import JudgeResult  # noqa: E402
from app.schemas.turn import TurnRequest  # noqa: E402

ROUNDS = int(sys.argv[sys.argv.index("--rounds") + 1]) if "--rounds" in sys.argv else 3
BEFORE = sys.argv[sys.argv.index("--before") + 1] if "--before" in sys.argv else "origin/main"
DRAWN = "--drawn" in sys.argv
OUT = ROOT / "eval" / "raw" / f"diary_line_{'drawn_' if DRAWN else ''}{BEFORE.replace('/', '_')}.jsonl"
sys.path.insert(0, str(ROOT / "eval"))
from prompt_block import MODE_SLOT, system_block  # noqa: E402

# (id, drawn, slots, question just asked, child's words, asked slot, judge's next slot, level)
CASES = [
    ("d01", ["놀이터", "미끄럼틀", "엄마"], {}, "여기는 어디야?", "놀이터", "place", "problem", "chain"),
    ("d02", ["집", "강아지"], {"place": "집"}, "집에서 무슨 일이 있었어?", "몰라", "problem", "problem", "pick"),
    ("d03", ["바다", "조개", "아빠"], {"place": "바다"}, "바다에서 무슨 일이 있었어?", "조개 주웠어", "problem", "companion", "chain"),
    ("d04", ["어린이집", "블록", "친구"], {"place": "어린이집", "problem": "블록을 쌓았다"}, "그래서 어떻게 됐어?", "무너졌어", "solution", "reaction", "reason"),
    ("d05", ["할머니 집", "수박"], {}, "오늘 어디 갔었어?", "음…", "place", "place", "pick"),
    ("d06", ["공원", "자전거"], {"place": "공원"}, "공원에서 무슨 일이 있었어?", "자전거 탔어", "problem", "solution", "chain"),
    ("d07", ["병원", "주사"], {}, "오늘 어디 갔었어?", "병원 갔어", "place", "problem", "chain"),
    ("d08", ["놀이터", "그네", "친구"], {"place": "놀이터"}, "놀이터에서 무슨 일이 있었어?", "배고파", "problem", "problem", "chain"),
    ("d09", ["마트", "과자", "엄마"], {"place": "마트"}, "마트에서 무슨 일이 있었어?", "과자 샀어", "problem", "companion", "chain"),
    ("d10", ["수영장"], {"place": "수영장", "problem": "수영했다"}, "그때 기분이 어땠어?", "재밌었어", "reaction", "solution", "reason"),
    ("d11", ["집", "해", "나"], {}, "여기는 어디야?", "집", "place", "problem", "pick"),
    ("d12", ["동물원", "기린", "코끼리"], {"place": "동물원"}, "동물원에서 무슨 일이 있었어?", "기린 봤어", "problem", "reaction", "chain"),
]

GRADER = """당신은 3~7세 그림일기 앱의 대사를 검사합니다. 일기는 아이가 오늘 **실제로 있었던 일**만 적습니다.
아래 [아이가 한 말]에 없는 일을 마스코트의 대사가 **있었던 일로 단정**하면 presumes=true 입니다.
- 아이가 그린 것은 있었던 일의 증거가 아닙니다. "미끄럼틀을 그렸네"는 괜찮지만 "미끄럼틀 탔구나"는 단정입니다.
- 질문 안에 깔린 전제도 봅니다. "엄마랑 미끄럼틀 탈 때 어땠어?"는 엄마와 미끄럼틀 탄 일을 전제합니다.
- 아이가 말한 것을 되받거나, 열린 질문으로 묻는 것은 단정이 아닙니다.
- 아이가 말한 곳 · 일을 넣어 묻는 것도 단정이 아닙니다. 아이가 "놀이터"라고 했으면 "놀이터에서 무슨 일이 있었어?"는 괜찮습니다.
  "무슨 일이 있었어?" · "뭐 했어?" · "누구랑 했어?"처럼 묻는 것은 일이 있었다는 단정이 아닙니다(답은 아이가 합니다).
- 아이가 그린 것을 "그렸네"라고 말하는 것은 단정이 아닙니다. 그린 것으로 무슨 일이 있었다고 말하거나 전제할 때만 단정입니다.
- 마스코트가 덧붙인 모습 · 소리 · 느낌("반짝였구나" · "바스락거렸어" · "긴 목이 보였구나")도 아이가 말하지 않았으면 단정입니다.
JSON 만 출력합니다."""
GRADER_SCHEMA = {"type": "object", "additionalProperties": False, "required": ["presumes", "what"],
                 "properties": {"presumes": {"type": "boolean"}, "what": {"type": "string"}}}

FUTURE = re.compile(r"(볼까|할까|할 거야|할 거니|갈까|할래|될까)\?$")


def git_file(rev: str, path: str) -> str:
    return subprocess.run(["git", "show", f"{rev}:{path}"], cwd=ROOT, capture_output=True,
                          text=True, encoding="utf-8", check=True).stdout


def before_system() -> str:
    """The diary line prompt as --before had it: its common prompt with its own diary piece put in."""
    text = git_file(BEFORE, "eval/line_prompt.md")
    if MODE_SLOT in text:
        text = text.replace(MODE_SLOT, git_file(BEFORE, "eval/prompt_modes/line_diary.md").rstrip("\n"))
    tmp = ROOT / "eval" / "_line_prompt_before.md"
    tmp.write_text(text, encoding="utf-8")
    try:
        return system_block(tmp)
    finally:
        tmp.unlink()


def nospace(t: str) -> int:
    return sum(1 for c in t if not c.isspace())


def rules_ok(q: str | None) -> tuple[bool, str]:
    if not q:
        return False, "질문 없음"
    if q.count("?") != 1:
        return False, f"물음표 {q.count('?')}개"
    if "언제" in q:
        return False, "언제"
    if FUTURE.search(q.strip()):
        return False, "앞일 시제"
    choices = "," in q.split("?")[0] or "·" in q
    limit = 40 if choices else 22
    if nospace(q) > limit:
        return False, f"길이 {nospace(q)}"
    return True, ""


def spoken_choices(q: str) -> list[str]:
    """「놀이터, 할머니 집. 오늘은 어디 있었어?」 → [놀이터, 할머니 집]"""
    head = q.split("?")[0]
    if "." not in head:
        return []
    lead = head.rsplit(".", 1)[0]
    parts = [p.strip(" .!") for p in re.split(r"[,·]|아니면", lead)]
    return [p for p in parts if p and len(p) <= 9]


def said_so_far(case) -> str:
    _, _, slots, q, said, *_ = case
    return " ".join([*(v for v in slots.values() if v), q, said])


async def grade(case, line: dict) -> dict:
    _, drawn, slots, q, said, *_ = case
    told = "\n".join([f"이미 들은 칸: {json.dumps(slots, ensure_ascii=False)}", f"방금 물은 말: {q}", f"아이가 한 말: {said}"])
    spoken = " / ".join(x for x in (line.get("ack"), line.get("expand"), line.get("question")) if x)
    user = f"[아이가 그린 것] {', '.join(drawn)}\n[아이가 한 말]\n{told}\n[마스코트 대사] {spoken}"
    try:
        return await complete(GRADER, user, GRADER_SCHEMA, name="presume_grade", effort="none", timeout_s=25)
    except Exception as e:
        return {"presumes": None, "what": f"grader failed: {e}"}


async def one(system: str, case, drawn_sent: bool) -> dict | None:
    cid, drawn, slots, q, said, asked, nxt, level = case
    req = TurnRequest(mode="diary", slots=slots, asked_slot=asked, question=q, utterance=said, turn=2,
                      level=level, template=("그린 것: " + ", ".join(drawn)) if drawn_sent else None)
    v = JudgeResult(reason="ok", story_ready=False, next_slot=nxt)
    try:
        return await complete(system, turn.user(req, v), turn.schema(), name="mascot_line", effort="none", timeout_s=25)
    except Exception as e:
        print(f"  ! {cid} {type(e).__name__}: {e}")
        return None


async def main() -> None:
    if "--yes-spend" not in sys.argv:
        sys.exit("costs about fifty won — pass --yes-spend")
    arms = {"A": before_system(), "B": turn.system("diary")}
    if arms["A"] == arms["B"]:
        sys.exit(f"the diary line prompt is the same as on {BEFORE} — nothing to compare")
    rows = []
    for r in range(ROUNDS):
        order = list(arms.items()) if r % 2 == 0 else list(arms.items())[::-1]
        for case in CASES:
            for arm, system in order:
                line = await one(system, case, DRAWN)
                if line is None:
                    rows.append({"round": r, "arm": arm, "id": case[0], "failed": True})
                    continue
                q = line.get("question")
                ok, why = rules_ok(q)
                spoken = " ".join(x for x in (line.get("ack"), line.get("expand"), q) if x)
                g = await grade(case, line)
                rows.append({
                    "round": r, "arm": arm, "id": case[0], "line": line, "rules_ok": ok, "rules_why": why,
                    "expand": bool(line.get("expand")),
                    "clue": any(w in spoken and w not in said_so_far(case) for w in case[1]) if DRAWN else None,
                    "invents": g.get("presumes"), "invents_what": g.get("what"),
                })
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in rows) + "\n", encoding="utf-8")

    def share(arm, key, want=True):
        xs = [x for x in rows if x["arm"] == arm and not x.get("failed") and x.get(key) is not None]
        return f"{sum(1 for x in xs if x[key] == want)}/{len(xs)}" if xs else "-"

    print(f"\n{'arm':<12}{'rules ok':>10}{'invents':>10}{'expand':>10}{'clue':>8}{'failed':>8}")
    for arm, label in (("A", "A before"), ("B", "B now")):
        failed = sum(1 for x in rows if x["arm"] == arm and x.get("failed"))
        print(f"{label:<12}{share(arm, 'rules_ok'):>10}{share(arm, 'invents'):>10}{share(arm, 'expand'):>10}{share(arm, 'clue'):>8}{failed:>8}")
    print("\ninvented:")
    for x in rows:
        if x.get("invents"):
            ln = x["line"]
            print(f"{x['id']} r{x['round']} {x['arm']}  {ln.get('ack')} / {ln.get('expand')} / {ln.get('question')}  ← {x['invents_what']}")
    print(f"\n→ {OUT}")


if __name__ == "__main__":
    asyncio.run(main())
