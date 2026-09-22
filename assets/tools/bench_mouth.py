# -*- coding: utf-8 -*-
"""턱을 자르지 않고 움직일 수 있나 — 입만 다시 그려 「벌린 버전」을 한 장 더 만든다.

왜 이걸 재나
  09-21에 관절을 만드는 길 세 개가 다 막혔다: 부품 생성 실패, 자동 분할 실패
  (몸통·다리·꼬리가 한 덩어리로 39%), 손으로 자르기는 세션 중 불가능.
  셋 다 **원본을 쪼개려는** 시도였다. 쪼개지 않는 길이 하나 남는다 —
  **입 영역만 다시 그리면 바깥은 픽셀 그대로**라 캐릭터가 안 바뀐다.
  두 장을 번갈아 보여주면 말하는 것처럼 된다.

무엇이 성패를 가르나
  · 다시 그린 자리가 주변과 이어 붙는가 (경계가 보이면 실패)
  · denoise를 얼마나 줘야 입이 실제로 벌어지는가 — 낮으면 안 벌어지고
    높으면 그 부분이 딴 그림이 된다

    python bench_mouth.py base                  기준 그림 한 장 (입 다문)
    python bench_mouth.py open x0 y0 x1 y1      그 네모만 「입 벌린」으로 다시 그림
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render  # noqa: E402

API = render.API
SEED = 81234
BASE = "mouth_base"
CLOSED = "a friendly cartoon dinosaur standing, mouth closed" + render.CUT
OPEN = "a friendly cartoon dinosaur, wide open mouth, teeth and tongue visible" + render.CUT
DENOISE = [0.55, 0.75, 0.95]


def submit(wf):
    body = json.dumps({"prompt": wf}).encode()
    req = urllib.request.Request(API + "/prompt", body,
                                 {"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=60) as r:
        pid = json.load(r)["prompt_id"]
    while True:
        with urllib.request.urlopen(API + "/history/" + pid, timeout=30) as r:
            hist = json.load(r)
        if pid in hist:
            return hist[pid], time.time() - t0
        time.sleep(0.12)


def save(hist, name):
    for node in hist["outputs"].values():
        for img in node.get("images", []):
            q = urllib.parse.urlencode(img)
            data = urllib.request.urlopen(API + "/view?" + q, timeout=120).read()
            path = os.path.join(render.OUT, name + ".png")
            open(path, "wb").write(data)
            return path


def upload(path, name):
    """ComfyUI reads inputs from its own input dir, so the base and the mask
    have to be pushed to the server before a LoadImage node can see them."""
    body, bnd = [], "----mouth%d" % time.time()
    with open(path, "rb") as f:
        blob = f.read()
    pre = ("--%s\r\nContent-Disposition: form-data; name=\"image\"; filename=\"%s\"\r\n"
           "Content-Type: image/png\r\n\r\n" % (bnd, name)).encode()
    post = ("\r\n--%s\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n"
            "--%s--\r\n" % (bnd, bnd)).encode()
    body = pre + blob + post
    req = urllib.request.Request(API + "/upload/image", body,
                                 {"Content-Type": "multipart/form-data; boundary=" + bnd})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)["name"]


def base():
    wf = render.workflow(CLOSED, 768, 768, BASE, SEED, fast=True)
    hist, dt = submit(wf)
    print("기준 그림 %.2f초 → %s" % (dt, save(hist, BASE)))


def open_mouth(box):
    from PIL import Image, ImageDraw
    src = os.path.join(render.OUT, BASE + ".png")
    im = Image.open(src).convert("RGB")
    m = Image.new("RGB", im.size, (0, 0, 0))
    ImageDraw.Draw(m).ellipse(box, fill=(255, 255, 255))
    mpath = os.path.join(render.OUT, "mouth_mask.png")
    m.save(mpath)

    b_name = upload(src, "mouth_base.png")
    m_name = upload(mpath, "mouth_mask.png")

    for dn in DENOISE:
        tag = "mouth_open_%02d" % int(dn * 100)
        wf = {
            "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": render.CKPT}},
            "2": {"class_type": "CLIPTextEncode", "inputs": {"text": OPEN, "clip": ["1", 1]}},
            "3": {"class_type": "CLIPTextEncode", "inputs": {"text": render.NEG, "clip": ["1", 1]}},
            "9": {"class_type": "LoadImage", "inputs": {"image": b_name}},
            "10": {"class_type": "LoadImage", "inputs": {"image": m_name}},
            "11": {"class_type": "ImageToMask", "inputs": {"image": ["10", 0], "channel": "red"}},
            "12": {"class_type": "VAEEncode", "inputs": {"pixels": ["9", 0], "vae": ["1", 2]}},
            "13": {"class_type": "SetLatentNoiseMask", "inputs": {
                "samples": ["12", 0], "mask": ["11", 0]}},
            "8": {"class_type": "LoraLoaderModelOnly", "inputs": {
                "model": ["1", 0], "lora_name": render.LORA, "strength_model": 1.0}},
            "5": {"class_type": "KSampler", "inputs": {
                "model": ["8", 0], "positive": ["2", 0], "negative": ["3", 0],
                "latent_image": ["13", 0], "seed": SEED + int(dn * 100),
                "steps": 8, "cfg": 1.0, "sampler_name": "euler",
                "scheduler": "sgm_uniform", "denoise": dn}},
            "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
            "7": {"class_type": "SaveImage", "inputs": {
                "images": ["6", 0], "filename_prefix": "bdraft/" + tag}},
        }
        hist, dt = submit(wf)
        print("denoise %.2f  %.2f초 → %s" % (dn, dt, save(hist, tag)))


if __name__ == "__main__":
    if sys.argv[1] == "base":
        base()
    else:
        open_mouth(tuple(int(v) for v in sys.argv[2:6]))
