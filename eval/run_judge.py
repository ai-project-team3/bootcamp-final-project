from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

try:
    from .config import key_env_for, load_dotenv, resolve_model
    from .providers import create_provider
    from .providers.http_sse import HttpStatusError
except ImportError:
    from config import key_env_for, load_dotenv, resolve_model
    from providers import create_provider
    from providers.http_sse import HttpStatusError

ROOT = Path(__file__).resolve().parent


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_jsonl(path: Path):
    with path.open("r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, start=1):
            if line.strip():
                try:
                    yield json.loads(line)
                except json.JSONDecodeError as e:
                    raise ValueError(f"invalid JSONL at {path}:{line_no}: {e}") from e


def load_system_prompt(path: Path, schema: dict) -> str:
    base = path.read_text(encoding="utf-8").strip()
    schema_text = json.dumps(schema, ensure_ascii=False, separators=(",", ":"))
    return f"{base}\n\n[공통 JSON 스키마]\n{schema_text}\n"


def build_user_prompt(item: dict) -> str:
    """Gold label은 절대 모델에 보내지 않는다."""
    slots = json.dumps(item.get("slots", {}), ensure_ascii=False, separators=(",", ":"))
    return (
        # id 는 **프롬프트에 넣지 않는다** (09-22). 모델이 읽지 않는 값이고, 채점은
        # 출력 레코드의 id 로 짝을 맞춘다(`score.py:109`) — 모델이 돌려주는 값이 아니다.
        # ⚠️ 이 줄을 뺀 것이 09-20 측정과의 유일한 프롬프트 차이다. 그래서 루나를 다시 쟀다.
        f"slots:{slots}\n"
        f"asked:{item.get('asked', '')}\n"
        f"template:{item.get('template', '')}\n"
        f"utterance:{item.get('utterance', '')}\n"
        "현재 슬롯 전체와 아이 발화를 함께 보고 판정하세요. JSON 외에는 출력하지 마세요."
    )


def _type_ok(value, spec) -> bool:
    if isinstance(spec, list):
        return any(_type_ok(value, s) for s in spec)
    return {
        "null": value is None,
        "string": isinstance(value, str),
        "boolean": isinstance(value, bool),
        "number": isinstance(value, (int, float)) and not isinstance(value, bool),
        "integer": isinstance(value, int) and not isinstance(value, bool),
        "object": isinstance(value, dict),
        "array": isinstance(value, list),
    }.get(spec, True)


def validate_pred(obj: object, schema: dict) -> dict:
    """현재 팀 스키마처럼 1단 평면 JSON Schema를 검증한다.

    팀 스키마가 또 바뀌어도 required/type/enum/additionalProperties 범위는
    코드 수정 없이 따라가도록 하드코딩 필드명을 없앴다.
    """
    if not isinstance(obj, dict):
        raise ValueError("prediction is not a JSON object")

    required = schema.get("required", [])
    missing = [k for k in required if k not in obj]
    if missing:
        raise ValueError(f"missing fields: {missing}")

    props = schema.get("properties", {})
    if schema.get("additionalProperties") is False:
        extras = [k for k in obj if k not in props]
        if extras:
            raise ValueError(f"unexpected fields: {extras}")

    for key, rules in props.items():
        if key not in obj:
            continue
        value = obj[key]
        if "type" in rules and not _type_ok(value, rules["type"]):
            raise ValueError(f"{key} has wrong type: {type(value).__name__}")
        if "enum" in rules and value not in rules["enum"]:
            raise ValueError(f"{key} is outside enum: {value!r}")
    return obj


def run(
    *,
    model: str,
    fixtures: Path,
    out: Path,
    prompt_path: Path | None = None,
    schema_path: Path | None = None,
    allow_real_api: bool = False,
    max_output_tokens: int = 768,
    request_delay: float = 0.0,
    max_retries: int = 4,
    retry_base_delay: float = 1.0,
    retry_max_delay: float = 60.0,
) -> list[dict]:
    load_dotenv()
    cfg = resolve_model(model)
    provider_name = cfg["provider"]
    if provider_name != "mock" and not allow_real_api:
        raise RuntimeError("real API call blocked: rerun with --yes-spend")

    key_env = key_env_for(provider_name)
    if key_env:
        import os
        if not os.getenv(key_env):
            raise RuntimeError(f"missing {key_env}; no API calls were made")

    prompt_path = prompt_path or (ROOT / "judge_prompt_demo.md")
    schema_path = schema_path or (ROOT / "judge_schema_demo.json")
    if not prompt_path.exists():
        raise FileNotFoundError(prompt_path)
    if not schema_path.exists():
        raise FileNotFoundError(schema_path)

    schema = load_json(schema_path)
    system_prompt = load_system_prompt(prompt_path, schema)
    provider = create_provider(provider_name)
    if request_delay < 0:
        raise ValueError("request_delay must be >= 0")
    if max_retries < 0:
        raise ValueError("max_retries must be >= 0")
    out.parent.mkdir(parents=True, exist_ok=True)
    rows = []

    with out.open("w", encoding="utf-8") as fw:
        for item_index, item in enumerate(load_jsonl(fixtures)):
            if item_index and request_delay:
                time.sleep(request_delay)
            user_prompt = build_user_prompt(item)
            t0 = time.perf_counter()
            ttft = None
            text_parts = []
            usage = None
            attempts = 0
            try:
                while True:
                    attempts += 1
                    text_parts = []
                    usage = None
                    try:
                        result = provider.stream_judge(
                            model=cfg["api_model"],
                            system_prompt=system_prompt,
                            user_prompt=user_prompt,
                            schema=schema,
                            max_output_tokens=max_output_tokens,
                            reasoning_effort=cfg.get("reasoning_effort"),
                        )
                        usage = result.usage
                        for chunk in result.chunks:
                            now = time.perf_counter()
                            if ttft is None and chunk.text:
                                ttft = now - t0
                            text_parts.append(chunk.text)
                        break
                    except HttpStatusError as e:
                        if e.status_code != 429 or attempts > max_retries:
                            raise
                        fallback = retry_base_delay * (2 ** (attempts - 1))
                        if e.retry_after_seconds is not None:
                            delay = e.retry_after_seconds
                        else:
                            delay = min(retry_max_delay, fallback)
                        delay = max(0.0, delay)
                        print(
                            f"[RETRY] {item.get('id')} HTTP 429 "
                            f"attempt={attempts}/{max_retries + 1} wait={delay:.1f}s"
                        )
                        time.sleep(delay)
                total = time.perf_counter() - t0
                raw_text = "".join(text_parts)
                pred = validate_pred(json.loads(raw_text), schema)
                row = {
                    "id": item["id"],
                    "model": model,
                    "api_model": cfg["api_model"],
                    "provider": provider_name,
                    "ok": True,
                    "ttft": round(ttft if ttft is not None else total, 6),
                    "total": round(total, 6),
                    "pred": pred,
                    "in_tok": usage.input_tokens if usage else 0,
                    "out_tok": usage.output_tokens if usage else 0,
                    "attempts": attempts,
                }
            except Exception as e:
                total = time.perf_counter() - t0
                row = {
                    "id": item.get("id"),
                    "model": model,
                    "api_model": cfg["api_model"],
                    "provider": provider_name,
                    "ok": False,
                    "ttft": round(ttft, 6) if ttft is not None else None,
                    "total": round(total, 6),
                    "error": f"{type(e).__name__}: {e}",
                    "raw": "".join(text_parts),
                    "in_tok": usage.input_tokens if usage else 0,
                    "out_tok": usage.output_tokens if usage else 0,
                    "attempts": attempts,
                }
            fw.write(json.dumps(row, ensure_ascii=False) + "\n")
            fw.flush()
            rows.append(row)
            status = "OK" if row["ok"] else "FAIL"
            print(f"[{status}] {item.get('id')} total={row['total']:.3f}s")
    return rows


def main():
    p = argparse.ArgumentParser(description="공통 판정 러너 — mock 또는 실제 API")
    p.add_argument("--model", default="mock-judge-v1")
    p.add_argument("--fixtures", default=str(ROOT / "fixtures_judge_demo.jsonl"))
    p.add_argument("--out", default=str(ROOT / "raw" / "mock_judge.jsonl"))
    p.add_argument("--prompt", default=str(ROOT / "judge_prompt_demo.md"))
    p.add_argument("--schema", default=str(ROOT / "judge_schema_demo.json"))
    p.add_argument("--max-output-tokens", type=int, default=768)
    p.add_argument("--request-delay", type=float, default=1.0, help="요청 사이 대기 시간(초)")
    p.add_argument("--max-retries", type=int, default=4, help="HTTP 429 최대 재시도 횟수")
    p.add_argument("--yes-spend", action="store_true", help="실제 유료 API 호출을 명시적으로 허용")
    p.add_argument("--dry-run", action="store_true", help="모델/키/파일만 확인하고 호출하지 않음")
    args = p.parse_args()

    cfg = resolve_model(args.model)
    if args.dry_run:
        import os
        key_env = key_env_for(cfg["provider"])
        print(f"alias={args.model}")
        print(f"provider={cfg['provider']}")
        print(f"api_model={cfg['api_model']}")
        print(f"key={key_env or '(mock)'}: {'SET' if not key_env or os.getenv(key_env) else 'MISSING'}")
        print(f"fixtures={Path(args.fixtures)}: {'OK' if Path(args.fixtures).exists() else 'MISSING'}")
        print(f"prompt={Path(args.prompt)}: {'OK' if Path(args.prompt).exists() else 'MISSING'}")
        print(f"schema={Path(args.schema)}: {'OK' if Path(args.schema).exists() else 'MISSING'}")
        return

    rows = run(
        model=args.model,
        fixtures=Path(args.fixtures),
        out=Path(args.out),
        prompt_path=Path(args.prompt),
        schema_path=Path(args.schema),
        allow_real_api=args.yes_spend,
        max_output_tokens=args.max_output_tokens,
        request_delay=args.request_delay,
        max_retries=args.max_retries,
    )
    ok = sum(1 for r in rows if r["ok"])
    print(f"judge done: {ok}/{len(rows)} JSON success -> {args.out}")


if __name__ == "__main__":
    main()
