"""/story with the app's page plan (09-29, 최민우): the app owns the book's shape and
which page carries which mission; the server writes the pages and never guesses them."""
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                    # noqa: E402
from app.routers import story as story_route       # noqa: E402
from app.schemas.story import Page, Scene, StoryRequest, StoryResult   # noqa: E402
from main import app                               # noqa: E402

# template C in StoryBank.kt: seven pages, missions on 4 and 6
C = [{"kind": "DEPART"}, {"kind": "SHAKE"}, {"kind": "TALK"}, {"kind": "RUB", "mission": "A6"},
     {"kind": "TALK"}, {"kind": "DRAG", "mission": "E1"}, {"kind": "TOGETHER"}]


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    return TestClient(app)


def test_the_book_has_exactly_the_planned_pages_with_their_kinds(client):
    r = client.post("/story", json={"slots": {"place": "공룡나라"}, "template": "C", "pages": C})
    scenes = r.json()["scenes"]
    assert [s["kind"] for s in scenes] == [p["kind"] for p in C]
    assert [s["index"] for s in scenes] == list(range(1, 8))


def test_without_a_plan_the_old_shape_stays(client):
    scenes = client.post("/story", json={"slots": {"place": "공룡나라"}}).json()["scenes"]
    assert len(scenes) == 6 and all(s["kind"] is None for s in scenes)


def test_an_unknown_kind_or_mission_is_refused(client):
    assert client.post("/story", json={"slots": {}, "pages": [{"kind": "BOSS"}]}).status_code == 422
    assert client.post("/story", json={"slots": {}, "pages": [{"kind": "RUB", "mission": "Z9"}]}).status_code == 422


# #86: the cover title rides along; a bad one is dropped, the book still goes
def test_a_bad_title_is_dropped_and_the_book_kept():
    from app.schemas.story import Scene, StoryResult
    req = StoryRequest(slots={})
    book = lambda t: StoryResult(title=t, scenes=[Scene(index=1, caption="{주인공}이 갔어요.", keywords="x")])  # noqa: E731
    assert story_route.stamp(book(" 공룡 나라의 불 끄기 "), req).title == "공룡 나라의 불 끄기"
    assert story_route.stamp(book("{이름}의 모험"), req).title is None
    assert story_route.stamp(book(""), req).title is None
    assert story_route.check(book("{이름}의 모험"), "story", [Page(kind="DEPART")]) is None   # title never rejects a book


# #101: the app may pick C3 (make the sound) once the server knows it — before 10-05 it was a 422
def test_the_missions_added_for_coop_are_accepted(client):
    for m in ("A7", "A8", "A9", "C3", "D4", "D5"):
        assert client.post("/story", json={"slots": {}, "pages": [{"kind": "RUB", "mission": m}]}).status_code != 422, m


def _book(n):
    return StoryResult(scenes=[Scene(index=i + 1, caption="가요.", keywords="x") for i in range(n)])


def test_a_page_short_or_extra_is_thrown_away():
    pages = [Page(**p) for p in C]
    assert story_route.check(_book(6), "story", pages), "one page short would move the missions"
    assert story_route.check(_book(8), "story", pages)
    assert story_route.check(_book(7), "story", pages) is None


def test_kinds_are_stamped_from_the_request_not_the_model():
    req = StoryRequest(slots={}, pages=[Page(**p) for p in C])
    out = story_route.stamp(_book(7), req)
    assert out.scenes[3].kind == "RUB" and out.scenes[5].kind == "DRAG"


def test_the_prompt_gets_the_plan_and_the_mission_setup_not_the_result():
    req = StoryRequest(slots={"place": "공룡나라"}, template="C", pages=[Page(**p) for p in C])
    text = story_route.user(req)
    assert "정확히 7쪽" in text
    assert "4 RUB" in text and "미션 A6" in text and "6 DRAG" in text and "미션 E1" in text
    assert "풀지 않는다" in text


def test_a_puzzle_page_gets_no_invented_situation():
    """A3 is the scene picture in pieces, not something that broke in the story."""
    req = StoryRequest(slots={}, pages=[Page(kind="DEPART"), Page(kind="DRAG", mission="A3")])
    line = story_route.user(req).splitlines()[-1]
    assert "미션 A3" in line and "새 사건도 만들지 않는다" in line
    assert "풀기 직전" not in line and "풀지 않는다" not in line


def test_the_page_count_is_not_the_apis_business():
    """6-8 is today's templates; the API only checks the answer matches the plan."""
    pages = [Page(kind="TALK")] * 12
    assert story_route.check(_book(12), "story", pages) is None


def test_a_diary_plan_forbids_inventing():
    req = StoryRequest(mode="diary", slots={"place": "놀이터"}, pages=[Page(kind="DEPART"), Page(kind="TOGETHER")])
    assert "지어내지 않는다" in story_route.user(req)


def test_every_mission_and_kind_has_a_meaning():
    from typing import get_args
    from app.schemas.story import MissionId, PageKindName
    assert set(get_args(MissionId)) == set(story_route.MISSION_SETUP)
    assert set(get_args(PageKindName)) == set(story_route.KIND_MEANING)


# #52: a coop book follows the reason the parent picked — "곧 체험해요" must not come out past tense
def test_coop_tense_follows_the_reason_the_parent_picked():
    def ask(reason, pages=None):
        return story_route.user(StoryRequest(mode="coop", slots={"place": "소방서"}, template="직업 · 소방관",
                                             reason=reason, pages=pages))
    assert "앞으로 할 일" in ask("soon") and "있었던 일" not in ask("soon")
    assert "상상한 이야기" in ask("dream")
    assert "있었던 일" in ask("done") and "있었던 일" in ask(None)
    assert "고른 이야기: 직업 · 소방관" in ask("done")
    # with a page plan the tense rides on the plan's last line, once
    with_plan = ask("soon", [Page(kind="DEPART"), Page(kind="TOGETHER")])
    assert with_plan.count("앞으로 할 일") == 1


