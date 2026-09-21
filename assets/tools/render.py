# -*- coding: utf-8 -*-
"""말로 짓는 인형극 — 초안 B 그림 생성 (ComfyUI HTTP API · SDXL base 1.0)

초안 A(안치영)는 krea2 turbo로 만든 '펠트·종이공예 3D'다.
초안 B는 일부러 다른 그림체를 쓴다 — **손으로 오린 색종이(cut paper collage)**.

왜 다른 그림체를 고르나
  · 결정 27은 "세계는 한 그림체, 아이 그림은 원본 그대로 섞인다"이다.
    아이 크레용 그림 옆에 놓였을 때 3D 펠트보다 평면 색종이가 덜 튄다. 그 가설을 눈으로 비교하려고 만든다.
  · 제품 이름이 '종이 인형극'이다. 종이를 오려 붙인 결이 이름과 맞는다.

배경 제거를 모델로 하지 않는다
  · 초안 A는 BiRefNet을 썼다. B는 모델을 더 받지 않으려고 흰 바탕에 그리고
    브라우저에서 흰색을 알파로 뺀다 (index.html의 whiteKey).

────────────────────────────────────────────────────────────────
프롬프트를 고치며 배운 것 (SDXL base 1.0 · RTX 3060 · 2026-09-17)

  1. **길게 쓰면 진다.** 수식어를 스무 개 붙이면 "흰 배경"을 무시하고 장식된 배경을 그린다.
     짧게 한 줄로 쓴 쪽이 훨씬 말을 잘 듣는다.
  2. `die-cut sticker`는 **스티커 시트**를 부른다. 한 마리가 아니라 스무 마리가 나온다. 쓰지 않는다.
  3. `full body`, `zoomed out`을 넣어도 자주 확대해 그린다.
     인형이 상반신 위주로 나오는 것을 받아들이고, 대신 **깨끗한 한 마리**를 얻는 쪽을 골랐다.
  4. 확실히 먹힌 조합:
     `"<주어 하나>, cut paper collage, flat torn paper shapes, centered on a pure white background,
       minimal, lots of white space"`
  5. 네거티브에 `sticker sheet · multiple subjects · scenery · ground`를 넣는 편이
     긍정 문구를 늘리는 것보다 효과가 컸다.

  6. **소품에 색을 지정하지 않으면 흰색으로 그리고, 그걸 보이게 하려고 어두운 카드를 뒤에 깐다.**
     그 카드가 화면에서 네모로 보인다. 소품마다 색을 반드시 적는다.
  7. 1024는 '작품'처럼 크게 그리려 든다. 작은 소품·마스코트는 **768**로 뽑아야 한 덩어리로 깔끔하다.

  → 배경(1344×768)은 반대다. 묘사를 충분히 넣어야 빈 들판이 안 나온다.
────────────────────────────────────────────────────────────────

돌리는 법
  ComfyUI 서버를 띄운 뒤:  python tools/render.py            (전부)
                          python tools/render.py mascot      (하나만)
  결과는 ../assets/<이름>.png · 이미 있으면 건너뛴다
"""
import json, os, time, urllib.request, urllib.parse

API = "http://127.0.0.1:8188"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, "..", "assets"))
os.makedirs(OUT, exist_ok=True)

CKPT = "sd_xl_base_1.0.safetensors"
LORA = "sdxl_lightning_8step_lora.safetensors"   # 배경용 · 09-21 채택

# 인형·소품: 짧게. 이 꼬리표가 핵심이다.
CUT = (", cut paper collage, flat torn paper shapes, children's picture book, "
       "centered on a pure white background, minimal, lots of white space")

# 배경: 길게. 묘사를 빼면 빈 화면이 나온다.
BG = (", cut paper collage landscape, layered torn construction paper, flat 2d shapes, "
      "warm crayon-box colors, children's picture book, no characters, no people, no text")

