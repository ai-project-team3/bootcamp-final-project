"""판정 평가셋 100개를 층화 추출한다 (역할 1 · 안치영 · 2026-09-18).

기준: 노션 「오늘 모델 검증 — 4명 실행 지시서」 §2 (9/18 설계 변경 반영).
칸 이름의 유일한 출처는 `../../guidelines/2_공통_데이터_모델.md` §1-1 (슬롯 12종 닫힌 목록).
  - 안치영은 **라벨 없이 추출만** 한다 (라벨은 박진웅이 100개 전부 붙인다)
  - 유형별 몫: 정답형 25 · S1 20 · S2 20 · 필수해제 10 · 무관 10 · 미완결 15
  - **`-서` 함정을 S1 20개 안에 최소 5개** — 인과 3 + 순차 2. 순차는 s1_reason = false다
  - **S2 20개 중 10개는 다중채움**(한 말이 칸 둘을 채움), **필수해제 10개** — 이번 설계 변경의 핵심 지표
  - 판정기는 질문 한 줄이 아니라 **칸 전체(`slots`) + 지금 묻는 칸(`asked`) + 템플릿**을 받는다 (§1-1)

입력: 앱 더미답 덤프 `bank_dump.jsonl` (android 의 EvalDumpTest.kt 가 만든다)
      경로는 `--dump` 인자 또는 환경변수 `BANK_DUMP` 로 준다. 기본값은 아래 DEFAULT_DUMP.
출력: eval/fixtures_judge.jsonl                                 (100줄 · gold 는 null)

왜 층화인가
  판정 대상 더미답 774줄에서 무작위로 100개를 뽑으면 흔한 유형만 들어오고
  "몰라" 같은 미완결은 2개, 무관·필수해제·다중채움은 0개가 된다. 유형별 F1을 볼 수 없다.

돌리는 법
  1) 앱 쪽에서 덤프를 만든다
     ./gradlew :app:testDebugUnitTest --tests "*EvalDumpTest*"   # → app/build/bank_dump.jsonl
  2) cd eval
     python tools/make_fixtures.py --dump <그 경로>

⚠️ 씨앗값(SEED)이 고정이라 같은 덤프에서는 **같은 100문항**이 다시 나온다.
   다시 뽑으면 확정한 `gold` 가 지워지므로, 확정 뒤에는 `labels_*.jsonl` 을 따로 보관한다.
"""

import argparse
import json
import os
import random
import re
from pathlib import Path

# 덤프는 레포 밖(안드로이드 빌드 산출물)에 있다. --dump 나 BANK_DUMP 로 덮어쓴다
DEFAULT_DUMP = Path("C:/Android/finalproject_demo/app/build/bank_dump.jsonl")
HERE = Path(__file__).resolve().parents[1]
OUT_JUDGE = HERE / "fixtures_judge.jsonl"

SEED = 20260918  # 같은 평가셋이 다시 나오도록 고정
QUOTA = {"정답형": 25, "S1": 20, "S2": 20, "필수해제": 10, "무관": 10, "미완결": 15}
MULTI_FILL_N = 10  # S2 20개 중 사람이 쓴 다중채움 (지시서 §2-4 "최소 10개")
EXTRA_IN_PLAIN = 5  # 정답형 25개 중 `extra` 를 묻는 문항의 상한

# 인과의 `-서` — "무서워서 울었어". 순차의 `-서`("가서 먹었어")와 겹치는 형태라 여기서 모델이 갈린다
CAUSAL_SEO = re.compile(r"(아파서|싶어서|없어서|커서|보여서|생겨서|작아서|놀라서|어서!|해서!)")


# ── 칸(슬롯) ─────────────────────────────────────────────────────
# 판정 스키마의 고정 enum (지시서 §1-1 · 구현대본 §2). `extra` 는 칸 상태에는 없고 asked 에만 쓴다
SLOTS = ["place", "problem", "reaction", "cause", "newcomer", "name",
         "companion", "sound", "adult", "solution", "title"]
# 마스코트가 대체로 묻는 차례. companion · adult 는 아이나 어른이 먼저 말할 때만 차므로 뺀다
ORDER = ["place", "problem", "reaction", "cause", "newcomer", "name", "sound", "solution", "title"]

