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
