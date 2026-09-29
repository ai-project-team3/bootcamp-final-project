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

    async def draw(_):
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
    async def boom(_):
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
    async def slow(_):
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