# 데모 v0.9 의 세 세계에서 "앞서 정해진 값"으로 쓸 것
WORLD = {
    "space": {"place": "우주", "problem": "외계인이 로켓을 흔들었어", "reaction": "로켓이 삐뚤빼뚤 갔어",
              "cause": "심심해서 같이 놀고 싶었대", "newcomer": "외계인", "name": "뿌뿌",
              "sound": "삐리삐리", "solution": "같이 숨바꼭질을 했어"},
    "sea": {"place": "바닷속", "problem": "문어가 거북이를 흔들었어", "reaction": "거북이가 기우뚱했어",
            "cause": "배가 고파서 그랬대", "newcomer": "문어", "name": "뿌뿌",
            "sound": "뽀글뽀글", "solution": "조개를 나눠 먹었어"},
    "dino": {"place": "공룡 나라", "problem": "아기 공룡이 기차를 흔들었어", "reaction": "기차가 덜컹 멈췄어",
             "cause": "엄마를 잃어버려서 그랬대", "newcomer": "아기 공룡", "name": "뿌뿌",
             "sound": "크아앙", "solution": "엄마를 같이 찾아 줬어"},
}

# 앱의 칸 이름 → (지금 묻는 칸, 그때까지 차 있는 칸).
# 앱 v0.9 는 칸을 19개로 잘게 나눠 묻는다. 새 enum 에 없는 것은 `extra` 로 보낸다
APP_SLOT = {
    "sight":       ("extra",    ["place"]),
    "stop":        ("place",    ["newcomer", "name"]),          # "어디를 지나가 볼까?" — 장소가 아직 없다
    "follow":      ("reaction", ["place", "problem"]),
    "cause":       ("cause",    ["place", "problem", "reaction"]),
    "hypo":        ("extra",    ["place", "problem", "reaction", "cause"]),
    "name":        ("name",     ["place", "problem", "reaction", "cause", "newcomer"]),
    "saw":         ("extra",    ORDER[:6]),
    "goal":        ("extra",    ORDER[:6]),
    "need":        ("extra",    ORDER[:6]),
    "role":        ("extra",    ORDER[:6]),
    "consequence": ("extra",    ORDER[:6]),
    "fail":        ("extra",    ORDER[:7]),
    "try1":        ("solution", ORDER[:7]),
    "response":    ("solution", ORDER[:7]),
    "helper":      ("solution", ORDER[:7]),
    "resolve":     ("solution", ORDER[:7]),
    "lesson":      ("extra",    ORDER[:8]),
    "feel":        ("extra",    ORDER[:8]),
    "reflect":     ("extra",    ORDER[:8]),
}
QID_ASKED = {"follow_who": "extra"}  # "누가 제일 놀랐을까?" 는 reaction 이 아니다
TEMPLATE_OF_PREFIX = {"a": "A", "c": "C", "d": "D", "e": "E", "g": "G"}  # qid 앞글자 = 뼈대


# 이름은 폰에만 둔다. 판정기에는 자리표시자로 들어간다 (지시서 §1-2 · 조사3 §3-2)
# 데모 v0.9 의 주인공은 "지호", 새 친구는 "뿌뿌"로 박혀 있다. 조사가 받침을 안 타는 이름이라 그대로 갈아 끼워도 된다
NAMES = {"지호": "{주인공}", "뿌뿌": "{친구1}"}


def mask(text: str | None) -> str | None:
    """실명 → 자리표시자. 아이가 **방금 새로 지은 이름**("콩콩이")은 아직 사전에 없으므로 그대로 둔다."""
    if text is None:
        return None
    for real, holder in NAMES.items():
        text = text.replace(real, holder)
    return text


def state(theme: str | None, filled: list[str], **over: str | None) -> dict:
    """칸 전체의 상태. 안 찬 칸은 null 로 둔다 — 판정기가 '뭐가 비었나'를 봐야 next_slot 을 정한다."""
    s: dict[str, str | None] = {k: None for k in SLOTS}
    for k in filled:
        s[k] = WORLD[theme][k]
    s.update(over)
    return s


# ── 코드에 없는 말은 사람이 쓴다 ────────────────────────────────────
# (세계, 지금 묻는 칸, 템플릿, 찬 칸, 덮어쓸 값, 질문, 아이 말)
Hand = tuple[str | None, str, str, list[str], dict, str, str]

# 순차 `-서` 함정: 형태는 인과와 같지만 **일이 일어난 차례**를 말한 것이라 s1_reason 이 아니다
TRAP_SEQUENTIAL: list[Hand] = [
    ("dino", "reaction", "C", ["place", "problem"], {}, "그래서 어떻게 됐어?", "가서 먹었어."),
    ("sea", "cause", "C", ["place", "problem", "reaction"], {}, "문어는 왜 그랬을까?", "일어나서 밥 먹었어."),
]

