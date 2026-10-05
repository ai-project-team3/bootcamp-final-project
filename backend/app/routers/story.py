"""POST /story — once or twice per session. Latency budget is generous.

The prompt is read from eval/, not copied (one thing in one place):
story → story_prompt.md, diary · coop → story_prompt_diary.md.
"""
import json
from functools import lru_cache

from fastapi import APIRouter, HTTPException

from ..config import REPO, settings
from ..filters.blocklist import has_unknown_placeholder, is_blocked
from ..llm.client import LLMError, complete
from ..llm.judge_prompt import load_schema, system_block
from ..schemas.story import Scene, StoryRequest, StoryResult

router = APIRouter()
EVAL = REPO / "eval"

# story: exactly six (spec §3-3). diary · coop: as many as the day filled.
# diary from 1: a day with only place + what happened is one honest page — the prompt says
# "pages as filled, invent nothing", and 3 at least turned that answer into a 502 (#39 · 10-01)
_SCENES = {"story": (6, 6), "diary": (1, 6), "coop": (3, 6)}


@lru_cache(maxsize=4)
def system(mode: str) -> str:
    # same cut as the judge and the measurement (eval/prompt_block.py): the fenced block, input dropped
    return system_block(EVAL / ("story_prompt.md" if mode == "story" else "story_prompt_diary.md"))


def schema() -> dict:
    return load_schema("story_schema.json")


# What each page is for. The app's PageKind names; the meanings follow StoryBank.kt templates.
KIND_MEANING = {
    "COVER": "표지 — 책 제목 한 줄",
    "DEPART": "떠남 — 장소로 나선다",
    "SHAKE": "사건 — 문제가 생긴다",
    "MEET": "만남 — 새 인물을 만난다",
    "TALK": "말하기 — 누군가 말하거나 알린다",
    "JOURNEY": "지나감 — 여러 곳을 지나간다",
    "FAIL": "시도 — 해 봤지만 잘 안 된다",
    "RUB": "미션 1 자리 — 곤란한 상황으로 끝낸다. 여기서 풀지 않는다",
    "DRAG": "미션 2 자리 — 풀기 직전의 상황으로 끝낸다. 결과는 쓰지 않는다",
    "TOGETHER": "마무리 — 함께 안전하게 끝난다",
}

# docs/미션_구상.md §3 · 맞춤미션_설계.md §4 (★ six added 10-05 for #101) — the situation the page must end on, so the child's hands can
# solve it next. Never the result: what the page says after the mission is the app's
# (today a banner, not the caption — 최민우 09-29, Scenes.kt).
MISSION_SETUP = {
    "A1": "물대포로 끄기 — 불이나 연기가 번진 상황",
    "A2": "도형 블록 넣기 — 블록이나 장난감이 흩어진 상황",
    "A3": "그림 퍼즐 — 아이가 이 쪽 그림을 조각 맞춰 완성한다",
    "A4": "손잡이 돌려 잠그기 — 물이 넘치거나 새는 상황",
    "A5": "쌓기 — 쌓은 것이 무너진 상황",
    "A6": "문지르기 — 무언가 묻거나 덮여 가려진 상황",
    "A7": "길게 누르기 — 무언가가 자라거나 차오르기를 기다려야 하는 상황",
    "A8": "선 따라 잇기 — 길이나 다리가 끊겨 이어 줘야 하는 상황",
    "A9": "벌려 키우기 — 작은 것이 커져야 하는 상황",
    "B1": "같은 것끼리 모으기 — 여러 물건이 뒤섞인 상황",
    "B2": "순서 맞추기 — 있었던 일을 차례대로 떠올리는 자리",
    "B3": "찾아 비추기 — 잃어버린 것을 어둠 속에서 찾아야 하는 상황",
    "C1": "불어서 날리기 — 촛불 · 먼지 · 민들레를 불어야 하는 상황",
    "C2": "불러서 오게 하기 — 친구가 멀리 있어 불러야 하는 상황",
    "C3": "소리 흉내 — 탈것이나 동물이 소리를 내야 움직이거나 깨어나는 상황",
    "D1": "연타로 밀어내기 — 무언가가 밀고 들어와 쫓아내야 하는 상황. 싸우지 않고 밀어낸다",
    "D2": "흔들어 털기 — 열매나 먼지를 털어야 하는 상황",
    "D3": "박자 맞춰 두드리기 — 노래나 춤이 시작되는 자리",
    "D4": "기울여 굴리기 — 공이나 구슬을 굴려 길을 찾아가야 하는 상황",
    "D5": "뒤집기 — 무언가가 뒤집혀 있거나 쏟아질 듯한 상황",
    "E1": "건네주기 — 친구에게 무언가를 건네줄 상황",
    "E2": "고쳐 주기 — 무언가가 부서진 상황",
}


# Missions played on the page itself, not on a problem in the story. Setting one up as
# "a situation" would put an event in the book that never happened (최민우 09-29: the
# puzzle is the scene picture cut into pieces, not something that broke in the story).
NO_SITUATION = {"A3", "B2", "D3"}