NEG = ("text, letters, words, watermark, signature, photo, photorealistic, 3d render, "
       "felt, fabric, plush, clay, blurry, ugly, scary, dark, horror, "
       "sticker sheet, multiple subjects, many objects, group, grid, tiled, repeated, "
       "scenery, ground, floor, colored backdrop, frame, border, "
       "realistic face, portrait, human skin, detailed features, anime")

JOBS = [
    # ── 배경 4장 (가로) ──
    ("bg_start", 1344, 768, "a small puppet theater stage with red and orange curtains tied at the sides, "
                            "a wooden stage floor, soft cream wall behind" + BG),
    ("bg_space", 1344, 768, "an empty night sky, deep indigo to purple gradient, a few tiny specks of light, "
                            "nothing else, no planets, no moon" + BG),
    ("bg_sea",   1344, 768, "empty underwater water, turquoise to deep teal gradient, "
                            "a pale sandy seabed strip along the bottom, nothing else" + BG),
    ("bg_dino",  1344, 768, "an empty wide green meadow under a pale blue sky, soft rolling ground, "
                            "nothing else, no trees, no volcano" + BG),

    # ── 진행자 · 주인공 ──
    ("mascot", 768, 768, "one small simple yellow bird shape, whole tiny bird visible, orange beak, black dot eye" + CUT),
    ("hero_a", 1024, 1024, "one flat paper doll of a child, simple cut shapes, short dark hair, round glasses, teal shirt" + CUT),
    ("hero_b", 1024, 1024, "one flat paper doll of a child, simple cut shapes, long dark hair, mustard yellow shirt" + CUT),

    # ── 새 친구 3종 ──
    ("nc_alien",  768, 768, "one small simple green alien shape, whole tiny alien visible, three dot eyes, two antennae" + CUT),
    ("nc_meteor", 1024, 1024, "one grey meteor rock with a smiling face and an orange tail" + CUT),
    ("nc_wind",   1024, 1024, "one white cloud with puffed cheeks blowing wind" + CUT),

    # ── 공룡 3종 ──
    ("dino_trex", 1024, 1024, "one green tyrannosaurus dinosaur, side view" + CUT),
    ("dino_long", 1024, 1024, "one teal long necked dinosaur, side view" + CUT),
    ("dino_horn", 1024, 1024, "one orange triceratops dinosaur with three horns, side view" + CUT),

    # ── 탈것 3종 ──
    ("rocket", 1024, 1024, "one red and cream rocket ship, upright" + CUT),
    ("turtle", 1024, 1024, "one green sea turtle, side view" + CUT),
    ("train",  1024, 1024, "one red and blue steam train, side view" + CUT),

    # ── 미션 물체 ──
    ("prop_fire",  1024, 1024, "one orange and yellow flame" + CUT),
    ("prop_smoke", 768, 768, "one small simple grey smoke puff shape" + CUT),
    ("prop_cloud", 768, 768, "one small simple grey rain cloud shape with three raindrops below" + CUT),
    ("prop_well",  1024, 1024, "one round stone well with a small roof" + CUT),

    # ── 배경 소품 라이브러리 ──
    # 14종뿐이다. 세계마다 새로 만들지 않고 배치·색·크기로 다르게 보이게 한다 (index.html의 scatter)
    ("sp_tree",   768, 768, "one simple green tree with a brown trunk" + CUT),
    ("sp_bush",   768, 768, "one simple round green bush" + CUT),
    ("sp_grass",   768, 768, "one simple tuft of green grass blades" + CUT),
    ("sp_rock_l",   768, 768, "one simple large grey brown boulder" + CUT),
    ("sp_rock_s",   768, 768, "one simple small grey pebble" + CUT),
    ("sp_cloud",   768, 768, "one simple pale blue grey fluffy cloud" + CUT),
    ("sp_star",   768, 768, "one simple yellow five pointed star" + CUT),
    ("sp_moon",   768, 768, "one simple pale yellow crescent moon" + CUT),
    ("sp_planet",   768, 768, "one simple orange planet with a ring" + CUT),
    ("sp_house",   768, 768, "one simple yellow house with a red triangle roof" + CUT),
    ("sp_tower",   768, 768, "one simple blue tall narrow building with square windows" + CUT),
    ("sp_bird",   768, 768, "one simple dark blue tiny bird" + CUT),
    ("sp_bunny",   768, 768, "one simple brown rabbit sitting" + CUT),
    ("sp_weed",   768, 768, "one simple dark green tall seaweed frond" + CUT),
]


