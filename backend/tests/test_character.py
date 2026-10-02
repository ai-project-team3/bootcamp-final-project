"""/image kind=character: a posed, cut-out character that fits 안치영's spec — or a preset."""
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


def doll_on_grey(confetti: bool = True) -> bytes:
    """What SDXL gives: a grey-white gradient, the doll in the middle, a few paper scraps around."""
    im = Image.new("RGB", (1024, 1024))
    d = ImageDraw.Draw(im)
    for y in range(1024):
        d.line([(0, y), (1023, y)], fill=(236 - y // 40,) * 3)           # 236 → 211, still background
    d.ellipse((430, 180, 590, 340), fill=(240, 200, 170))                # head
    d.rectangle((420, 340, 600, 640), fill=(220, 60, 60))               # red dress
    d.ellipse((470, 240, 490, 260), fill=(255, 255, 255))               # white of the eye — a hole to fill
    d.rectangle((450, 640, 490, 820), fill=(240, 200, 170))             # legs
    d.rectangle((530, 640, 570, 820), fill=(240, 200, 170))
    if confetti:
        d.rectangle((60, 60, 90, 90), fill=(60, 120, 220))              # a scrap, must be dropped
    b = io.BytesIO(); im.save(b, "PNG")
    return b.getvalue()


def test_cut_and_fit_matches_the_spec():
    out = Image.open(io.BytesIO(character.cut_and_fit(doll_on_grey())))
    assert out.size == (640, 640) and out.mode == "RGBA"
    a = out.getchannel("A")
    assert a.getpixel((0, 0)) == 0 and a.getpixel((639, 639)) == 0, "background must be transparent"
    left, top, right, bottom = a.point(lambda v: 255 if v > 127 else 0).getbbox()
    assert abs(bottom - round(640 * character.FEET)) <= 2, "feet on the 93% line (app FEET_IN_ART)"
    assert (right - left) <= 640 * character.MAX_W + 2
    assert a.getpixel((80 * 640 // 1024, 80 * 640 // 1024)) == 0, "the paper scrap is dropped"


def test_the_eye_hole_is_filled_not_see_through():
    out = Image.open(io.BytesIO(character.cut_and_fit(doll_on_grey(confetti=False))))
    a = out.getchannel("A")
    # somewhere inside the head there must be no transparent pixel
    left, top, right, bottom = a.point(lambda v: 255 if v > 127 else 0).getbbox()
    cx = (left + right) // 2
    # head + dress only — below that the centre line runs between the legs, which is really empty
    assert all(a.getpixel((cx, y)) > 200 for y in range(top + 10, top + (bottom - top) * 2 // 3))


def test_an_empty_picture_is_a_cutout_error():
    b = io.BytesIO(); Image.new("RGB", (512, 512), (240, 240, 240)).save(b, "PNG")
    with pytest.raises(character.CutoutError):
        character.cut_and_fit(b.getvalue())


def test_every_rig_has_a_mannequin():
    # blob too — without one the model paints a coloured backdrop the cut-out cannot remove (09-29)
    assert all(character.template(r) for r in ("human", "quad", "blob"))


def test_the_img2img_graph_starts_from_the_mannequin():
    wf = comfy.character_workflow("robot", "human", 1, "otto_mannequin_human.png")
    assert wf["10"]["inputs"]["image"] == "otto_mannequin_human.png"
    assert wf["5"]["inputs"]["latent_image"] == ["11", 0] and wf["5"]["inputs"]["denoise"] == 0.85
    assert "A-pose" in wf["2"]["inputs"]["text"] and "white background" in wf["2"]["inputs"]["text"]
    blob = comfy.character_workflow("octopus", "blob", 1, "otto_mannequin_blob.png")
    assert blob["5"]["inputs"]["denoise"] == 0.9


@pytest.fixture
def live(monkeypatch):
    monkeypatch.setattr(settings, "mock", False)
    image_route._uploaded.clear()

    async def order(*_, **__):
        return {"safe": True, "rig": "human", "subject": "little girl in a red dress"}

    async def upload(png, name):
        return name

    async def run(wf, front=False):
        assert front, "a character is waited for — it goes to the front of the GPU queue (#32)"
        return doll_on_grey()

    async def safe(_):
        return True, "ok"
    monkeypatch.setattr(image_route, "complete", order)
    monkeypatch.setattr(comfy, "upload", upload)
    monkeypatch.setattr(comfy, "run", run)
    monkeypatch.setattr(check, "is_safe", safe)
    return monkeypatch


def post(**body):
    return TestClient(app).post("/image", json={"kind": "character", **body})


def test_a_character_comes_back_cut_out_with_its_rig(live):
    out = post(description="빨간 드레스 입은 공주").json()
    assert out["preset"] is False and out["rig"] == "human"
    png = Image.open(io.BytesIO(__import__("base64").b64decode(out["png_base64"])))
    assert png.size == (640, 640) and png.mode == "RGBA"


def test_a_failed_cutout_is_a_preset(live):
    async def blank(wf, front=False):
        b = io.BytesIO(); Image.new("RGB", (512, 512), (240, 240, 240)).save(b, "PNG")
        return b.getvalue()
    live.setattr(comfy, "run", blank)
    out = post(description="유령").json()
    assert out["preset"] is True and out["reason"].startswith("cutout")


def test_a_character_needs_a_description():
    r = TestClient(app).post("/image", json={"kind": "character"})
    assert r.status_code == 422
