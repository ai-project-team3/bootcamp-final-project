# -*- coding: utf-8 -*-
"""학습한 LoRA 를 원래 `large-v3` 에 합치고 faster-whisper(CTranslate2) 모델로 바꾼다.

합친 모델은 원래와 크기 · 구조가 같다 → 서버 속도가 그대로다. 정밀도는 지금 서버와 같게 둔다:
변환은 float16, 서버가 읽을 때 `int8_float16`(backend/app/config.py `stt_compute_type`) — 다르게 두면
속도 · 정확도가 같이 바뀌어 학습 효과를 가를 수 없다.

    py eval/whisper_ft/merge_convert.py --adapter D:/whisper_ft/lora/best --out D:/whisper_ft/ct2

남이 올린 미세조정 모델을 같은 잣대로 재려면 LoRA 대신 그 모델을 준다(추가 비교 · README):

    py eval/whisper_ft/merge_convert.py --model sikaro/unsloth_whisper_large_v3_16bit_Zeroth_kor --out D:/whisper_ft/ct2_zeroth

서버에 쓸 때(채택 기준을 통과한 뒤에만): PC1 `.env` 에 `STT_MODEL=<ct2 폴더>` 한 줄. 되돌리기는 그 줄을 지운다.
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

import torch
from peft import PeftModel
from transformers import WhisperFeatureExtractor, WhisperForConditionalGeneration, WhisperProcessor

BASE = "openai/whisper-large-v3"


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--adapter", help="train_lora.py 가 남긴 LoRA — 원래 모델에 합친다")
    src.add_argument("--model", help="이미 미세조정된 whisper(Hugging Face 이름) — 합치지 않고 바꾸기만 한다")
    ap.add_argument("--out", required=True)
    ap.add_argument("--base", default=BASE, help="시험용으로 작은 모델(openai/whisper-tiny)을 줄 때만")
    a = ap.parse_args()
    out = Path(a.out)
    merged = out.with_name(out.name + "_hf")

    if a.model:
        # 같은 구조(large-v3 · 멜 128)인지 먼저 본다 — 다르면 토크나이저 · 멜 설정을 빌려 쓸 수 없다
        model = WhisperForConditionalGeneration.from_pretrained(a.model, torch_dtype=torch.float16)
        ref = WhisperForConditionalGeneration.config_class.from_pretrained(a.base)
        for k in ("num_mel_bins", "d_model", "encoder_layers", "decoder_layers", "vocab_size"):
            assert getattr(model.config, k) == getattr(ref, k), f"{a.model} 의 {k} 가 {a.base} 와 다르다"
    else:
        model = WhisperForConditionalGeneration.from_pretrained(a.base, torch_dtype=torch.float16)
        model = PeftModel.from_pretrained(model, a.adapter).merge_and_unload()
    model.generation_config.forced_decoder_ids = None
    model.save_pretrained(str(merged))
    WhisperProcessor.from_pretrained(a.base).save_pretrained(str(merged))
    # transformers 5 는 processor_config.json 하나로 묶어 저장한다 — faster-whisper 는 preprocessor_config.json(멜 칸 수)을 찾는다
    WhisperFeatureExtractor.from_pretrained(a.base).save_pretrained(str(merged))
    print(f"합친 모델 → {merged}")

    from ctranslate2.converters import TransformersConverter
    if out.exists():
        shutil.rmtree(out)
    # large-v3 는 멜 128칸이라 preprocessor_config.json 이 같이 있어야 faster-whisper 가 맞게 읽는다
    TransformersConverter(str(merged), copy_files=["tokenizer.json", "preprocessor_config.json"]).convert(
        str(out), quantization="float16")
    print(f"faster-whisper 모델 → {out}")


if __name__ == "__main__":
    main()
