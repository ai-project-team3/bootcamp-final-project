# -*- coding: utf-8 -*-
"""뼈대 실험 결과를 **서버가 보낼 모양**으로 내보낸다 — 앱 시연용 (2026-09-28)

실제 서비스에서는 서버가 캐릭터를 생성하고 오려서 **조각 그림 + 관절 정보(JSON)** 를 폰에 내려 준다.
여기서는 그 자리를 `app/src/main/assets/rig/<이름>/` 이 대신한다. 앱(`ui/Rig.kt`)은 이것만 읽는다.

  rig.json   { "kind", "canvas": [w, h], "parts": [{ "name", "image", "x", "y", "pivot": [x, y] | null, "z" }] }
             x · y 는 조각 그림의 왼쪽 위 자리(캔버스 좌표) · pivot 은 도는 축 · z 가 작을수록 뒤에 그린다
  <조각>.png 제 모양대로 잘라 낸 그림 (빈 가장자리 없음)

쓰는 법  python tools/rig_export.py
"""
import json, os, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig_pose_check as rc

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "rig")
SCALE = 0.5   # 1024 → 512. 폰 화면에서 이 이상은 필요 없다
PICK = {"kid": ("human_60_22", "human"), "dino": ("quad2_60_22", "quad2")}

for name, (tag, kind) in PICK.items():
    im = Image.open(os.path.join(rc.OUT, tag + "_cut.png")).convert("RGBA").resize((rc.N, rc.N))
    res = rc.human(tag, im) if kind == "human" else rc.quad(tag, im, kind)
    out = os.path.join(ASSETS, name); os.makedirs(out, exist_ok=True)
    parts = []
    for pname, img, pivot, z in res["pieces"]:
        bb = img.getbbox()
        if bb is None:
            continue
        piece = img.crop(bb)
        piece = piece.resize((max(1, round(piece.width * SCALE)), max(1, round(piece.height * SCALE))), Image.LANCZOS)
        piece.save(os.path.join(out, pname + ".png"), optimize=True)
        parts.append({"name": pname, "image": pname + ".png", "x": round(bb[0] * SCALE), "y": round(bb[1] * SCALE),
                      "pivot": [round(pivot[0] * SCALE), round(pivot[1] * SCALE)] if pivot else None, "z": z})
    json.dump({"kind": kind.rstrip("2"), "canvas": [round(rc.N * SCALE)] * 2, "parts": parts},
              open(os.path.join(out, "rig.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    size = sum(os.path.getsize(os.path.join(out, f)) for f in os.listdir(out))
    print(f"{name}: 조각 {len(parts)}개 · {size / 1024:.0f}KB · {[p['name'] for p in parts]}")
