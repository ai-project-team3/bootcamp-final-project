"""Diary: does telling the line model what the child drew make Otto's questions better? (10-05)

Three arms on the same diary turns, alternating so drift hits all:
  A  now        — the prompt on --before (origin/main), no drawing sent (what the app sends today)
  B  drawn only — the same prompt, template "그린 것: …" sent, no rule about it
  C  drawn+rule — the working eval/line_prompt.md (diary rule: the drawing is a clue, never a fact), template sent

Scored per line:
  rules      — question present · one 「?」 · no 「언제」 · not future tense · short (22 chars; 40 with choices)
  clue       — the line names something the child drew but has not said yet (the drawing used as a clue)
  presumes   — a grader call: does the line say something happened that the child never said?
               (e.g. 「미끄럼틀 탔구나!」 when the child only drew a slide). Lower is better
  choices    — pick-level cases: every choice spoken comes from the drawing (C only claims this)

  py eval/bench_diary_drawing.py [--rounds 3] [--before origin/main] --yes-spend

Costs about a hundred won (gpt-6-luna · 2 calls × 3 arms × rounds × cases).
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
from app.llm.judge_prompt import _blocks  # noqa: E402
from app.routers import turn  # noqa: E402
from app.schemas.judge import JudgeResult  # noqa: E402
from app.schemas.turn import TurnRequest  # noqa: E402

ROUNDS = int(sys.argv[sys.argv.index("--rounds") + 1]) if "--rounds" in sys.argv else 3
BEFORE = sys.argv[sys.argv.index("--before") + 1] if "--before" in sys.argv else "origin/main"
OUT = ROOT / "eval" / "raw" / "diary_drawing_1005.jsonl"

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
    tmp = ROOT / "eval" / "_line_prompt_before.md"
    tmp.write_text(git_file(BEFORE, "eval/line_prompt.md"), encoding="utf-8")
    try:
        return _blocks.system_block(tmp)
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
        sys.exit("costs about a hundred won — pass --yes-spend")
    old, new = before_system(), turn.system()
    arms = {"A": (old, False), "B": (old, True), "C": (new, True)}
    rows = []
    for r in range(ROUNDS):
        order = list(arms.items())
        order = order[r % 3:] + order[:r % 3]
        for case in CASES:
            for arm, (system, drawn_sent) in order:
                line = await one(system, case, drawn_sent)
                if line is None:
                    rows.append({"round": r, "arm": arm, "id": case[0], "failed": True})
                    continue
                q = line.get("question")
                ok, why = rules_ok(q)
                spoken = " ".join(x for x in (line.get("ack"), line.get("expand"), q) if x)
                g = await grade(case, line)
                choices = spoken_choices(q or "") if case[7] == "pick" else []
                rows.append({
                    "round": r, "arm": arm, "id": case[0], "line": line, "rules_ok": ok, "rules_why": why,
                    "clue": any(w in spoken and w not in said_so_far(case) for w in case[1]),"presumes": g.get("presumes"), "presumes_what": g.get("what"),
                    "choices": choices, "choices_from_drawing": (all(any(w in c or c in w for w in case[1]) for c in choices) if choices else None),
                })
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in rows) + "\n", encoding="utf-8")

    def share(arm, key, want=True):
        xs = [x for x in rows if x["arm"] == arm and not x.get("failed") and x.get(key) is not None]
        return f"{sum(1 for x in xs if x[key] == want)}/{len(xs)}"

    print(f"\n{'arm':<14}{'rules ok':>10}{'clue':>15}{'presumes':>10}{'choices∈drawing':>17}{'failed':>8}")
    for arm, label in (("A", "A now"), ("B", "B drawn only"), ("C", "C drawn+rule")):
        failed = sum(1 for x in rows if x["arm"] == arm and x.get("failed"))
        print(f"{label:<14}{share(arm, 'rules_ok'):>10}{share(arm, 'clue'):>15}{share(arm, 'presumes'):>10}{share(arm, 'choices_from_drawing'):>17}{failed:>8}")
    print("\nround 0 lines:")
    for x in rows:
        if x["round"] == 0 and not x.get("failed"):
            ln = x["line"]
            flag = ("" if x["rules_ok"] else f" [rules: {x['rules_why']}]") + (" [PRESUMES: " + str(x["presumes_what"]) + "]" if x["presumes"] else "")
            print(f"{x['id']} {x['arm']}  {ln.get('ack')} / {ln.get('expand')} / {ln.get('question')}{flag}")
    print(f"\n→ {OUT}")


if __name__ == "__main__":
    asyncio.run(main())
