"""POST /image: every way out that is not "drawn and checked in time" is a preset (rule 8)."""
import asyncio
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                  # noqa: E402
from app.filters.blocklist import BLOCK         # noqa: E402
from app.image import check, comfy               # noqa: E402
from app.llm.client import LLMError              # noqa: E402
from app.routers import image as image_route     # noqa: E402
from main import app                             # noqa: E402

PNG = b"\x89PNG fake"


@pytest.fixture
def live(monkeypatch):
    """Not mock: the scene LLM, ComfyUI and the check are faked one by one."""
    monkeypatch.setattr(settings, "mock", False)

    async def scene(*_, **__):
        return {"safe": True, "scene": "a dinosaur land with tall ferns"}

    async def draw(*_):
        return PNG

    async def safe(_):
        return True, "ok"
    monkeypatch.setattr(image_route, "complete", scene)
    monkeypatch.setattr(comfy, "background", draw)
    monkeypatch.setattr(check, "is_safe", safe)
    return monkeypatch


def post(**body):
    return TestClient(app).post("/image", json={"place": "공룡나라", **body}).json()


def test_mock_gives_a_picture(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    out = post()
    assert out["preset"] is False and out["png_base64"]


def test_drawn_and_checked_gives_the_picture(live):
    out = post()
    assert out["preset"] is False and out["scene"].startswith("a dinosaur")
    assert out["png_base64"]


def test_a_blocked_place_never_leaves(live):
    called = []

    async def spy(*_, **__):
        called.append(1)
        return {"safe": True, "scene": "x"}
    live.setattr(image_route, "complete", spy)
    out = post(place=f"{next(iter(BLOCK))} 나라")
    assert out["preset"] is True and not called


def test_not_drawable_is_a_preset(live):
    async def no(*_, **__):
        return {"safe": False, "scene": None}
    live.setattr(image_route, "complete", no)
    assert post(place="몰라")["preset"] is True


def test_scene_llm_down_is_a_preset(live):
    async def boom(*_, **__):
        raise LLMError("down")
    live.setattr(image_route, "complete", boom)
    assert post()["preset"] is True


def test_comfy_down_is_a_preset(live):
    async def boom(*_):
        raise comfy.ComfyError("network")
    live.setattr(comfy, "background", boom)
    out = post()
    assert out["preset"] is True and out["png_base64"] is None


def test_a_flagged_picture_never_reaches_the_child(live):
    async def flagged(_):
        return False, "flagged: violence"
    live.setattr(check, "is_safe", flagged)
    out = post()
    assert out["preset"] is True and out["png_base64"] is None


def test_too_slow_is_a_preset(live):
    async def slow(*_):
        await asyncio.sleep(1)
        return PNG
    live.setattr(comfy, "background", slow)
    live.setattr(settings, "image_deadline_s", 0.2)
    out = post()
    assert out["preset"] is True and "over" in out["reason"]


def test_the_check_fails_closed_without_a_key(monkeypatch):
    monkeypatch.setattr(settings, "openai_api_key", "")
    ok, why = asyncio.run(check.is_safe(PNG))
    assert ok is False


def test_the_workflow_is_the_measured_recipe():
    wf = comfy.workflow("a beach", seed=1)
    ks = wf["5"]["inputs"]
    assert (ks["steps"], ks["cfg"], ks["sampler_name"], ks["scheduler"]) == (8, 1.0, "euler", "sgm_uniform")
    assert wf["8"]["inputs"]["lora_name"] == settings.image_lora
    assert "no text" in wf["2"]["inputs"]["text"] and "no people" in wf["2"]["inputs"]["text"]


def test_the_scene_prompt_is_the_fenced_block_only():
    s = image_route.system()
    assert s.startswith("당신은") and "근거:" not in s


def test_giving_up_cancels_the_comfy_job(monkeypatch):
    """A given-up picture must not hold the queue for the next child (09-29: 48.9 s cold start)."""
    cancelled = []

    async def fake_cancel(base, pid):
        cancelled.append(pid)

    class Resp:
        status_code = 200

        def json(self):
            return {"prompt_id": "p1"}

    class Http:
        def __init__(self, **_): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, *_, **__): return Resp()
        async def get(self, *_, **__):
            r = Resp()
            r.json = lambda: {}          # never done
            return r

    monkeypatch.setattr(comfy, "_cancel", fake_cancel)
    monkeypatch.setattr(comfy.httpx, "AsyncClient", Http)

    async def go():
        try:
            await asyncio.wait_for(comfy.background("x"), timeout=0.3)
        except asyncio.TimeoutError:
            pass
    asyncio.run(go())
    assert cancelled == ["p1"]


