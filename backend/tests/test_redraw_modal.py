"""Redraw on our Modal GPU (#460 ⑥): the child's drawing is used in memory and gone — no file, no log line,
no error message carries the picture or its base64, on the backend side or on the Modal side (backend/modal_gpu/web.py)."""
import asyncio
import base64
import io
import json
import logging
import os
import sys
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "modal_gpu"))

from app.config import settings                  # noqa: E402
from app.image import check, comfy               # noqa: E402
from app.routers import image as image_route     # noqa: E402
from main import app                             # noqa: E402
from web import make_app                         # noqa: E402


def _png(color=(200, 40, 40)) -> bytes:
    im = Image.new("RGBA", (300, 260), (0, 0, 0, 0))
    ImageDraw.Draw(im).rectangle((60, 110, 240, 240), outline=color + (255,), width=8)
    b = io.BytesIO()
    im.save(b, "PNG")
    return b.getvalue()


DRAWING = _png()
B64 = base64.b64encode(DRAWING).decode()
OUT = base64.b64encode(_png((40, 40, 200))).decode()


def _traces(text: str) -> bool:
    """Any recognisable piece of the drawing's base64 (the backend re-encodes it, so look for a long run)."""
    return B64[40:80] in text or B64[:60] in text


@pytest.fixture
def modal_on(monkeypatch):
    monkeypatch.setattr(settings, "redraw_modal_url", "https://otto-redraw.example")
    monkeypatch.setattr(settings, "redraw_modal_key", "k")
    monkeypatch.setattr(settings, "redraw_modal_secret", "s")
    calls = []

    def use(handler):
        real = httpx.AsyncClient

        def client(*a, **kw):
            kw["transport"] = httpx.MockTransport(handler)
            return real(*a, **kw)
        monkeypatch.setattr(comfy.httpx, "AsyncClient", client)
    return use, calls


def _graph() -> dict:
    return comfy.redraw_workflow("a small red house", 7, B64, mode="diary")


def test_off_by_default():
    assert settings.redraw_modal_url == ""


def test_redraw_goes_to_modal_with_auth_and_waits_out_a_cold_start(modal_on):
    use, calls = modal_on
    answers = iter([503, 503, 200])

    def handler(req: httpx.Request):
        calls.append(req)
        code = next(answers)
        return httpx.Response(code, json={"png_base64": OUT} if code == 200 else {})
    use(handler)
    png = asyncio.run(comfy.draw(_graph(), "redraw"))
    assert png == base64.b64decode(OUT)
    assert [c.url.path for c in calls] == ["/draw"] * 3
    assert calls[0].headers["Modal-Key"] == "k" and calls[0].headers["Modal-Secret"] == "s"


@pytest.mark.parametrize("kind", ["background", "character"])
def test_by_default_only_the_redraw_goes_to_modal(modal_on, kind):
    use, calls = modal_on
    seen = []

    def handler(req: httpx.Request):
        seen.append(str(req.url))
        return httpx.Response(500)
    use(handler)
    with pytest.raises(comfy.ComfyError):
        asyncio.run(comfy.draw(comfy.workflow("a meadow", 1), kind))     # still PC2's ComfyUI
    assert seen and all(u.startswith(settings.comfy_url) for u in seen)


def test_a_redraw_left_off_modal_goes_to_pc2(modal_on, monkeypatch):
    use, _ = modal_on
    monkeypatch.setattr(settings, "modal_kinds", "background")
    seen = []

    def handler(req: httpx.Request):
        seen.append(str(req.url))
        return httpx.Response(500)
    use(handler)
    with pytest.raises(comfy.ComfyError):
        asyncio.run(comfy.draw(_graph(), "redraw"))
    assert seen and all(u.startswith(settings.comfy_url) for u in seen)


def test_a_background_on_modal_comes_back_through_memory(modal_on, monkeypatch):
    """Nothing on Modal may write a file: SaveImage becomes OttoReturnImageB64 before the graph leaves."""
    use, calls = modal_on
    monkeypatch.setattr(settings, "modal_kinds", "redraw, background")

    def handler(req: httpx.Request):
        calls.append(req)
        return httpx.Response(200, json={"png_base64": OUT})
    use(handler)
    png = asyncio.run(comfy.background("a sunny park"))
    assert png == base64.b64decode(OUT)
    sent = json.loads(calls[0].content)["workflow"]
    assert [c.url.path for c in calls] == ["/draw"]
    kinds = {n["class_type"] for n in sent.values()}
    assert "SaveImage" not in kinds and "OttoReturnImageB64" in kinds
    assert sent["4"]["inputs"]["width"] == comfy.W               # the same background graph otherwise


def test_a_character_on_modal_brings_its_mannequin_and_uploads_nothing(modal_on, monkeypatch):
    use, calls = modal_on
    monkeypatch.setattr(settings, "modal_kinds", "character")
    monkeypatch.setattr(settings, "mock", False)

    def handler(req: httpx.Request):
        calls.append(req)
        return httpx.Response(200, json={"png_base64": OUT})
    use(handler)

    async def order(*_, **__):
        return {"safe": True, "subject": "robot", "rig": "human"}
    monkeypatch.setattr(image_route, "complete", order)

    async def safe(*_):
        return True, "ok"
    monkeypatch.setattr(check, "is_safe", safe)
    monkeypatch.setattr(image_route.character, "cut_and_fit", lambda raw: raw)
    r = TestClient(app).post("/image", json={"kind": "character", "description": "로봇", "mode": "story"})
    assert r.status_code == 200 and r.json()["preset"] is False
    assert [c.url.path for c in calls] == ["/draw"]                # no /upload/image to PC2 or anywhere
    sent = json.loads(calls[0].content)["workflow"]
    tmpl = image_route.character.template("human")
    assert sent["10"] == {"class_type": "OttoLoadImageB64", "inputs": {"png_base64": base64.b64encode(tmpl).decode()}}
    assert "LoadImage" not in {n["class_type"] for n in sent.values()}