# S2-다중채움: 한 말이 **칸 둘**을 채운다. 둘째 칸은 상태에서 비어 있어야 한다
MULTI_FILL: list[Hand] = [
    (None, "place", "C", [], {}, "오늘은 어디로 가 볼까?", "공룡나라 갈래 근데 뿌뿌도 데려갈래."),
    (None, "place", "E", [], {}, "오늘은 어디로 가 볼까?", "바닷속! 우리 강아지 보리랑 같이 갈래."),
    (None, "place", "A", [], {}, "오늘은 어디로 가 볼까?", "공룡 나라 갈래. 근데 가다가 기차가 멈췄어."),
    ("space", "problem", "C", ["place"], {}, "어? 로켓이 흔들려. 왜 그럴까?", "외계인이 흔들었어. 심심해서 그랬대."),
    ("sea", "problem", "G", ["place"], {}, "어? 거북이가 흔들려. 왜 그럴까?", "문어가 거북이를 흔들었어. 그래서 다 깜짝 놀랐어."),
    ("sea", "reaction", "C", ["place", "problem"], {}, "그래서 어떻게 됐어?", "거북이가 기우뚱했어. 문어가 배고파서 그런 거야."),
    ("space", "cause", "C", ["place", "problem", "reaction"], {}, "외계인은 왜 그랬을까?", "같이 놀고 싶어서 그랬어. 그러니까 같이 숨바꼭질하면 돼."),
    ("space", "newcomer", "A", ORDER[:4], {}, "새 친구는 어떻게 생겼어?", "눈이 세 개야. 이름은 콩콩이야."),
    ("dino", "name", "D", ORDER[:5], {}, "아기 공룡 이름은 뭐야?", "쿵쿵이! 쿵쿵이는 크아앙 하고 울어."),
    ("dino", "solution", "D", ORDER[:7], {}, "아기 공룡이랑 어떻게 친해질까?", "사과를 나눠 먹으면 돼. 책 이름은 사과 친구들이야."),
]

# 필수해제: 아이 말 때문에 **그 칸이 더는 필요 없어진다**. 칸을 채운 게 아니라 없앤 것이다
RELEASE: list[Hand] = [
    ("sea", "companion", "C", ["place"], {}, "누구랑 같이 갈까?", "혼자 갔어."),
    ("space", "companion", "E", ["place"], {}, "누구랑 같이 갈까?", "아무도 안 데려가. 나 혼자 갈 거야."),
    ("dino", "companion", "A", ["place"], {}, "같이 갈 친구가 있어?", "멍멍이는 집에 있으래. 나만 갈래."),
    ("dino", "sound", "C", ORDER[:6], {}, "아기 공룡은 어떻게 울어?", "안 울어. 소리 안 내."),
    ("sea", "sound", "G", ORDER[:6], {}, "문어는 어떤 소리를 낼까?", "문어는 말 못 해. 조용한 애야."),
    ("space", "newcomer", "A", ORDER[:4], {"problem": "로켓이 흔들렸어", "cause": "운석이 쿵 부딪혀서 그랬대"},
     "새 친구는 어떻게 생겼어?", "친구 안 나와. 아무도 없었어."),
    ("space", "name", "C", ORDER[:5], {}, "외계인 이름은 뭐야?", "이름 없어. 그냥 외계인이야."),
    ("sea", "solution", "C", ORDER[:7], {}, "문어랑 어떻게 친해질까?", "안 친해져도 돼. 문어는 그냥 집에 갔어."),
    ("dino", "problem", "E", ["place"], {}, "어? 무슨 일이 생겼을까?", "아무 일도 없었어. 그냥 재밌게 놀았어."),
    ("space", "title", "A", ORDER[:8], {}, "이 책 이름은 뭐라고 할까?", "이름 없어도 돼. 그냥 책이야."),
]