def test_diary_ignores_a_reason_and_stays_a_day_that_happened():
    u = story_route.user(StoryRequest(mode="diary", slots={"place": "놀이터"}, reason="soon",
                                      pages=[Page(kind="DEPART")]))
    assert "있었던 일" in u and "앞으로 할 일" not in u and "고른 이야기" not in u


# 10-05: coop has its own prompt — the diary one said "오늘 있었던 일 · 과거형" and fought the soon/dream tense line
def test_each_mode_reads_its_own_prompt():
    story_route.system.cache_clear()
    coop, diary, tale = (story_route.system(m) for m in ("coop", "diary", "story"))
    assert coop != diary and coop != tale
    assert "고른 이야기" in coop and "앞으로 할 일" in coop and "상상한 이야기" in coop
    # the fenced block only: the input notes under it never reach the model
    assert "## 입력" not in coop and "정본 초안" not in coop


def test_the_coop_prompt_closes_a_soon_book_without_ending_the_day():
    coop = story_route.system("coop")
    assert "하루가 저물었어요" in coop and "그날이 정말 기다려져요" in coop


# #113: the spots inside the picked item reach the model as scenery, coop only
def test_the_coop_stage_reaches_the_model_as_scenery():
    def ask(**kw):
        return story_route.user(StoryRequest(mode="coop", slots={"place": "소방서"}, template="직업 · 소방관", **kw))
    assert "무대: 소방차 차고 · 출동 준비실 · 훈련장" in ask(stage=["소방차 차고", "출동 준비실", "훈련장"])
    assert "무대" not in ask()
    assert "무대" not in story_route.user(StoryRequest(mode="story", slots={"place": "공룡나라"}, stage=["동굴"]))
    assert "무대" in story_route.system("coop") and "새 사건을 만들지 않습니다" in story_route.system("coop")


def test_a_stage_too_long_or_too_many_is_refused(client):
    base = {"mode": "coop", "slots": {"place": "소방서"}}
    assert client.post("/story", json={**base, "stage": ["가" * 13]}).status_code == 422
    assert client.post("/story", json={**base, "stage": ["가"] * 6}).status_code == 422
    assert client.post("/story", json={**base, "stage": ["소방차 차고"]}).status_code == 200


# 10-05: the mission page sets up the object the app's mission then uses
def test_the_mission_prop_reaches_the_plan():
    req = StoryRequest(slots={}, pages=[Page(kind="DRAG", mission="E1", prop="맛있는 간식")])
    assert "「맛있는 간식」" in story_route.plan(req)
    assert "「" not in story_route.plan(StoryRequest(slots={}, pages=[Page(kind="DRAG", mission="E1")]))



# 10-05 device round: a co-op book read its pages with the story meanings — FAIL (the feeling page) as
# 「해 봤지만 잘 안 된다」 and TALK (the why page) as 「누가 말한다」 — and wrote three empty pages in eight
def test_a_day_book_reads_its_pages_by_the_slots_they_are_made_of():
    pages = [Page(kind="FAIL"), Page(kind="TALK"), Page(kind="RUB", mission="A6"), Page(kind="TOGETHER")]
    coop = story_route.plan(StoryRequest(mode="coop", slots={}, reason="done", pages=pages))
    assert "reaction" in coop and "cause" in coop and "keep" in coop
    assert "해 봤지만 잘 안 된다" not in coop
    story = story_route.plan(StoryRequest(slots={}, pages=pages))
    assert "해 봤지만 잘 안 된다" in story and "reaction" not in story


def test_a_day_book_keeps_the_empty_place_line_to_the_last_page():
    req = StoryRequest(mode="coop", slots={}, reason="done", pages=[Page(kind="DEPART"), Page(kind="TOGETHER")])
    assert "마지막 쪽에 한 번만" in story_route.plan(req)
    assert "마지막 쪽에 한 번만" not in story_route.plan(StoryRequest(slots={}, pages=[Page(kind="DEPART")]))


def test_every_day_meaning_is_a_kind_the_app_knows():
    assert set(story_route.DAY_KIND_MEANING) <= set(story_route.KIND_MEANING)


# 10-05 device round: a real-day co-op book got a mission situation the child never told —
# 「무언가 묻거나 가려진 일은 아직 듣지 못했어요」 — the app now sends a prop only from the child's words
def test_a_real_day_mission_page_without_a_prop_sets_up_nothing():
    pages = [Page(kind="RUB", mission="A6"), Page(kind="DRAG", mission="E1", prop="블록")]
    done = story_route.plan(StoryRequest(mode="coop", slots={}, reason="done", pages=pages))
    rub, drag = done.splitlines()[1:3]
    assert "만들지 않고" in rub and "묻거나 덮여" not in rub
    assert "「블록」" in drag and "건네주기" in drag
    assert "만들지 않고" in story_route.plan(StoryRequest(mode="coop", slots={}, reason="soon", pages=pages)).splitlines()[1]


def test_an_imagined_or_story_book_still_sets_the_mission_up():
    pages = [Page(kind="RUB", mission="A6")]
    for req in (StoryRequest(mode="coop", slots={}, reason="dream", pages=pages), StoryRequest(slots={}, pages=pages)):
        line = story_route.plan(req).splitlines()[1]
        assert "묻거나 덮여" in line and "만들지 않고" not in line
