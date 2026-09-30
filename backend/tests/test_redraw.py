"""/image kind: redraw (issue #32) — the child's drawing reaches our GPU only, is never kept,
and never goes to the (external) check. Every other way out is a preset (rule 8)."""
import asyncio
import base64
import io
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings                  # noqa: E402
from app.image import character, check, comfy    # noqa: E402
from app.routers import image as image_route     # noqa: E402
from main import app                             # noqa: E402


def _png(im: Image.Image) -> bytes:
    b = io.BytesIO(); im.save(b, "PNG"); return b.getvalue()


def child_drawing() -> bytes:
    """A crayon house on a transparent background, cropped loosely like the app sends it."""
    im = Image.new("RGBA", (300, 260), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((60, 110, 240, 240), outline=(200, 40, 40, 255), width=8)
    d.polygon([(40, 115), (150, 20), (260, 115)], outline=(40, 40, 200, 255), width=8)
    return _png(im)


def generated() -> bytes:
    """What SDXL hands back: a coloured shape on pale paper."""
    im = Image.new("RGB", (1024, 1024), (246, 246, 244))
    ImageDraw.Draw(im).rectangle((300, 300, 720, 760), fill=(210, 90, 60))
    return _png(im)


DRAWING = child_drawing()
B64 = base64.b64encode(DRAWING).decode()


def post(**body):
    return TestClient(app).post("/image", json={"kind": "redraw", "description": "우리 집",
                                                "png_base64": B64, "mode": "diary", **body})


@pytest.fixture
def live(monkeypatch):
    monkeypatch.setattr(settings, "mock", False)
    seen = {"wf": None, "checked": []}

    async def order(*_, **__):
        return {"safe": True, "rig": "blob", "subject": "small red house with a blue roof"}

    async def run(wf):
        seen["wf"] = wf
        return generated()

    async def safe(png):
        seen["checked"].append(png)
        return True, "ok"
    monkeypatch.setattr(image_route, "complete", order)
    monkeypatch.setattr(comfy, "run", run)
    monkeypatch.setattr(check, "is_safe", safe)
    return seen


def test_mock_hands_the_drawing_back(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    out = post().json()
    assert out["preset"] is False and out["png_base64"] == B64 and out["rig"] is None


def test_redraw_needs_the_drawing():
    r = TestClient(app).post("/image", json={"kind": "redraw", "description": "우리 집"})
    assert r.status_code == 422


def test_drawn_checked_and_cut_out(live):
    out = post().json()
    assert out["preset"] is False and out["rig"] is None
    png = base64.b64decode(out["png_base64"])
    im = Image.open(io.BytesIO(png))
    assert im.size == (640, 640) and im.mode == "RGBA"
    assert im.getpixel((0, 0))[3] == 0                     # transparent around it


def test_the_drawing_never_touches_comfy_disk(live):
    """No /upload/image · LoadImage (input folder) and no SaveImage (output folder)."""
    post()
    types = {n["class_type"] for n in live["wf"].values()}
    assert {"OttoLoadImageB64", "OttoReturnImageB64"} <= types
    assert not types & {"LoadImage", "SaveImage", "PreviewImage"}


def test_only_the_generated_picture_is_checked(live):
    post()
    assert len(live["checked"]) == 1
    checked = live["checked"][0]
    assert checked != DRAWING
    sent = live["wf"]["10"]["inputs"]["png_base64"]
    assert checked != base64.b64decode(sent)               # nor the prepared drawing


def test_a_flagged_redraw_is_a_preset(live, monkeypatch):
    async def flagged(_):
        return False, "flagged"
    monkeypatch.setattr(check, "is_safe", flagged)
    out = post().json()
    assert out["preset"] is True and out["png_base64"] is None


def test_comfy_without_the_nodes_is_a_preset(live, monkeypatch):
    async def rejected(_):
        raise comfy.ComfyError("prompt HTTP 400")         # PC2 lacks comfy_nodes/otto_memory.py
    monkeypatch.setattr(comfy, "run", rejected)
    assert post().json()["preset"] is True


def test_not_a_picture_is_a_preset(live):
    out = post(png_base64=base64.b64encode(b"not a png").decode()).json()
    assert out["preset"] is True and "cutout" in out["reason"]


def test_the_request_never_prints_the_drawing():
    from app.schemas.image import ImageRequest
    req = ImageRequest(kind="redraw", description="우리 집", png_base64=B64)
    assert B64 not in repr(req) and B64 not in str(req)


def test_prepared_drawing_is_white_paper():
    im = Image.open(io.BytesIO(character.prepare_drawing(DRAWING)))
    assert im.size == (1024, 1024) and im.mode == "RGB"
    assert im.getpixel((5, 5)) == (255, 255, 255)          # transparent became paper, not black


def test_run_deletes_the_history_entry(monkeypatch):
    """The history entry holds the drawing (prompt) and the result — gone as soon as read."""
    calls = []
    out_b64 = base64.b64encode(b"result").decode()

    class Resp:
        status_code = 200
        def __init__(self, j): self._j = j
        def json(self): return self._j

    class Http:
        def __init__(self, **_): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, url, json=None, **__):
            calls.append((url, json))
            return Resp({"prompt_id": "p9"})
        async def get(self, url, **__):
            return Resp({"p9": {"status": {"status_str": "success"},
                                "outputs": {"7": {"otto_png": [out_b64]}}}})

    monkeypatch.setattr(comfy.httpx, "AsyncClient", Http)
    wf = comfy.redraw_workflow("house", 1, "AAAA")
    assert asyncio.run(comfy.run(wf)) == b"result"
    assert any(u.endswith("/history") and j == {"delete": ["p9"]} for u, j in calls)