# 무관 · 엉뚱: 칸을 채울 수 없는 말. 뒤의 4개는 앞서 정한 것을 뒤집는 말이다(contradiction)
IRRELEVANT: list[Hand] = [
    (None, "place", "C", [], {}, "오늘은 어디로 가 볼까?", "엄마 어디 있어?"),
    ("space", "problem", "C", ["place"], {}, "어? 로켓이 흔들려. 왜 그럴까?", "이거 뭐야? 이 버튼 뭐야?"),
    ("space", "cause", "A", ["place", "problem", "reaction"], {}, "외계인은 왜 그랬을까?", "어제 어린이집에서 블록 쌓았어."),
    ("sea", "reaction", "G", ["place", "problem"], {}, "그래서 어떻게 됐어?", "(노래) 상어 가족 뚜루루뚜루~"),
    ("dino", "sound", "D", ORDER[:6], {}, "아기 공룡은 어떤 소리를 낼까?", "아빠 언제 와?"),
    ("sea", "solution", "C", ORDER[:7], {}, "어떻게 하면 뿌뿌랑 사이좋게 지낼 수 있을까?", "티브이 볼래."),
    ("space", "problem", "E", ["place"], {}, "어? 로켓이 흔들려. 왜 그럴까?", "우주 안 갈래. 바닷속 갈 거야."),
    ("space", "reaction", "C", ["place", "problem"], {}, "그래서 어떻게 됐어?", "아무도 안 흔들었어. 그냥 흔들린 거야."),
    ("space", "cause", "C", ["place", "problem", "reaction"], {}, "외계인은 왜 그랬을까?", "외계인 없어. 내가 지어낸 거야."),
    ("dino", "solution", "G", ORDER[:7], {}, "어떻게 하면 뿌뿌랑 사이좋게 지낼 수 있을까?", "뿌뿌 미워. 안 놀 거야."),
]

# 미완결 · 뭉개진 말 (음성 인식이 흐린 경우)
INCOMPLETE: list[Hand] = [
    (None, "place", "C", [], {}, "오늘은 어디로 가 볼까?", "공뇽…"),
    ("space", "problem", "A", ["place"], {}, "어? 로켓이 흔들려. 왜 그럴까?", "어… 그… 음…"),
    ("sea", "reaction", "C", ["place", "problem"], {}, "그래서 어떻게 됐어?", "(기침) 그래서… 어…"),
]


def stratum_of(row: dict) -> str | None:
    """덤프 한 줄이 어느 유형인가. 유형은 서로 겹치지 않게 정한다(라벨은 겹칠 수 있다)."""
    if row["source"] != "bank":
        return None  # 함께 하는 사람 답은 판정 대상이 아니다
    if row["words"] <= 2 and not row["s1"] and not row["s2"]:
        return "미완결"
    if row["s1"]:
        return "S1"
    if row["s2"]:
        return "S2"
    if row["value"]:
        return "정답형"
    return None


def pick(rows: list[dict], n: int, rnd: random.Random, taken: set[str]) -> list[dict]:
    """같은 말 · 같은 질문이 몰리지 않게 고르게 뽑는다."""
    chosen: list[dict] = []
    seen_q: dict[str, int] = {}
    rnd.shuffle(rows)
    for cap in (1, 2, 99):  # 질문 하나당 1개 → 2개 → 제한 없음 순으로 채운다
        for r in rows:
            if len(chosen) >= n:
                return chosen
            if r["utterance"] in taken:
                continue
            if seen_q.get(r["question"], 0) >= cap:
                continue
            taken.add(r["utterance"])
            seen_q[r["question"]] = seen_q.get(r["question"], 0) + 1
            chosen.append(r)
    return chosen


def item(slots: dict, asked: str, template: str, context: str, utterance: str,
         type_: str, origin: str, meta: dict) -> dict:
    """지시서 §2-3 형식. gold 는 비워 둔다 — 박진웅이 채운다. id 는 섞은 뒤에 매긴다."""
    return {
        "id": "",
        "slots": {k: mask(v) for k, v in slots.items()},  # 칸 전체의 지금 상태 (안 찬 칸은 null)
        "asked": asked,         # 지금 묻는 칸
        "template": template,   # 이야기 뼈대 A · C · D · E · G
        "context": mask(context),  # 그때 마스코트가 던진 질문 (라벨링할 때 사람이 읽는다)
        "utterance": mask(utterance),
        "gold": None,
        "type": type_,
        "origin": origin,       # bank = 데모 더미답에서 뽑음 · handwritten = 사람이 씀
        "meta": meta,           # 러너는 안 봐도 된다. 라벨링할 때 참고용
    }


def from_bank(r: dict, type_: str, rnd: random.Random) -> dict:
    asked, filled = APP_SLOT[r["slot"]]
    asked = QID_ASKED.get(r["qid"], asked)
    prefix = r["qid"].split("_")[0]
    template = TEMPLATE_OF_PREFIX.get(prefix) or rnd.choice("ACDEG")  # 공통 질문은 뼈대를 안 가린다
    return item(state(r["theme"], filled), asked, template, r["question"], r["utterance"], type_, "bank",
                {"qid": r["qid"], "app_slot": r["slot"], "probe": r["probe"]})


