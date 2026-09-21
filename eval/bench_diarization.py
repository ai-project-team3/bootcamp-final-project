"""화자 분리를 그림 옆에 얹을 자리가 있나.

조합 A의 마지막 관문이다. 09-21에 그림(ComfyUI)과 받아쓰기(faster-whisper)가
9,659 MiB로 같이 올라가는 것은 확인했고, 남은 2,628 MiB에 이것이 들어가는지가 문제다.

⚠️ **한 길이만 재면 안 된다.** 공개 이슈에 따르면 pyannote의 peak는 오디오가 길수록
커진다(discrete diarization 단계). 우리 세션은 15분이므로 짧은 파일로 재고
"들어간다"고 결론내면 실제 세션에서 터진다. 그래서 1·5·15분을 같이 잰다.

⚠️ **이 스크립트는 정확도를 재지 않는다.** 합성음을 쓰므로 DER은 의미가 없다.
정확도는 실제 협업 모드 녹음이 있어야 하고, 그건 아직 없다
(`docs/부모협업모드_설계.md` §9-2).

    python -m eval.bench_diarization
"""
from __future__ import annotations

import math
import os
import struct
import subprocess
import tempfile
import time
import wave

MINUTES = [1, 5, 15]
MODEL = "pyannote/speaker-diarization-3.1"


def device_mb() -> float:
    out = subprocess.run(
        ["nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits"],
        capture_output=True, text=True, timeout=30,
    ).stdout.strip().splitlines()[0]
    return float(out)


def make_wav(path: str, seconds: int) -> None:
    """두 사람이 번갈아 말하는 모양의 합성음.

    내용은 뜻이 없다 — 화자 분리 파이프라인이 실제로 돌면서 메모리를 얼마나
    잡는지만 보려는 것이다. 5초마다 음높이를 바꿔 구간이 갈리게 한다.
    """
    sr = 16000
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        frames = bytearray()
        for i in range(sr * seconds):
            hz = 180 if (i // (sr * 5)) % 2 == 0 else 320   # 번갈아
            amp = 0 if (i // (sr * 5)) % 4 == 3 else 6000   # 가끔 쉼
            frames += struct.pack("<h", int(amp * math.sin(2 * math.pi * hz * i / sr)))
        w.writeframes(bytes(frames))


def main() -> None:
    from eval.config import load_dotenv
    load_dotenv()
    token = os.environ.get("HF_TOKEN") or os.environ.get("HUGGINGFACE_TOKEN")
    if not token:
        raise SystemExit("HF_TOKEN이 .env에 없습니다")

    base = device_mb()
    print("시작 시점 장치 사용   %.0f MiB" % base)
    print("  (ComfyUI가 떠 있어야 의미가 있습니다 — 실제 조합을 재는 것이므로)\n")

    import torch
    from pyannote.audio import Pipeline
    import pyannote.audio as pa

    # 실제 구성은 셋이 같이 올라간 상태다. 받아쓰기를 빼고 재면
    # "화자 분리만의 몫"은 나오지만 "조합 A가 되는가"는 답이 안 나온다.
    from eval.bench_coresident import add_cuda_dlls
    add_cuda_dlls()
    from faster_whisper import WhisperModel
    print("받아쓰기 먼저 올립니다...")
    stt = WhisperModel("large-v3-turbo", device="cuda", compute_type="int8_float16")
    # CTranslate2는 게을러서 적재만으로는 VRAM을 거의 안 잡는다. 한 번 돌려야 잡힌다.
    _wav = os.path.join(tempfile.gettempdir(), "warm.wav")
    make_wav(_wav, 6)
    for _ in stt.transcribe(_wav, language="ko", beam_size=5)[0]:
        pass
    os.remove(_wav)
    after_stt = device_mb()
    print("  받아쓰기까지 %.0f MiB (추가 %.0f)\n" % (after_stt, after_stt - base))

    print("pyannote.audio %s · torch %s · cuda %s"
          % (pa.__version__, torch.__version__, torch.cuda.is_available()))
    if pa.__version__.startswith("4"):
        print("⚠️ 4.x는 같은 일에 6배를 씁니다(공개 실측 1.59GB 대 9.54GB). 3.x로 고정하세요.")

    os.environ["HF_TOKEN"] = token
    # torch 2.6부터 torch.load의 weights_only 기본값이 True다. pyannote 체크포인트에는
    # 자기 클래스들이 들어 있어 하나씩 허용하면 끝이 안 난다(TorchVersion 다음에
    # Specifications, 그 다음에 또...). 이 파일은 우리가 HF에서 약관에 동의하고 받은
    # pyannote 공식 체크포인트이므로, 이 측정 스크립트 안에서만 예전 동작으로 되돌린다.
    # ⚠️ 서비스 코드에 그대로 옮기지 말 것 — 출처를 아는 파일에만 쓰는 우회다.
    _load = torch.load
    torch.load = lambda *a, **k: _load(*a, **{**k, "weights_only": False})
    t0 = time.time()
    pipe = Pipeline.from_pretrained(MODEL)
    pipe.to(torch.device("cuda"))
    print("모델 올리는 데 %.1f초 · 추가 %.0f MiB\n" % (time.time() - t0, device_mb() - base))

    rows = []
    for minutes in MINUTES:
        path = os.path.join(tempfile.gettempdir(), "diar_%dmin.wav" % minutes)
        make_wav(path, minutes * 60)
        peak = device_mb()
        t0 = time.time()
        # 우리는 아이와 어른 둘뿐이다. 화자 수를 고정하면 모델이 훨씬 편해진다.
        pipe(path, num_speakers=2)
        took = time.time() - t0
        peak = max(peak, device_mb())
        rows.append((minutes, peak, took))
        print("%2d분 오디오 → 최대 %.0f MiB (추가 %.0f) · 처리 %.1f초"
              % (minutes, peak, peak - base, took))
        os.remove(path)

    total = 12287
    print("\n" + "=" * 56)
    print("%-10s %12s %12s %12s" % ("오디오", "장치 최대", "화자분리 몫", "남는 자리"))
    for minutes, peak, _ in rows:
        print("%-10s %9.0f MiB %9.0f MiB %9.0f MiB"
              % ("%d분" % minutes, peak, peak - base, total - peak))
    print("=" * 56)
    worst = max(r[1] for r in rows)
    print("판정: %s (카드 %d MiB 기준)"
          % ("들어갑니다" if worst < total else "넘칩니다", total))
    print("\n※ 정확도는 안 쟀습니다. 합성음이라 DER은 의미가 없습니다 —")
    print("  실제 협업 모드 녹음이 있어야 하고, 그건 아직 없습니다.")


if __name__ == "__main__":
    main()