def plan(req: StoryRequest) -> str:
    """The page list as the model reads it. Takes the place of the fixed scene order."""
    lines = [f"[쪽 목록] 정확히 {len(req.pages)}쪽. 이 차례와 개수를 그대로 따른다 — 위 장면 구성보다 우선한다."]
    for i, pg in enumerate(req.pages, 1):
        if pg.mission in NO_SITUATION:
            line = (f"{i} {pg.kind} : 이야기를 한 걸음 잇는 평범한 장면 · 미션 {pg.mission} {MISSION_SETUP[pg.mission]}."
                    " 문장에는 미션 · 조각 · 퍼즐 이야기를 넣지 않고 새 사건도 만들지 않는다")
        else:
            line = f"{i} {pg.kind} : {KIND_MEANING[pg.kind]}"
            if pg.mission:
                line += (f" · 미션 {pg.mission} {MISSION_SETUP[pg.mission]}. 이 상황으로 끝내고 풀지 않는다."
                         " 미션 이름 · 도구 이름 · '직전' 같은 설명 말은 쓰지 않고 이야기 속 장면으로만 보여 준다")
        lines.append(line)
    if req.mode != "story":
        lines.append(f"{TENSE[req.reason if req.mode == 'coop' else None]} 미션 쪽도 칸에 있는 일로만 쓰고, 없던 일을 지어내지 않는다.")
    return "\n".join(lines)


# What kind of day the book is about. diary is always a day that happened; coop follows the
# reason the parent picked (#52) — a field trip next week must not come out as "다녀왔어요".
TENSE = {
    None: "있었던 일이다.",
    "done": "있었던 일이다.",
    "soon": "앞으로 할 일이다. 아직 일어나지 않았으니 「~할 거예요」 · 「~할까요?」처럼 기대하는 말로 쓰고, 이미 한 일처럼 쓰지 않는다.",
    "dream": "아이가 좋아해서 상상한 이야기다. 그림책처럼 지난 일로 써도 되지만, 실제로 있었던 일이라고 말하지 않는다.",
}


def user(req: StoryRequest) -> str:
    slots = json.dumps(req.slots, ensure_ascii=False, separators=(",", ":"))
    tail = f"\n{plan(req)}" if req.pages else ""
    if req.mode == "story":
        return (f"[입력 슬롯]\n{slots}\n"
                f"이야기 템플릿: {req.template or '(없음)'}\n아이 수준: {req.level or '(없음)'}{tail}")
    by = json.dumps(req.slot_by, ensure_ascii=False, separators=(",", ":"))
    keep = json.dumps(req.keep, ensure_ascii=False)
    head = f"모드: {req.mode}\n"
    if req.mode == "coop":
        # without a page plan the tense still has to reach the model
        head += f"고른 이야기: {req.template or '(없음)'}\n" + ("" if req.pages else f"{TENSE[req.reason]}\n")
    return f"{head}채워진 칸: {slots}\n칸마다 by: {by}\n맺음: {keep}{tail}"


def check(result: StoryResult, mode: str, pages: list | None = None) -> str | None:
    """Why this book must not reach the child, or None. A bad book is thrown away, not patched."""
    if pages:
        # the app's plan: a page short or extra would put a mission on the wrong page
        if len(result.scenes) != len(pages):
            return f"{len(result.scenes)} scenes (want {len(pages)} pages)"
    else:
        lo, hi = _SCENES[mode]
        if not lo <= len(result.scenes) <= hi:
            return f"{len(result.scenes)} scenes (want {lo}-{hi})"
    for sc in result.scenes:
        if is_blocked(sc.caption):
            return f"blocked word in scene {sc.index}"
        if has_unknown_placeholder(sc.caption):
            return f"unknown placeholder in scene {sc.index}"
    return None


def stamp(result: StoryResult, req: StoryRequest) -> StoryResult:
    """Page kinds come from the request, in order — the app's plan, never the model's word."""
    for i, sc in enumerate(result.scenes):
        sc.index = i + 1
        sc.kind = req.pages[i].kind if req.pages else None
    return result


def mock(req: StoryRequest) -> StoryResult:
    filled = [v for v in req.slots.values() if v]
    n = len(req.pages) if req.pages else _SCENES[req.mode][1]
    caps = [f"{{주인공}}은 {v} 이야기를 했어요." for v in filled][:n]
    caps += ["{주인공}은 오늘 이야기를 마쳤어요."] * (n - len(caps))
    return StoryResult(scenes=[Scene(index=i + 1, caption=c, keywords="paper cutout")
                               for i, c in enumerate(caps)])


@router.post("/story", response_model=StoryResult)
async def story(req: StoryRequest) -> StoryResult:
    if settings.mock:
        return stamp(mock(req), req)
    try:
        raw = await complete(system(req.mode), user(req), schema(), name="story",
                             effort=settings.llm_effort_story, max_output_tokens=6000,
                             timeout_s=settings.story_deadline_s)
    except LLMError as e:
        raise HTTPException(502, str(e)) from e
    result = StoryResult.model_validate(raw)
    why = check(result, req.mode, req.pages)
    if why:
        # the phone falls back to its own template book (spec §3-0: the app never stops)
        raise HTTPException(502, f"story rejected: {why}")
    return stamp(result, req)
