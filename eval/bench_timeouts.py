"""How long each route takes as the answer gets longer — to set the deadlines in guidelines/3 §3-0.

Hits the real team server (public https by default) in sequence, N rounds per case, and prints
p50 · p95 · max · failures per case. Speech for /stt is made by /tts from the same texts, so the
"long answer" is an adult synthetic voice, not a child's (a limit — see results.md).

  py eval/bench_timeouts.py [--base https://otto-back.shelldocs.cloud] [--rounds 5]

Cloudflare blocks Python's default User-Agent (403 · 1010), so one is set.
Costs a few hundred won (LLM + TypeCast). 10-01 조장.
"""
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[sys.argv.index("--base") + 1] if "--base" in sys.argv else "https://otto-back.shelldocs.cloud"
ROUNDS = int(sys.argv[sys.argv.index("--rounds") + 1]) if "--rounds" in sys.argv else 5
UA = {"User-Agent": "otto-bench/1.0"}

TEXTS = {   # what the child says — short / one sentence / a long run of events (about 15 s spoken)
    "short": "바닷속",
    "medium": "문어가 먹물을 쏘아서 바다가 깜깜해졌어",
    "long": ("바닷속에 갔는데 문어가 갑자기 먹물을 쏘아서 바다가 깜깜해졌어 그래서 나는 무서워서 "
             "친구 손을 꼭 잡았는데 그때 반짝이는 해파리가 와서 길을 밝혀 줬어 그래서 우리는 집에 돌아왔어"),
}
LINES = {   # what the mascot says — one line / one book page read aloud
    "line": "문어가 먹물을 쏘았구나! 그다음엔 어떻게 됐어?",
    "page": ("바닷속은 조용하고 파랬어요. 그런데 갑자기 문어가 먹물을 쏘아서 바다가 깜깜해졌어요. "
             "무서웠지만 친구 손을 꼭 잡았더니 반짝이는 해파리가 와서 길을 밝혀 주었어요."),
}
FULL = {"place": "바닷속", "companion": "{친구1}", "newcomer": "문어", "problem": "문어가 먹물을 쏘았다",
        "cause": "문어가 놀라서", "solution": "해파리가 길을 밝혀 주었다", "reaction": "기뻤다", "sound": "뽀글뽀글"}


def post(path, body, ctype="application/json", timeout=90):
    data = body if isinstance(body, bytes) else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data, {"Content-Type": ctype, **UA})
    t = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return time.monotonic() - t, r.status, r.read()
    except urllib.error.HTTPError as e:
        return time.monotonic() - t, e.code, e.read()
    except Exception as e:
        return time.monotonic() - t, 0, str(e).encode()


def multipart(audio: bytes, name: str):
    b = uuid.uuid4().hex
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
            f"Content-Type: audio/mpeg\r\n\r\n").encode() + audio + f"\r\n--{b}--\r\n".encode()
    return body, f"multipart/form-data; boundary={b}"


def report(case, times, fails):
    if times:
        s = sorted(times)
        p95 = s[min(len(s) - 1, round(0.95 * (len(s) - 1)))]
        print(f"{case:<28} n={len(times)} p50={statistics.median(s):5.1f}s p95={p95:5.1f}s max={s[-1]:5.1f}s fail={fails}")
    else:
        print(f"{case:<28} all failed ({fails})")
    sys.stdout.flush()


def run(case, fn):
    times, fails = [], []
    for _ in range(ROUNDS):
        dt, code, body = fn()
        if code == 200:
            times.append(dt)
        else:
            fails.append(f"{code}:{body[:60].decode(errors='replace')}@{dt:.0f}s")
    report(case, times, fails)


def main():
    print(f"base {BASE} · rounds {ROUNDS} · {time.strftime('%Y-%m-%d %H:%M')}")
    audio = {}
    for k, text in {**LINES, **TEXTS}.items():
        dt, code, body = post("/tts", {"text": text})
        if code == 200 and k in TEXTS:
            audio[k] = body
    for k, text in LINES.items():
        run(f"tts {k} ({len(text)} chars)", lambda t=text: post("/tts", {"text": t}))
    for k, a in audio.items():
        run(f"stt {k} ({len(a) // 1024} KB mp3)", lambda a=a: post("/stt", *multipart(a, "turn.mp3")))
    for k, text in TEXTS.items():
        body = {"mode": "story", "slots": {"place": "바닷속", "companion": "{친구1}"}, "asked_slot": "problem",
                "question": "그런데 무슨 일이 생겼어?", "utterance": text, "turn": 4}
        run(f"turn {k} ({len(text)} chars)", lambda b=body: post("/turn", b))
    books = {
        "diary 1 page": {"mode": "diary", "slots": {"place": "놀이터", "problem": "그네를 탔다"}},
        "diary 4 pages": {"mode": "diary", "slots": {k: FULL[k] for k in ("place", "problem", "cause", "solution")}},
        "story 6 pages": {"mode": "story", "slots": FULL},
    }
    for k, body in books.items():
        run(f"story {k}", lambda b=body: post("/story", b))


if __name__ == "__main__":
    main()