def from_hand(h: Hand, type_: str, note: str = "") -> dict:
    theme, asked, template, filled, over, context, utterance = h
    return item(state(theme, filled, **over), asked, template, context, utterance, type_, "handwritten",
                {"주의": note} if note else {})


def resolve_dump(arg: str | None) -> Path:
    """--dump > 환경변수 BANK_DUMP > DEFAULT_DUMP 순으로 고른다.

    ⚠️ 사람이 **직접 준** 경로(--dump · BANK_DUMP)가 없으면 조용히 기본값으로 넘어가지 않고 바로 멈춘다.
       엉뚱한 덤프로 평가셋이 다시 뽑히면 아무도 알아채지 못한다.
    """
    for cand, src in ((arg, "--dump"), (os.environ.get("BANK_DUMP"), "BANK_DUMP")):
        if cand:
            path = Path(cand)
            if not path.is_file():
                raise SystemExit(f"{src} 로 준 경로에 파일이 없다: {path}")
            return path
    if DEFAULT_DUMP.is_file():
        return DEFAULT_DUMP
    raise SystemExit(
        "덤프를 찾을 수 없다. 앱 쪽에서 먼저 만든 뒤 경로를 준다:\n"
        '  ./gradlew :app:testDebugUnitTest --tests "*EvalDumpTest*"\n'
        "  python tools/make_fixtures.py --dump <app/build/bank_dump.jsonl 경로>\n"
        f"  (찾아본 기본 경로: {DEFAULT_DUMP})")


def guard_existing_gold(force: bool) -> None:
    """⚠️ 이미 확정된 gold 를 **조용히 날리지 않는다.**

    이 스크립트는 픽스처를 `gold: null` 로 다시 쓴다. 라벨 100개가 들어 있는 파일에 그냥 덮어쓰면
    사람이 몇 시간 붙인 정답지가 사라지고, 되살릴 방법이 없다. README 의 경고만으로는 막히지 않는다.
    """
    if not OUT_JUDGE.exists():
        return
    try:
        rows = [json.loads(l) for l in OUT_JUDGE.read_text(encoding="utf-8").splitlines() if l.strip()]
    except (OSError, json.JSONDecodeError):
        return   # 읽을 수 없으면 덮어쓰기를 막을 근거도 없다
    filled = sum(1 for r in rows if r.get("gold"))
    if not filled:
        return
    if force:
        print(f"⚠️ --force — gold {filled}개를 덮어쓴다")
        return
    raise SystemExit(
        f"{OUT_JUDGE.name} 에 이미 확정된 gold 가 {filled}개 있다. 다시 뽑으면 **전부 사라진다.**\n"
        "먼저 빼내 두고, 뽑은 뒤 되돌려 넣는다:\n"
        "  python tools/gold.py --export --by <이름>\n"
        "  python tools/make_fixtures.py --dump <경로>\n"
        "  python tools/gold.py --apply  --by <이름>\n"
        "정말 버릴 생각이면 --force 를 붙인다.")


