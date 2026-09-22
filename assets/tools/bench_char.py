# -*- coding: utf-8 -*-
"""캐릭터 한 장이 몇 초인가 — 배경(1344x768 · 8스텝 · 3.74초)과 나란히 놓으려고 잰다.

왜 따로 재나
  배경은 8스텝을 쓰기로 했지만 **캐릭터에는 안 켰다**(이미 만들어 둔 소품이 28스텝이라
  섞으면 결이 달라 보여서다). 그런데 캐릭터를 세션 중에 만든다면 28스텝 12.3초는
  「좋아/싫어 뒤 최대 2회 재생성」을 감당 못 한다 — 3장이면 37초다.
  그래서 **캐릭터에도 8스텝을 켜면 몇 초인지**가 실제로 물어야 할 값이다.

⚠️ 회차마다 seed를 바꾼다. ComfyUI는 워크플로 해시로 캐시해서, 같은 seed로 다시 돌리면
   0.25초가 찍힌다 — 09-21에 이걸로 한 번 속았다.

    python bench_char.py
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render  # noqa: E402  (workflow/CUT/NEG를 그대로 쓴다)

API = render.API
SEED = 70000
RUNS = 3
PROMPT = "a friendly cartoon dinosaur standing" + render.CUT


def wait(pid):
    """1.2초 폴링으로는 3초짜리를 못 잰다. 0.12초로 좁힌다."""
    while True:
        with urllib.request.urlopen(API + "/history/" + pid, timeout=30) as r:
            hist = json.load(r)
        if pid in hist:
            return hist[pid]
        time.sleep(0.12)


def one(steps, seed, tag):
    wf = render.workflow(PROMPT, 768, 768, tag, seed, fast=(steps == 8))
    if steps != 8:
        wf["5"]["inputs"]["steps"] = steps
    body = json.dumps({"prompt": wf}).encode()
    req = urllib.request.Request(API + "/prompt", body,
                                 {"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=60) as r:
        pid = json.load(r)["prompt_id"]
    hist = wait(pid)
    dt = time.time() - t0

    for node in hist["outputs"].values():
        for img in node.get("images", []):
            q = urllib.parse.urlencode(img)
            data = urllib.request.urlopen(API + "/view?" + q, timeout=120).read()
            open(os.path.join(render.OUT, tag + ".png"), "wb").write(data)
    return dt


def main():
    print("768x768 캐릭터 · RTX 3060 · ComfyUI 기동 상태\n")
    for steps in (8, 28):
        ts = [one(steps, SEED + steps * 10 + i, "charbench_%d_%d" % (steps, i))
              for i in range(RUNS)]
        print("%2d 스텝  %s  평균 %.2f초" %
              (steps, "  ".join("%.2f" % t for t in ts), sum(ts) / len(ts)))


if __name__ == "__main__":
    main()
