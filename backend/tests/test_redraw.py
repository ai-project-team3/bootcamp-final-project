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

    async def run(wf, front=False):
        seen["wf"] = wf
        seen["front"] = front
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


def test_the_diary_redraws_in_colored_pencil_at_0_85():
    """#32 · 진웅 09-30: diary → colored pencil · 0.85; other modes keep cut paper · 0.9."""
    d = comfy.redraw_workflow("house", 1, "AAAA", mode="diary")
    s = comfy.redraw_workflow("house", 1, "AAAA", mode="story")
    assert "colored pencil" in d["2"]["inputs"]["text"] and d["5"]["inputs"]["denoise"] == 0.85
    assert "cut paper" in s["2"]["inputs"]["text"] and s["5"]["inputs"]["denoise"] == 0.9


def test_the_mode_reaches_the_workflow(live):
    post(mode="diary")
    assert "colored pencil" in live["wf"]["2"]["inputs"]["text"]


def test_redraw_orders_with_its_own_prompt_that_takes_things():
    """#32: the character prompt turned a house and a sun down as not-a-character."""
    s = image_route.system("redraw")
    assert "물건" in s and "rig" not in image_route.schema("redraw")["properties"]


def test_every_drawn_piece_is_kept():
    """#32: a sun's rays and a head drawn apart from the body were thrown away."""
    im = Image.new("RGB", (1024, 1024), (246, 246, 244))
    d = ImageDraw.Draw(im)
    d.ellipse((380, 380, 640, 640), fill=(250, 200, 30))           # the sun
    d.rectangle((495, 150, 525, 300), fill=(250, 150, 20))         # a ray, not touching it
    d.rectangle((495, 720, 525, 870), fill=(250, 150, 20))         # another
    out = Image.open(io.BytesIO(character.cut_out_all(_png(im))))
    alpha = out.getchannel("A")
    box = alpha.getbbox()
    # rays above and below the sun survive: the kept shape is much taller than wide
    assert (box[3] - box[1]) > 1.5 * (box[2] - box[0])



# #32 (10-02): nobody waits for a redraw, so it never jumps the GPU queue and gets a longer deadline
def test_a_redraw_does_not_jump_the_gpu_queue(live):
    assert post().json()["preset"] is False
    assert live["front"] is False


def test_a_redraw_may_take_longer_than_a_story_picture(live, monkeypatch):
    async def slow(wf, front=False):
        await asyncio.sleep(0.4)
        return generated()
    monkeypatch.setattr(comfy, "run", slow)
    monkeypatch.setattr(settings, "image_deadline_s", 0.2)     # a background would give up here
    monkeypatch.setattr(settings, "redraw_deadline_s", 5.0)
    assert post().json()["preset"] is False
    monkeypatch.setattr(settings, "redraw_deadline_s", 0.2)
    out = post().json()
    assert out["preset"] is True and "over" in out["reason"]


def test_redraws_enter_comfy_one_at_a_time(live, monkeypatch):
    busy, most = [0], [0]

    async def run(wf, front=False):
        busy[0] += 1; most[0] = max(most[0], busy[0])
        await asyncio.sleep(0.05)
        busy[0] -= 1
        return generated()
    monkeypatch.setattr(comfy, "run", run)

    async def two():
        from app.schemas.image import ImageRequest
        req = ImageRequest(kind="redraw", description="우리 집", png_base64=B64, mode="diary")
        await asyncio.gather(image_route.image(req), image_route.image(req))
    asyncio.run(two())
    assert most[0] == 1
