from __future__ import annotations

import argparse
import json
from pathlib import Path

from eval.config import resolve_model, usd_krw
from eval.score import score_judge
from eval.run_team_eval import DEFAULT_MODELS, render_results, safe_name

ROOT = Path(__file__).resolve().parent
EVAL = ROOT  # scripts live inside eval/ now; there is no nested eval/
RAW = EVAL / "raw"


def main():
    p = argparse.ArgumentParser(description="이미 수집한 raw 예측을 gold로 채점 — API 호출 없음")
    p.add_argument("--models", nargs="+", default=DEFAULT_MODELS)
    p.add_argument("--fixtures", default=str(EVAL / "fixtures_judge.jsonl"))
    args = p.parse_args()

    fixtures = Path(args.fixtures)
    if not fixtures.exists():
        raise SystemExit(f"gold 평가셋 없음: {fixtures}")

    scored = []
    missing = []
    fx = usd_krw()
    for alias in args.models:
        raw = RAW / f"{safe_name(alias)}.jsonl"
        if not raw.exists():
            missing.append(alias)
            continue
        cfg = resolve_model(alias)
        metrics = score_judge(
            fixtures=fixtures,
            raw=raw,
            input_price_usd_per_mtok=float(cfg.get("input_usd_per_mtok", 0.0)),
            output_price_usd_per_mtok=float(cfg.get("output_usd_per_mtok", 0.0)),
            usd_krw=fx,
        )
        scored.append((alias, metrics))

    if not scored:
        raise SystemExit("채점할 raw 파일이 없음. 먼저 실제 예측을 수집하세요.")

    md = render_results(scored, fx)
    (EVAL / "results.md").write_text(md, encoding="utf-8")
    (RAW / "comparison_summary.json").write_text(
        json.dumps(dict(scored), ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("API 호출 0건 — 기존 raw만 채점 완료")
    print(f"저장: {EVAL / 'results.md'}")
    if missing:
        print("raw가 없어 제외된 모델:", ", ".join(missing))


if __name__ == "__main__":
    main()
