# -*- coding: utf-8 -*-
"""리액션을 미리 구워 번들에 넣을 수 있나 — 그리고 그게 지연을 얼마나 덮나.

왜 재나
  마스코트 본 대사는 **아이 말을 되비추므로 미리 못 굽는다**("공룡 나라구나!"의
  「공룡 나라」는 아이가 방금 한 말이다). 그래서 TTS는 전부 API 호출이 되고,
  판정 p95 2.4초 위에 TTS 왕복이 더 붙는다.

  빠져나갈 구멍이 하나 있다. **리액션은 진짜로 고정이다** — "음~" "그랬구나"는
  아이가 뭐라 하든 같은 문장이다. 같은 모델·같은 목소리로 구워 두면 **VAD가 말 끝을
  잡는 즉시** 0ms로 틀 수 있고, 그 뒤로 STT·판정·TTS가 전부 숨는다.

  그래서 이 측정이 답해야 하는 건 둘이다:
    1. 구운 파일을 번들에 넣을 만한가 (길이·용량)
    2. **리액션이 덮어야 하는 구멍보다 긴가** — 리액션 길이 vs 본 대사 TTS 시간

⚠️ 구운 리액션은 **내용 중립**이어야 한다. 판정 전에 나가므로 아이가 무슨 말을 했는지
   모른다. "우와 신난다!"를 틀었는데 아이가 "무서웠어"라고 했으면 사고다.

    python bench_tts.py          (기본 목소리 · 리액션 6개 + 본 대사 3개)
"""
import json
import os
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tts_out")
MODEL = "gpt-4o-mini-tts"
VOICE = "coral"          # 아이 목소리에 가장 가까운 축. 최종 선정은 귀 판정이다
FMT = "mp3"
INSTR = ("여섯 살 또래 친구처럼, 높고 밝게. 또박또박, 조금 빠르게. "
         "과장하지 말고 자연스럽게.")

# 미리 구울 수 있는 것 — 아이 말과 무관하게 언제나 같은 문장이다.
FILLERS = ["음~", "어디 보자…", "그랬구나!", "오~", "잠깐만, 생각 좀 하고!", "우와, 좋은 생각이다!"]
# 미리 못 굽는 것 — 아이 말이 안에 들어간다. 매 턴 API를 불러야 한다.
LIVE = ["공룡 나라구나! 공룡들이 사는 곳에 지호가 갔어. 거기서 누굴 만났어?",
        "외계인이 장난을 쳤구나! 로켓을 꽉 잡고 흔들었대. 그래서 어떻게 됐어?",
        "괜찮아, 천천히 생각해도 돼. 지호가 제일 먼저 본 건 뭐였을까?"]


def key():
    for line in open(os.path.join(ROOT, ".env"), encoding="utf-8"):
        if line.strip().startswith("OPENAI_API_KEY="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    sys.exit(".env에 OPENAI_API_KEY가 없다")


def speak(text, api, name):
    """첫 바이트까지와 전체를 따로 잰다 — 지연을 덮는 건 첫 바이트다."""
    body = json.dumps({"model": MODEL, "voice": VOICE, "input": text,
                       "instructions": INSTR, "response_format": FMT}).encode()
    req = urllib.request.Request(
        "https://api.openai.com/v1/audio/speech", body,
        {"Authorization": "Bearer " + api, "Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as r:
        first = r.read(1)
        ttfb = time.time() - t0
        blob = first + r.read()
    total = time.time() - t0
    path = os.path.join(OUT, name + "." + FMT)
    open(path, "wb").write(blob)
    return ttfb, total, len(blob), path


# ⚠️ OpenAI는 mp3를 MPEG-2 / 24kHz로 준다. MPEG-1만 보는 파서는 프레임을 하나도
# 못 찾고 길이 0.00초를 돌려준다 — 한 번 이걸로 "리액션이 0초"라는 헛것을 봤다.
RATES = {0: [11025, 12000, 8000], 2: [22050, 24000, 16000], 3: [44100, 48000, 32000]}
BR_V1 = [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0]
BR_V2 = [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0]


def mp3_seconds(path):
    """mp3 길이 — 프레임 헤더를 세는 최소 구현(외부 의존 없이). Layer III만."""
    data = open(path, "rb").read()
    i, dur = 0, 0.0
    if data[:3] == b"ID3":                      # ID3v2 헤더(syncsafe 길이)를 건너뛴다
        b = data[6:10]
        i = 10 + ((b[0] << 21) | (b[1] << 14) | (b[2] << 7) | b[3])
    while i + 4 <= len(data):
        if data[i] != 0xFF or (data[i + 1] & 0xE0) != 0xE0:
            i += 1
            continue
        ver, layer = (data[i + 1] >> 3) & 3, (data[i + 1] >> 1) & 3
        br_i, sr_i = data[i + 2] >> 4, (data[i + 2] >> 2) & 3
        if ver == 1 or layer != 1 or br_i in (0, 15) or sr_i == 3:
            i += 1
            continue
        br = (BR_V1 if ver == 3 else BR_V2)[br_i] * 1000
        sr = RATES[ver][sr_i]
        spf = 1152 if ver == 3 else 576         # MPEG2/2.5 Layer III는 프레임당 576
        size = spf // 8 * br // sr + ((data[i + 2] >> 1) & 1)
        dur += spf / sr
        i += max(size, 1)
    return dur


def main():
    os.makedirs(OUT, exist_ok=True)
    api = key()
    print("%s · voice=%s\n" % (MODEL, VOICE))

    print("■ 미리 구울 수 있는 것 (리액션 — 내용 중립)")
    bundle, cover = 0, []
    for i, t in enumerate(FILLERS):
        ttfb, total, n, p = speak(t, api, "filler_%d" % i)
        sec = mp3_seconds(p)
        bundle += n
        cover.append(sec)
        print("  %-22s 길이 %4.2f초  %5.1f KB   (구울 때 %4.2f초)" % (t, sec, n / 1024, total))
    print("  → 6개 번들 %.0f KB · 재생 지연 0ms (로컬 파일)\n" % (bundle / 1024))

    print("■ 미리 못 굽는 것 (본 대사 — 아이 말이 들어간다)")
    ttfbs = []
    for i, t in enumerate(LIVE):
        ttfb, total, n, p = speak(t, api, "live_%d" % i)
        ttfbs.append(ttfb)
        print("  첫 소리 %4.2f초 · 전체 %4.2f초 · 길이 %4.2f초  %s…" % (ttfb, total, mp3_seconds(p), t[:16]))

    gap = sum(ttfbs) / len(ttfbs)
    print("\n■ 리액션이 구멍을 덮는가")
    print("  덮어야 하는 것 = 판정 p95 2.40초 + TTS 첫 소리 %.2f초 = %.2f초" % (gap, 2.40 + gap))
    print("  가장 긴 리액션 %.2f초 · 가장 짧은 것 %.2f초" % (max(cover), min(cover)))
    print("  → %s" % ("덮는다" if max(cover) >= 2.40 + gap else
                      "안 덮는다 — %.2f초가 모자란다" % (2.40 + gap - max(cover))))
    print("\n파일: %s" % OUT)


if __name__ == "__main__":
    main()
