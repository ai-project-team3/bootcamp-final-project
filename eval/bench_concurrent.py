# -*- coding: utf-8 -*-
"""셋이 겹쳐 돌면 서로 얼마나 느려지나.

09-21에 "셋 다 한 카드에 올라간다"를 확인했지만 그건 **자리**만 본 것이다.
10,437 MiB가 들어간다는 것과 **동시에 돌려도 쓸 만하다**는 것은 다른 말이고,
덱에도 그렇게 미결로 적었다. 이 스크립트가 그 줄을 닫는다.

재는 방식
  1부 — 혼자 돌린다. 받아쓰기 · 화자 구분 · 그림을 각각.
  2부 — 그림을 쉬지 않고 돌리는 스레드를 띄워 두고 그 위에서 받아쓰기·화자 구분을 돌린다.
        같은 창 안에서 그림이 몇 초 걸렸는지도 같이 모은다.

⚠️ 반드시 `.venv-diar`(Python 3.12 · torch cu126)로 돌린다. 기본 파이썬은 3.14라
   CUDA 휠이 없어 **torch가 CPU판으로 조용히 깔린다** — 09-21에 이걸로 한 번 속았고,
   그때는 오류 하나 없이 VRAM 0으로 찍혔다.

    .venv-diar/Scripts/python.exe eval/bench_concurrent.py
"""
import json
import os
import statistics
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from eval.bench_coresident import add_cuda_dlls  # noqa: E402
from eval.bench_diarization import device_mb, make_wav  # noqa: E402

COMFY = "http://127.0.0.1:8188"
SECONDS = 10          # 아이 한 턴의 현실적인 길이
ROUNDS = 3
CKPT = "sd_xl_base_1.0.safetensors"
LORA = "sdxl_lightning_8step_lora.safetensors"
POS = ("a friendly cartoon dinosaur standing, cut paper collage, "
       "flat torn paper shapes, centered on a pure white background")
NEG = "text, watermark, photo, 3d render, sticker sheet, multiple subjects"


def sdxl(seed):
    """배경이 아니라 캐릭터 설정(768x768 · 8스텝)으로 돈다 — 세션 중에 제일 자주 돈다."""
    wf = {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": CKPT}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": POS, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": NEG, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage",
              "inputs": {"width": 768, "height": 768, "batch_size": 1}},
        "8": {"class_type": "LoraLoaderModelOnly",
              "inputs": {"model": ["1", 0], "lora_name": LORA, "strength_model": 1.0}},
        "5": {"class_type": "KSampler", "inputs": {
            "model": ["8", 0], "positive": ["2", 0], "negative": ["3", 0],
            "latent_image": ["4", 0], "seed": seed, "steps": 8, "cfg": 1.0,
            "sampler_name": "euler", "scheduler": "sgm_uniform", "denoise": 1}},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage",
              "inputs": {"images": ["6", 0], "filename_prefix": "conc/x"}},
    }
    body = json.dumps({"prompt": wf}).encode()
    req = urllib.request.Request(COMFY + "/prompt", body,
                                 {"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as r:
        pid = json.load(r)["prompt_id"]
    while True:
        with urllib.request.urlopen(COMFY + "/history/" + pid, timeout=30) as r:
            if pid in json.load(r):
                return time.time() - t0
        time.sleep(0.1)


class Load:
    """그림을 쉬지 않고 돌리는 배경 부하. 걸린 시간을 모아 둔다."""

    def __init__(self):
        self.times, self.stop = [], threading.Event()
        self.t = threading.Thread(target=self._run, daemon=True)

    def _run(self):
        seed = 91000
        while not self.stop.is_set():
            # 회차마다 seed를 바꾼다 — 같은 seed면 ComfyUI가 캐시로 0.25초를 돌려준다
            self.times.append(sdxl(seed))
            seed += 1

    def __enter__(self):
        self.t.start()
        time.sleep(3)          # 부하가 실제로 걸린 뒤에 재기 시작한다
        return self

    def __exit__(self, *a):
        self.stop.set()
        self.t.join(timeout=180)


def main():
    add_cuda_dlls()
    import torch
    from pyannote.audio import Pipeline
    from faster_whisper import WhisperModel

    token = os.environ.get("HF_TOKEN") or _from_env("HF_TOKEN")
    # ⚠️ 합성 사인파로 재면 안 된다. 말소리가 없어서 받아쓰기도 화자 구분도
    # 할 일이 없다고 판단하고 빠져나간다 — 화자 구분이 0.01초로 찍혔다.
    # `bench_tts.py`가 구워 둔 **진짜 한국어 음성**을 이어 붙여 쓴다.
    wav = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tts_out", "real10s.wav")
    if not os.path.exists(wav):
        raise SystemExit("먼저 bench_tts.py를 돌려 tts_out/을 채운다")

    print("모델 올리는 중…", flush=True)
    stt = WhisperModel("large-v3-turbo", device="cuda", compute_type="int8_float16")
    os.environ["HF_TOKEN"] = token
    _load = torch.load
    torch.load = lambda *a, **k: _load(*a, **{**k, "weights_only": False})
    diar = Pipeline.from_pretrained("pyannote/speaker-diarization-3.1")
    diar.to(torch.device("cuda"))

    def run_stt():
        t0 = time.time()
        for _ in stt.transcribe(wav, language="ko", beam_size=5)[0]:
            pass
        return time.time() - t0

    def run_diar():
        t0 = time.time()
        diar(wav, num_speakers=2)
        return time.time() - t0

    run_stt(); run_diar(); sdxl(90000)          # 워밍업 — 첫 회는 항상 느리다

    print("\n■ 1부 — 혼자 돌릴 때", flush=True)
    alone = {}
    alone["받아쓰기"] = [run_stt() for _ in range(ROUNDS)]
    alone["화자 구분"] = [run_diar() for _ in range(ROUNDS)]
    alone["그림"] = [sdxl(90100 + i) for i in range(ROUNDS)]
    for k, v in alone.items():
        print("  %-8s %s  평균 %.2f초" % (k, " ".join("%.2f" % x for x in v),
                                        statistics.mean(v)), flush=True)

    print("\n■ 2부 — 그림이 쉬지 않고 도는 위에서", flush=True)
    base_mb = device_mb()
    with Load() as load:
        both = {"받아쓰기": [run_stt() for _ in range(ROUNDS)],
                "화자 구분": [run_diar() for _ in range(ROUNDS)]}
        peak = device_mb()
        both["그림"] = list(load.times)
    for k in ("받아쓰기", "화자 구분", "그림"):
        v = both[k]
        print("  %-8s %s  평균 %.2f초" % (k, " ".join("%.2f" % x for x in v[:6]),
                                        statistics.mean(v)), flush=True)

    print("\n■ 얼마나 느려지나", flush=True)
    for k in ("받아쓰기", "화자 구분", "그림"):
        a, b = statistics.mean(alone[k]), statistics.mean(both[k])
        print("  %-8s %5.2f → %5.2f초   %+.0f%%" % (k, a, b, (b / a - 1) * 100))
    print("\n  장치 메모리 %.0f → %.0f MiB (12,287 중)" % (base_mb, peak))
    print("  ※ %d초짜리 소리 · 회차마다 seed 변경" % SECONDS)


def _from_env(name):
    p = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".env")
    for line in open(p, encoding="utf-8"):
        if line.strip().startswith(name + "="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    sys.exit(".env에 %s가 없다" % name)


if __name__ == "__main__":
    main()
