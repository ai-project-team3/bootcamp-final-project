# -*- coding: utf-8 -*-
"""서버 캐릭터 생성 속도 측정 (09-30) — 켜 둔 로컬 서버(`/image kind=character`)에 아이 말처럼 묘사를 보내고
끝까지 걸린 시간 · 결과(그림 / 프리셋)를 적는다. 받은 그림은 `<몸종류>__<번호>_<이름>.png` 로 저장해
앱 뼈대 검사(`RigBuilderTest.생성_캐릭터_모음`, 환경 변수 RIG_CORPUS)로 바로 넘긴다.

    python tools/bench_character.py OUT_DIR [--url http://127.0.0.1:8010] [--rounds 1]
결과: OUT_DIR/*.png · OUT_DIR/bench.tsv (번호 · 묘사 · 걸린 초 · 결과 · 몸 종류 · 영어 묘사)
단계별 시간(LLM · 그림 · 검사)은 서버 로그의 `image character scene … draw … check …` 줄에 있다.
"""
import base64
import json
import os
import sys
import time
import urllib.request

WORDS = [
    # 사람형 — 아이가 말할 법한 말 그대로
    "분홍 드레스 입은 공주", "은색 로봇", "우주복 입은 우주인", "빨간 옷 입은 소방관 아저씨", "갈색 곰 인형",
    "빨간 목도리 한 펭귄", "해적 선장", "안경 쓴 할머니", "보라색 망토 입은 슈퍼히어로", "멜빵바지 입은 하얀 토끼",
    "날개 달린 요정", "고깔모자 쓴 마법사", "풍선 든 아이", "치마 입은 발레리나",
    # 네발형
    "아기 공룡", "강아지", "줄무늬 고양이", "무지개 갈기 조랑말", "아기 코끼리", "아기 사자",
    # 덩어리형
    "문어", "눈사람", "해파리", "구름", "별", "금붕어",
]


def call(url, words):
    body = json.dumps({"kind": "character", "description": words, "mode": "story"}).encode()
    req = urllib.request.Request(url + "/image", data=body, headers={"Content-Type": "application/json"})
    t0 = time.monotonic()
    j = json.load(urllib.request.urlopen(req, timeout=60))
    return time.monotonic() - t0, j


if __name__ == "__main__":
    out = sys.argv[1]
    args = sys.argv[2:]
    url = args[args.index("--url") + 1] if "--url" in args else "http://127.0.0.1:8010"
    rounds = int(args[args.index("--rounds") + 1]) if "--rounds" in args else 1
    os.makedirs(out, exist_ok=True)
    rows = []
    n = 0
    for r in range(rounds):
        for w in WORDS:
            dt, j = call(url, w)
            ok = not j.get("preset")
            name = ""
            if ok and j.get("png_base64"):
                slug = (j.get("scene") or "x").split(" with ")[0].replace(" ", "_")[:24]
                name = f"{j.get('rig')}__{n:02d}_{slug}.png"
                open(os.path.join(out, name), "wb").write(base64.b64decode(j["png_base64"]))
            rows.append((n, w, f"{dt:.2f}", "그림" if ok else f"프리셋({j.get('reason')})", j.get("rig") or "", j.get("scene") or "", name))
            print("\t".join(map(str, rows[-1])), flush=True)
            n += 1
    with open(os.path.join(out, "bench.tsv"), "w", encoding="utf-8") as f:
        f.write("번호\t아이 말\t초\t결과\t몸 종류\t영어 묘사\t파일\n")
        for row in rows:
            f.write("\t".join(map(str, row)) + "\n")
    t = [float(r[2]) for r in rows if r[3] == "그림"]
    if t:
        t.sort()
        print(f"그림 {len(t)}/{len(rows)} · 중앙 {t[len(t) // 2]:.2f}s · 최소 {t[0]:.2f}s · 최대 {t[-1]:.2f}s", flush=True)
    print("ALL DONE", flush=True)
