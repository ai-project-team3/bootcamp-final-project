"""The Laya judge sidecar — loads the fine-tuned checkpoint once and answers /predict.

The backend never imports torch: app/llm/laya.py posts {state, questions} here and reads the answers.

On the team server the backend is a container (scripts/deploy/start_backend.ps1, network `otto`), so the
sidecar is a container on the same network too — scripts/deploy/start_laya.ps1 — and the backend reaches it
at http://otto-laya:8100 (LAYA_URL). No port is published to the host, so the child's words never leave
the machine. On a dev PC without Docker it runs as a plain process on 127.0.0.1:

    pip install "laya==0.3.28" fastapi uvicorn          # torch with CUDA if there is a GPU
    set LAYA_CKPT=<the checkpoint folder>                # otto-judge-v4 from the deploy bundle (not in git)
    python backend/scripts/laya_judge_server.py          # 127.0.0.1:8100

One call is about 0.05 s on a GPU; a CPU takes seconds. /health also reports the GPU memory this process
holds — Windows nvidia-smi often shows N/A per process (WDDM).
"""
from __future__ import annotations

import os
import time

import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

CKPT = os.environ.get("LAYA_CKPT", "")
DEVICE = os.environ.get("LAYA_DEVICE") or None      # None = cuda if there is one
HOST = os.environ.get("LAYA_HOST", "127.0.0.1")     # 0.0.0.0 only inside a container with no published port
PORT = int(os.environ.get("LAYA_PORT", "8100"))

app = FastAPI(title="otto laya judge")
agent = None


class Predict(BaseModel):
    state: str
    questions: dict


@app.on_event("startup")
def load() -> None:
    global agent
    if not CKPT or not os.path.isdir(CKPT):
        raise SystemExit(f"LAYA_CKPT is not a folder: {CKPT!r}")
    import laya
    agent = laya.load(CKPT, device=DEVICE)
    agent.predict("모드: story\n채워진 칸: 없음\n오또가 물은 칸: place\n오또 질문: 어디 갈까?\n아이 말: 바다",
                  {"q": {"type": "noul", "instructions": "준비", "criteria": {"true": "예", "false": "아니오"}}})


def _gpu() -> dict:
    try:
        import torch
        if not torch.cuda.is_available():
            return {"device": "cpu"}
        free, total = torch.cuda.mem_get_info()
        return {"device": torch.cuda.get_device_name(0),
                "this_process_mib": round(torch.cuda.memory_reserved() / 2**20),
                "gpu_used_mib": round((total - free) / 2**20), "gpu_total_mib": round(total / 2**20)}
    except Exception as e:  # noqa: BLE001 — health must answer even if the probe fails
        return {"error": type(e).__name__}


@app.get("/health")
def health() -> dict:
    return {"ok": agent is not None, "ckpt": os.path.basename(CKPT.rstrip("/\\")), "gpu": _gpu()}


@app.post("/predict")
def predict(body: Predict) -> dict:
    if agent is None:
        raise HTTPException(503, "loading")
    t0 = time.monotonic()
    res = agent.predict(body.state, body.questions)
    if (res.get("usage") or {}).get("truncated"):
        # the short state is ~100 tokens; a cut means the child's words fell off the end
        raise HTTPException(422, "state truncated")
    return {"answers": res["answers"], "seconds": round(time.monotonic() - t0, 3)}


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)
