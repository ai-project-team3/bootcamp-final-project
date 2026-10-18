"""Server STT with N live sessions: each child speaks once every 20-30 s (load test scenario A, #460).

One GPU, one model, one lock, as routers/stt.py. Each session starts at a random moment and then
sends a 3-5 year old clip every uniform(20, 30) s. We record send -> text wait per clip.
"""
import csv, glob, io, json, os, random, statistics, sys, sysconfig, threading, time

site = sysconfig.get_paths()["purelib"]
for sub in ("cublas", "cudnn"):
    d = os.path.join(site, "nvidia", sub, "bin")
    if glob.glob(os.path.join(d, "*.dll")):
        os.add_dll_directory(d)
        os.environ["PATH"] = d + os.pathsep + os.environ["PATH"]

from faster_whisper import WhisperModel

HERE = os.path.dirname(os.path.abspath(__file__))
rows = [r for r in csv.DictReader(open(os.path.join(HERE, "clips.csv"), encoding="utf-8-sig"))
        if r["set"] == "child" and r["age"] in ("3", "4", "5")]
random.seed(18)
random.shuffle(rows)
audio = [open(os.path.join(HERE, "stage", r["name"]), "rb").read() for r in rows[:400]]

model = WhisperModel("large-v3", device="cuda", compute_type="int8_float16")
lock = threading.Lock()
for a in audio[:3]:
    list(model.transcribe(io.BytesIO(a), language="ko", beam_size=5)[0])

DUR = float(os.environ.get("DUR", "120"))


def session(i, stop_at, waits, rng):
    t = time.perf_counter() + rng.uniform(0, 25)
    while True:
        now = time.perf_counter()
        if t > stop_at:
            return
        if t > now:
            time.sleep(t - now)
        a = audio[rng.randrange(len(audio))]
        t0 = time.perf_counter()
        with lock:
            list(model.transcribe(io.BytesIO(a), language="ko", beam_size=5)[0])
        waits.append(time.perf_counter() - t0)
        t = t0 + rng.uniform(20, 30)


out = []
for n in [int(x) for x in sys.argv[1:]]:
    waits = []
    stop_at = time.perf_counter() + DUR
    th = [threading.Thread(target=session, args=(i, stop_at, waits, random.Random(n * 1000 + i)))
          for i in range(n)]
    for x in th: x.start()
    for x in th: x.join()
    waits.sort()
    rec = dict(n=n, clips=len(waits), p50=statistics.median(waits),
               p90=waits[int(0.9 * (len(waits) - 1))], p99=waits[int(0.99 * (len(waits) - 1))],
               max=waits[-1], over3s=sum(w > 3 for w in waits) / len(waits))
    out.append(rec)
    print(json.dumps(rec), flush=True)
json.dump(out, open(os.path.join(HERE, "gpu_sessions.json"), "w"), indent=1)