def main() -> None:
    ap = argparse.ArgumentParser(description="판정 평가셋 100문항을 층화 추출한다")
    ap.add_argument("--dump", default=None, help="bank_dump.jsonl 경로 (없으면 BANK_DUMP 환경변수)")
    ap.add_argument("--force", action="store_true",
                    help="확정된 gold 가 있어도 덮어쓴다 (라벨이 사라진다)")
    args = ap.parse_args()
    guard_existing_gold(args.force)
    dump = resolve_dump(args.dump)

    rnd = random.Random(SEED)
    rows = [json.loads(l) for l in dump.read_text(encoding="utf-8").splitlines() if l.strip()]

    by_type: dict[str, list[dict]] = {}
    for r in rows:
        st = stratum_of(r)
        if st:
            by_type.setdefault(st, []).append(r)

    taken: set[str] = set()
    items: list[dict] = []

    # ① S1 20개 — 인과 `-서` 3개를 먼저 채우고, 순차 함정 2개는 사람이 쓴 것으로 넣는다
    causal = [r for r in by_type["S1"] if CAUSAL_SEO.search(r["utterance"])]
    other_s1 = [r for r in by_type["S1"] if not CAUSAL_SEO.search(r["utterance"])]
    picked_causal = pick(causal, 3, rnd, taken)
    picked_other = pick(other_s1, QUOTA["S1"] - len(picked_causal) - len(TRAP_SEQUENTIAL), rnd, taken)
    items += [from_bank(r, "S1-인과서", rnd) for r in picked_causal]
    items += [from_bank(r, "S1", rnd) for r in picked_other]
    items += [from_hand(h, "S1-순차함정", "형태는 -서지만 차례를 말한 것이라 s1_reason = false")
              for h in TRAP_SEQUENTIAL]

    # ② 정답형 — 더미답에서. `extra` 로 가는 질문이 절반이라 그냥 뽑으면 slot_1 F1 이 extra 맞히기가 된다
    #    → enum 의 진짜 칸을 묻는 것 20 + extra 5
    def asked_of(r: dict) -> str:
        return QID_ASKED.get(r["qid"], APP_SLOT[r["slot"]][0])
    real = [r for r in by_type["정답형"] if asked_of(r) != "extra"]
    extra = [r for r in by_type["정답형"] if asked_of(r) == "extra"]
    items += [from_bank(r, "정답형", rnd) for r in pick(real, QUOTA["정답형"] - EXTRA_IN_PLAIN, rnd, taken)]
    items += [from_bank(r, "정답형", rnd) for r in pick(extra, EXTRA_IN_PLAIN, rnd, taken)]

    # ③ S2 20개 — 다중채움 10개는 사람이 쓴 것(더미답은 칸 하나만 채우게 만들어져 있다), 나머지는 더미답에서
    items += [from_hand(h, "S2-다중채움", "한 말이 칸 둘을 채운다 → slot_1 + slot_2")
              for h in MULTI_FILL[:MULTI_FILL_N]]
    items += [from_bank(r, "S2", rnd) for r in pick(by_type["S2"], QUOTA["S2"] - MULTI_FILL_N, rnd, taken)]

    # ④ 필수해제 10 · 무관 10 — 전부 사람이 쓴 것
    items += [from_hand(h, "필수해제", "칸을 채운 게 아니라 필요 없게 만들었다 → no_longer_needed")
              for h in RELEASE[:QUOTA["필수해제"]]]
    items += [from_hand(h, "무관") for h in IRRELEVANT[:QUOTA["무관"]]]

    # ⑤ 미완결 15 — 더미답 12 + 사람이 쓴 뭉개진 말 3
    items += [from_bank(r, "미완결", rnd)
              for r in pick(by_type["미완결"], QUOTA["미완결"] - len(INCOMPLETE), rnd, taken)]
    items += [from_hand(h, "미완결", "음성 인식이 뭉갠 말") for h in INCOMPLETE]

    # 라벨링할 때 유형이 뭉쳐 있으면 앞의 답에 끌려간다 → 섞는다
    rnd.shuffle(items)
    for i, it in enumerate(items, 1):
        it["id"] = f"j{i:03d}"

    # 지금 묻는 칸이 이미 차 있으면 문항이 잘못된 것이다
    for it in items:
        assert it["asked"] == "extra" or it["slots"][it["asked"]] is None, it

    OUT_JUDGE.write_text(
        "\n".join(json.dumps(it, ensure_ascii=False) for it in items) + "\n",
        encoding="utf-8", newline="\n")   # OS 상관없이 LF — 안 그러면 윈도우에서 CRLF 가 섞인다

    counts: dict[str, int] = {}
    for it in items:
        key = it["type"].split("-")[0]
        counts[key] = counts.get(key, 0) + 1
    seo_n = sum(1 for it in items if it["type"] in ("S1-인과서", "S1-순차함정"))
    multi_n = sum(1 for it in items if it["type"] == "S2-다중채움")
    hand_n = sum(1 for it in items if it["origin"] == "handwritten")
    asked_n: dict[str, int] = {}
    for it in items:
        asked_n[it["asked"]] = asked_n.get(it["asked"], 0) + 1

    print(f"{OUT_JUDGE.name}: {len(items)}줄 (gold 는 비어 있음 — 박진웅이 채운다)")
    for t, q in QUOTA.items():
        mark = "✔" if counts.get(t, 0) == q else "✘"
        print(f"  {t:5s} {counts.get(t, 0):3d} / 목표 {q}  {mark}")
    print(f"  -서 함정 {seo_n}개 (인과 3 · 순차 2) · 다중채움 {multi_n}개 · 사람이 쓴 문항 {hand_n}개")
    print("  묻는 칸: " + " · ".join(f"{k} {v}" for k, v in sorted(asked_n.items(), key=lambda kv: -kv[1])))


if __name__ == "__main__":
    main()