def test_a_graph_that_still_needs_pc2s_input_folder_never_goes_to_modal():
    with pytest.raises(comfy.ComfyError):
        comfy.memory_graph(comfy.character_workflow("robot", "human", 1, "otto_mannequin_human.png"))


def test_warm_up_stays_on_pc2_and_skips_when_everything_is_on_modal(modal_on, monkeypatch):
    use, _ = modal_on
    seen = []

    def handler(req: httpx.Request):
        seen.append(str(req.url))
        return httpx.Response(500)
    use(handler)
    monkeypatch.setattr(settings, "modal_kinds", "background")
    asyncio.run(comfy.warm_up())
    assert seen and all(u.startswith(settings.comfy_url) for u in seen)
    seen.clear()
    monkeypatch.setattr(settings, "modal_kinds", "redraw,background,character")
    asyncio.run(comfy.warm_up())
    assert seen == []


@pytest.mark.parametrize("how", ["http_error_echoing_the_body", "network_error", "bad_answer"])
def test_a_failed_modal_redraw_leaves_no_trace_of_the_drawing(modal_on, caplog, how):
    use, _ = modal_on

    def handler(req: httpx.Request):
        if how == "network_error":
            raise httpx.ConnectError(f"refused while sending {req.content[:200]!r}", request=req)
        if how == "bad_answer":
            return httpx.Response(200, text="not json " + req.content.decode()[:500])
        return httpx.Response(500, text=req.content.decode())            # a proxy that echoes what it got
    use(handler)
    caplog.set_level(logging.DEBUG)
    with pytest.raises(comfy.ComfyError) as e:
        asyncio.run(comfy.draw(_graph(), "redraw"))
    assert not _traces(str(e.value))
    assert e.value.__cause__ is None              # nothing chained that a later log line could print
    assert not _traces(caplog.text)


def test_the_route_answers_preset_without_the_drawing_when_modal_fails(modal_on, monkeypatch, caplog):
    use, _ = modal_on
    use(lambda req: httpx.Response(500, text=req.content.decode()))
    monkeypatch.setattr(settings, "mock", False)

    async def order(*_, **__):
        return {"safe": True, "subject": "small red house"}
    monkeypatch.setattr(image_route, "complete", order)

    async def never(*_):
        raise AssertionError("the check must not run on a failed redraw")
    monkeypatch.setattr(check, "is_safe", never)
    caplog.set_level(logging.DEBUG)
    r = TestClient(app).post("/image", json={"kind": "redraw", "description": "우리 집", "png_base64": B64, "mode": "diary"})
    assert r.status_code == 200 and r.json()["preset"] is True
    assert not _traces(r.text) and not _traces(caplog.text)


# ── the Modal side: backend/modal_gpu/web.py ──────────────────────────────────

def test_modal_side_draws_in_memory_and_writes_no_file(tmp_path, monkeypatch, caplog):
    monkeypatch.chdir(tmp_path)
    caplog.set_level(logging.DEBUG)
    got = {}

    def run(wf):
        got["drawing"] = wf["10"]["inputs"]["png_base64"]
        return OUT
    r = TestClient(make_app(run)).post("/draw", json={"workflow": _graph()})
    assert r.status_code == 200 and r.json()["png_base64"] == OUT
    assert got["drawing"] == B64
    assert os.listdir(tmp_path) == []
    assert not _traces(caplog.text)


def test_modal_side_error_says_only_what_kind_of_failure(tmp_path, monkeypatch, caplog):
    monkeypatch.chdir(tmp_path)
    caplog.set_level(logging.DEBUG)

    def run(wf):
        raise ValueError("cannot decode " + wf["10"]["inputs"]["png_base64"])
    r = TestClient(make_app(run), raise_server_exceptions=False).post("/draw", json={"workflow": _graph()})
    assert r.status_code == 500 and r.json() == {"error": "ValueError"}
    assert not _traces(r.text) and not _traces(caplog.text)
    assert os.listdir(tmp_path) == []


def test_wake_is_a_no_op_when_off_and_at_most_once_in_ten_seconds(monkeypatch):
    sent = []

    async def wake():
        sent.append(1)
    monkeypatch.setattr(comfy, "_wake", wake)
    monkeypatch.setattr(comfy, "_woke_at", 0.0)

    async def turns():
        comfy.wake_gpu("diary")                   # off: nothing
        monkeypatch.setattr(settings, "redraw_modal_url", "https://otto-redraw.example")
        comfy.wake_gpu("diary")
        comfy.wake_gpu("diary")                   # same moment: skipped
        await asyncio.sleep(0)
    asyncio.run(turns())
    assert sent == [1]


@pytest.mark.parametrize("kinds,mode,wakes", [
    ("redraw", "diary", True), ("redraw", "story", False), ("redraw", "coop", False),
    ("redraw,background", "story", True), ("character", "coop", True),
])
def test_which_turns_wake_the_gpu(monkeypatch, kinds, mode, wakes):
    """The redraw alone keeps the diary's GPU up (#471); a background or a character may come in any mode."""
    sent = []

    async def wake():
        sent.append(1)
    monkeypatch.setattr(comfy, "_wake", wake)
    monkeypatch.setattr(comfy, "_woke_at", -100.0)
    monkeypatch.setattr(settings, "redraw_modal_url", "https://otto-redraw.example")
    monkeypatch.setattr(settings, "modal_kinds", kinds)

    async def turn():
        comfy.wake_gpu(mode)
        await asyncio.sleep(0)
    asyncio.run(turn())
    assert sent == ([1] if wakes else [])
