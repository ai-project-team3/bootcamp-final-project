# -*- coding: utf-8 -*-
"""whisper `large-v3` 에 LoRA 를 붙여 아이 목소리로 학습한다 — RTX 3060 12GB 하룻밤 기준.

무엇을 바꾸나: 원래 모델 무게는 그대로 두고(fp16 · 얼림) 주의(attention) 층 옆에 작은 무게(LoRA)만 학습한다.
학습이 끝나면 `merge_convert.py` 가 이 작은 무게를 원래 모델에 **합쳐** 같은 크기 · 같은 구조의 모델로 만든다
→ 서버(faster-whisper)의 속도는 그대로다(합치지 않고 쓰면 faster-whisper 가 못 읽는다).

    py eval/whisper_ft/train_lora.py --data D:/whisper_ft/data --out D:/whisper_ft/lora
    (중간에 끊겼으면 같은 명령에 --resume)

예상: 학습 4만 클립 · 실효 배치 16 · 2,500 걸음 → 3060 에서 6~8시간(재 보지 않은 추정 — 첫 50걸음 속도로 다시 잰다)
"""
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

import soundfile as sf
import torch
from peft import LoraConfig, get_peft_model
from transformers import (Seq2SeqTrainer, Seq2SeqTrainingArguments, WhisperForConditionalGeneration,
                          WhisperProcessor)

BASE = "openai/whisper-large-v3"


class Clips(torch.utils.data.Dataset):
    def __init__(self, path: Path, proc: WhisperProcessor):
        self.rows = [json.loads(l) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]
        self.proc = proc

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, i: int) -> dict:
        r = self.rows[i]
        audio, sr = sf.read(r["wav"], dtype="float32")
        if audio.ndim > 1:
            audio = audio.mean(axis=1)
        assert sr == 16000, f"16kHz 가 아니다: {r['wav']} ({sr})"
        feats = self.proc.feature_extractor(audio, sampling_rate=16000).input_features[0]
        labels = self.proc.tokenizer(r["text"]).input_ids
        return {"input_features": feats, "labels": labels}


@dataclass
class Collate:
    proc: WhisperProcessor

    def __call__(self, batch: list[dict]) -> dict:
        feats = self.proc.feature_extractor.pad([{"input_features": b["input_features"]} for b in batch], return_tensors="pt")
        lab = self.proc.tokenizer.pad([{"input_ids": b["labels"]} for b in batch], return_tensors="pt")
        labels = lab["input_ids"].masked_fill(lab["attention_mask"].ne(1), -100)
        # 토크나이저가 붙인 시작 토큰은 모델이 스스로 붙이므로 정답에서 뗀다
        if (labels[:, 0] == self.proc.tokenizer.convert_tokens_to_ids("<|startoftranscript|>")).all():
            labels = labels[:, 1:]
        feats["input_features"] = feats["input_features"].half()
        feats["labels"] = labels
        return feats


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="prepare.py 의 --out")
    ap.add_argument("--out", required=True)
    ap.add_argument("--max-steps", type=int, default=2500)
    ap.add_argument("--batch", type=int, default=4)
    ap.add_argument("--accum", type=int, default=4)
    ap.add_argument("--lr", type=float, default=3e-4)
    ap.add_argument("--rank", type=int, default=32)
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--base", default=BASE, help="시험용으로 작은 모델(openai/whisper-tiny)을 줄 때만")
    a = ap.parse_args()
    data, out = Path(a.data), Path(a.out)

    proc = WhisperProcessor.from_pretrained(a.base, language="korean", task="transcribe")
    model = WhisperForConditionalGeneration.from_pretrained(a.base, torch_dtype=torch.float16)
    model.config.apply_spec_augment = True          # 아이 목소리 · 잡음에 덜 흔들리게 (학습 때만 켜진다)
    model.config.mask_time_prob = 0.05
    model.config.use_cache = False                  # 기울기 체크포인트와 같이 못 쓴다
    model.generation_config.language = "korean"
    model.generation_config.task = "transcribe"
    model.generation_config.forced_decoder_ids = None

    lora = LoraConfig(r=a.rank, lora_alpha=a.rank * 2, lora_dropout=0.05,
                      target_modules=["q_proj", "k_proj", "v_proj", "out_proj"], bias="none")
    model = get_peft_model(model, lora)
    # 얼린 원래 무게는 fp16, 학습하는 LoRA 무게만 fp32 — fp16 기울기를 그대로 쓰면 학습이 멈춘다(unscale 오류)
    for p in model.parameters():
        if p.requires_grad:
            p.data = p.data.float()
    # 입력 쪽이 얼어 있으면 기울기 체크포인트가 기울기를 못 이어 준다
    model.base_model.model.model.encoder.conv1.register_forward_hook(lambda m, i, o: o.requires_grad_(True))
    model.print_trainable_parameters()

    args = Seq2SeqTrainingArguments(
        output_dir=str(out), per_device_train_batch_size=a.batch, per_device_eval_batch_size=a.batch,
        gradient_accumulation_steps=a.accum, learning_rate=a.lr, warmup_steps=min(200, a.max_steps // 10), max_steps=a.max_steps,
        lr_scheduler_type="linear", fp16=True, gradient_checkpointing=True,
        eval_strategy="steps", eval_steps=min(500, a.max_steps), save_steps=min(500, a.max_steps), save_total_limit=3,
        logging_steps=25, report_to=[], remove_unused_columns=False, label_names=["labels"],
        dataloader_num_workers=2, predict_with_generate=False,
        load_best_model_at_end=True, metric_for_best_model="eval_loss", greater_is_better=False,
    )
    trainer = Seq2SeqTrainer(
        model=model, args=args, data_collator=Collate(proc),
        train_dataset=Clips(data / "train.jsonl", proc), eval_dataset=Clips(data / "dev.jsonl", proc),
    )
    trainer.train(resume_from_checkpoint=a.resume or None)
    best = out / "best"
    model.save_pretrained(str(best))                 # LoRA 무게만(수십 MB)
    proc.save_pretrained(str(best))
    print(f"→ {best} · 다음: merge_convert.py --adapter {best}")


if __name__ == "__main__":
    main()
