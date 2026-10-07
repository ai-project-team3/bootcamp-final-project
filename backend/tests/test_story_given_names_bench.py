"""The benchmark must catch a name removed from the visible captions (#250)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "eval"))

from bench_story_given_names import score
from app.schemas.story import Page, Scene, StoryRequest, StoryResult


def request():
    return StoryRequest(slots={"name": "맥도날드"}, pages=[Page(kind="MEET")])


def test_a_name_in_the_title_or_keywords_does_not_count_as_caption_retention():
    book = StoryResult(title="맥도날드의 모험", scenes=[
        Scene(index=1, caption="광대를 만났어요.", keywords="맥도날드")])
    result = score(book, request(), ["맥도날드"])
    assert result["missing"] == ["맥도날드"]
    assert result["kept"] is False


def test_visible_names_and_existing_placeholders_are_counted_literally():
    book = StoryResult(scenes=[Scene(index=1, caption="{친구1}은 맥도날드를 만났어요.", keywords="clown")])
    assert score(book, request(), ["맥도날드", "{친구1}"])["kept"] is True


def test_an_invalid_page_count_is_not_a_success_even_if_names_are_present():
    book = StoryResult(scenes=[Scene(index=i, caption="맥도날드를 만났어요.", keywords="clown") for i in (1, 2)])
    result = score(book, request(), ["맥도날드"])
    assert result["missing"] == []
    assert result["rejection"] == "2 scenes (want 1 pages)"
    assert result["kept"] is False