def test_a_background_goes_to_the_front_of_the_gpu_queue(monkeypatch):
    """#32 (10-02): a child waits for this picture — it goes ahead of redraws already queued."""
    seen = {}

    async def run(wf, front=False):
        seen["front"] = front
        return PNG
    monkeypatch.setattr(comfy, "run", run)
    asyncio.run(comfy.background("a sunny park"))
    assert seen["front"] is True


# ── art style (10-07 종훈 · 크레용 아이 그림체) ───────────────────────────

def test_the_book_style_reaches_the_background(live):
    got = []

    async def draw(scene, style="felt", mode="story"):
        got.append(style)
        return PNG
    live.setattr(comfy, "background", draw)
    post()
    post(style="crayon")
    assert got == ["felt", "crayon"]


# ── diary background (#264 · 10-08 진웅) ───────────────────────────────

def test_a_diary_background_is_asked_in_the_diary_style(live):
    got = []

    async def draw(scene, style="felt", mode="story"):
        got.append(mode)
        return PNG
    live.setattr(comfy, "background", draw)
    post()
    post(mode="diary")
    assert got == ["story", "diary"]


def test_a_diary_background_is_colored_pencil_not_felt():
    wf = comfy.workflow("a beach", seed=1, mode="diary")
    assert wf["2"]["inputs"]["text"] == "a beach" + comfy.DIARY_BG_STYLE
    assert wf["3"]["inputs"]["text"] == comfy.DIARY_BG_NEG
    assert "felt" not in wf["2"]["inputs"]["text"]
    # whatever the book's style — the diary keeps colored pencil, as its redraws do
    assert comfy.workflow("a beach", seed=1, style="crayon", mode="diary")["2"]["inputs"]["text"] == "a beach" + comfy.DIARY_BG_STYLE
    assert comfy.workflow("a beach", seed=1)["2"]["inputs"]["text"] == "a beach" + comfy.BG_STYLE


def test_a_diary_background_reaches_the_workflow(monkeypatch):
    seen = {}

    async def run(wf, front=False):
        seen["text"] = wf["2"]["inputs"]["text"]
        return PNG
    monkeypatch.setattr(comfy, "run", run)
    asyncio.run(comfy.background("a sunny park", mode="diary"))
    assert seen["text"].endswith(comfy.DIARY_BG_STYLE)


def test_an_unknown_style_is_refused():
    r = TestClient(app).post("/image", json={"place": "공룡나라", "style": "oil"})
    assert r.status_code == 422


def test_felt_words_are_unchanged_and_crayon_has_its_own():
    felt = comfy.workflow("a beach", seed=1)
    assert felt["2"]["inputs"]["text"] == "a beach" + comfy.BG_STYLE and felt["3"]["inputs"]["text"] == comfy.NEG
    crayon = comfy.workflow("a beach", seed=1, style="crayon")
    text = crayon["2"]["inputs"]["text"]
    assert "crayon" in text and "felt" not in text
    assert "felt" in crayon["3"]["inputs"]["text"]          # the negative keeps felt out
    assert crayon["1"] == felt["1"] and crayon["5"]["inputs"]["steps"] == 8   # same model and steps