def post(path, data):
    req = urllib.request.Request(API + path, data=json.dumps(data).encode(),
                                 headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=180))


def get(path):
    return json.load(urllib.request.urlopen(API + path, timeout=180))


def workflow(prompt, w, h, prefix, seed, fast=None):
    """배경은 8스텝 LoRA, 그 외는 28스텝.

    09-21 측정(`eval/results.md`): 배경 한 장이 19.07초 → 3.74초로 5.1배 빨라지고
    VRAM은 그대로다. 4스텝도 재봤는데 종이 결이 물감처럼 뭉개지고 네거티브가
    풀려서 버렸다 — 되돌리는 손잡이는 세기가 아니라 **스텝 수**였다.

    ⚠️ 인형·소품(CUT)에는 기본으로 안 켠다. 이미 만들어 둔 것들이 28스텝이라
    섞이면 결이 달라 보인다. 배경은 세션 중에 새로 만들므로 속도가 값이 된다.
    """
    if fast is None:
        fast = w > h          # 배경만 가로로 뽑는다 (1344x768)
    wf = {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": CKPT}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": NEG, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage", "inputs": {"width": w, "height": h, "batch_size": 1}},
        "5": {"class_type": "KSampler", "inputs": {
            "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["4", 0],
            "seed": seed, "steps": 28, "cfg": 6.5,
            "sampler_name": "dpmpp_2m", "scheduler": "karras", "denoise": 1}},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {"images": ["6", 0], "filename_prefix": "bdraft/" + prefix}},
    }
    if fast:
        wf["8"] = {"class_type": "LoraLoaderModelOnly", "inputs": {
            "model": ["1", 0], "lora_name": LORA, "strength_model": 1.0}}
        # Lightning은 cfg를 거의 1로 두고 sgm_uniform을 써야 한다.
        # 28스텝 설정을 그대로 주면 그림이 하얗게 날아간다.
        wf["5"]["inputs"].update({
            "model": ["8", 0], "steps": 8, "cfg": 1.0,
            "sampler_name": "euler", "scheduler": "sgm_uniform"})
    return wf


def run(name, w, h, prompt, seed):
    r = post("/prompt", {"prompt": workflow(prompt, w, h, name, seed)})
    pid = r["prompt_id"]
    while True:
        hist = get("/history/" + pid)
        if pid in hist:
            for node in hist[pid]["outputs"].values():
                for img in node.get("images", []):
                    q = urllib.parse.urlencode(img)
                    data = urllib.request.urlopen(API + "/view?" + q, timeout=180).read()
                    with open(os.path.join(OUT, name + ".png"), "wb") as f:
                        f.write(data)
                    return True
            return False
        time.sleep(1.2)


if __name__ == "__main__":
    import sys
    only = set(sys.argv[1:])
    t0 = time.time()
    for i, (name, w, h, prompt) in enumerate(JOBS):
        if only and name not in only:
            continue
        if os.path.exists(os.path.join(OUT, name + ".png")):
            print(f"[skip] {name}", flush=True)
            continue
        print(f"[{i+1}/{len(JOBS)}] {name} {w}x{h}", flush=True)
        try:
            run(name, w, h, prompt, 1000 + i)
            print(f"        ok ({time.time()-t0:.0f}s)", flush=True)
        except Exception as e:
            print(f"        FAIL {e}", flush=True)
    print("done.")
