"""/image redraw with role: background (#168 · 10-06 진웅) — a background piece (ground · sky · sea lines
across the board) comes as the whole board, gets its bands washed, is redrawn in colored pencil as a
wide scene at 0.9 and is not cut out. Measured: eval/results.md 「10-06 — 일기 배경 조각 다시 그리기」."""
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

GREEN, SKY = (90, 170, 80, 255), (110, 180, 235, 255)


def _png(im: Image.Image) -> bytes:
    b = io.BytesIO(); im.save(b, "PNG"); return b.getvalue()


def board(lines=(("ground", 0.8),), size=(1600, 800)) -> bytes:
    """The whole board, transparent, with flat lines where the child drew them (like the app sends it)."""
    w, h = size
    im = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for kind, y in lines:
        d.line([(int(w * 0.04), int(h * y)), (int(w * 0.96), int(h * y))], fill=GREEN if kind == "ground" else SKY,
               width=max(4, int(h * 0.02)))
    return _png(im)


def generated(size=(1536, 768)) -> bytes:
    im = Image.new("RGB", size, (246, 246, 244))
    ImageDraw.Draw(im).rectangle((0, int(size[1] * 0.8), size[0], size[1]), fill=(120, 190, 100))
    return _png(im)


B64 = base64.b64encode(board()).decode()


def post(**body):
    return TestClient(app).post("/image", json={"kind": "redraw", "description": "바다", "png_base64": B64,
                                                "mode": "diary", "role": "background", **body})


@pytest.fixture
def live(monkeypatch):
    monkeypatch.setattr(settings, "mock", False)
    seen = {"wf": None, "checked": []}

    async def order(*_, **__):
        return {"safe": True, "subject": "blue sea with waves"}

    async def run(wf, front=False):
        seen["wf"] = wf
        return generated()

    async def safe(png):
        seen["checked"].append(png)
        return True, "ok"
    monkeypatch.setattr(image_route, "complete", order)
    monkeypatch.setattr(comfy, "run", run)
    monkeypatch.setattr(check, "is_safe", safe)
    return seen


def test_role_is_only_object_or_background():
    assert TestClient(app).post("/image", json={"kind": "redraw", "description": "바다", "png_base64": B64,
                                                "role": "sky"}).status_code == 422


def test_board_keeps_where_the_line_was():
    """Not cropped and centred like a piece: a line at 80 % of the board stays at 80 %."""
    im = Image.open(io.BytesIO(character.prepare_board(board(size=(1600, 800)))))
    assert im.mode == "RGB" and im.height == 768 and im.width % 8 == 0 and abs(im.width / im.height - 2) < 0.02
    col = [im.getpixel((im.width // 2, y)) for y in range(im.height)]
    ink = [y for y, p in enumerate(col) if min(p) < 200]
    assert ink and abs(sum(ink) / len(ink) / im.height - 0.8) < 0.02
    assert im.getpixel((5, 5)) == (255, 255, 255)          # transparent became paper


def test_board_aspect_is_kept_within_bounds():
    im = Image.open(io.BytesIO(character.prepare_board(board(size=(4000, 400)))))
    assert im.height == 768 and im.width <= 1536            # a sliver is not sent as a 7680-wide picture


def test_ground_is_washed_down_and_sky_up():
    prepared = character.prepare_board(board(lines=(("sky", 0.15), ("ground", 0.8))))
    im = Image.open(io.BytesIO(character.wash_bands(prepared)))
    x = im.width // 2
    below_ground, middle, above_sky = (im.getpixel((x, int(im.height * f))) for f in (0.92, 0.5, 0.05))
    assert middle == (255, 255, 255)                        # between the lines: paper
    assert below_ground != (255, 255, 255) and below_ground[1] > below_ground[0]     # a pale green
    assert above_sky != (255, 255, 255) and above_sky[2] > above_sky[0]               # a pale blue


def test_a_board_with_no_lines_comes_back_as_it_was():
    paper = _png(Image.new("RGB", (1536, 768), (255, 255, 255)))
    assert character.wash_bands(paper) == paper


def test_an_empty_board_is_not_a_drawing():
    with pytest.raises(character.CutoutError):
        character.prepare_board(_png(Image.new("RGBA", (1600, 800), (0, 0, 0, 0))))


def test_background_is_a_wide_pencil_scene_not_cut_out(live):
    out = post().json()
    assert out["preset"] is False
    im = Image.open(io.BytesIO(base64.b64decode(out["png_base64"])))
    assert im.size == (1536, 768)                           # the scene itself, not a 640² cut piece
    positive = live["wf"]["2"]["inputs"]["text"]
    negative = live["wf"]["3"]["inputs"]["text"]
    assert positive.startswith("blue sea with waves") and "colored pencil" in positive and "landscape" in positive
    assert "isolated" not in positive and "background scenery" not in negative
    assert live["wf"]["5"]["inputs"]["denoise"] == comfy.DIARY_BG_DENOISE == 0.9


def test_background_sends_the_washed_board(live):
    post()
    sent = Image.open(io.BytesIO(base64.b64decode(live["wf"]["10"]["inputs"]["png_base64"])))
    assert sent.height == 768 and sent.width == 1536
    assert sent.getpixel((sent.width // 2, int(sent.height * 0.92))) != (255, 255, 255)


def test_background_follows_the_same_privacy_path(live):
    """Same as a piece: memory nodes only, and only the generated picture is checked."""
    post()
    types = {n["class_type"] for n in live["wf"].values()}
    assert {"OttoLoadImageB64", "OttoReturnImageB64"} <= types
    assert not types & {"LoadImage", "SaveImage", "PreviewImage"}
    assert len(live["checked"]) == 1 and live["checked"][0] == generated()


def test_no_role_is_still_a_piece(live, monkeypatch):
    async def a_piece(wf, front=False):
        live["wf"] = wf
        im = Image.new("RGB", (1024, 1024), (246, 246, 244))
        ImageDraw.Draw(im).rectangle((300, 300, 720, 760), fill=(210, 90, 60))
        return _png(im)
    monkeypatch.setattr(comfy, "run", a_piece)
    out = post(role=None).json()
    assert Image.open(io.BytesIO(base64.b64decode(out["png_base64"]))).size == (640, 640)
    assert "isolated" in live["wf"]["2"]["inputs"]["text"]
