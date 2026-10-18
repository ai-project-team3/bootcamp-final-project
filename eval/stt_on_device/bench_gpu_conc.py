"""Server STT under N children talking at once: one GPU, one model, one lock (as routers/stt.py).

Each round, N clips (3-5 year olds) arrive at the same instant; we record how long each child waits
from sending to getting text back. Rounds use different clips. Output: per-N p50/p90/max wait,
GPU busy time per clip, and throughput.
"""
import csv, glob, io, json, os, random, statistics, sys, sysconfig, threading, time
from concurrent.futures import ThreadPoolExecutor

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
random.seed(16)
random.shuffle(rows)
audio = {r["name"]: open(os.path.join(HERE, "stage", r["name"]), "rb").read() for r in rows[:400]}
secs = {r["name"]: float(r["sec"]) for r in rows[:400]}
names = list(audio)

model = WhisperModel("large-v3", device="cuda", compute_type="int8_float16")
lock = threading.Lock()


def stt(name):
    t0 = time.perf_counter()
    with lock:
        g0 = time.perf_counter()
        list(model.transcribe(io.BytesIO(audio[name]), language="ko", beam_size=5)[0])
        g1 = time.perf_counter()
    return g1 - t0, g1 - g0


for n in names[:5]:  # warm up
    stt(n)

NS = [int(x) for x in sys.argv[1:]] or [1, 2, 4, 8, 16, 32]
ROUNDS = 6
cur = 0
out = []
for n in NS:
    waits, gpu = [], []
    t_all = time.perf_counter()
    for _ in range(ROUNDS):
        batch = [names[(cur + i) % len(names)] for i in range(n)]
        cur += n
        with ThreadPoolExecutor(n) as ex:
            for w, g in ex.map(stt, batch):
                waits.append(w); gpu.append(g)
    wall = time.perf_counter() - t_all
    waits.sort()
    rec = dict(n=n, clips=len(waits), p50=statistics.median(waits),
               p90=waits[int(0.9 * (len(waits) - 1))], max=waits[-1],
               gpu_per_clip=statistics.mean(gpu), clips_per_s=len(waits) / wall)
    out.append(rec)
    print(json.dumps(rec), flush=True)

print("mean clip sec", statistics.mean(secs.values()))
json.dump(out, open(os.path.join(HERE, "gpu_conc.json"), "w"), indent=1)
