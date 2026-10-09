"""One synthetic eight-page story through the real route, including its validation (#261).

Run from the repository root: py eval/bench_story_eight_pages.py
An optional --settings-file loads credentials through the normal Settings runtime.
No private settings or request headers are printed.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))

from app import config


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--settings-file", type=Path)
    parser.add_argument("--server", help="Measure the deployed /story route instead of the local runtime")
    parser.add_argument("--output", type=Path, default=ROOT / "eval/raw/story_eight_pages.json")
    args = parser.parse_args()
    if args.settings_file:
        config.settings = config.Settings(_env_file=args.settings_file)
    config.settings.mock = False
    # Import after configuring the runtime so route and client share the same settings.
    from app.routers.story import story
    from app.schemas.story import StoryRequest

    slots = {
        "place": "바닷가", "problem": "모래성이 무너졌어", "reaction": "속상했어",
        "cause": "파도가 와서", "newcomer": "물고기", "name": "파랑이",
        "companion": "엄마", "adult": "엄마가 조개를 모아 줬어",
        "solution": "높은 곳에 같이 다시 쌓았어", "sound": "찰랑찰랑",
        "extra": "집에 가는 길에 조개를 세어 봤어",
    }
    pages = [
        {"kind": "DEPART"}, {"kind": "SHAKE"}, {"kind": "JOURNEY"},
        {"kind": "RUB", "mission": "A6", "prop": "모래"},
        {"kind": "DRAG", "mission": "E1", "prop": "조개"},
        {"kind": "JOURNEY"}, {"kind": "TALK"}, {"kind": "TOGETHER"},
    ]
    request = StoryRequest(mode="story", template="E", level="pick", slots=slots,
                           slot_by={key: "child" for key in slots}, pages=pages)
    started = time.monotonic()
    if args.server:
        import httpx
        from app.schemas.story import StoryResult
        from app.routers.story import check
        async with httpx.AsyncClient(timeout=65) as client:
            response = await client.post(args.server.rstrip("/") + "/story", json=request.model_dump())
            if response.is_error:
                failure = {"accepted": False, "status": response.status_code,
                           "seconds": round(time.monotonic() - started, 3)}
                # The server's status is enough for the measurement; never echo response
                # bodies, headers or settings which could contain operational details.
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_text(json.dumps(failure, indent=2), encoding="utf-8")
                print(json.dumps(failure))
                raise SystemExit(1)
            response.raise_for_status()
            result = StoryResult.model_validate(response.json())
        if check(result, "story", request.pages):
            raise RuntimeError("The deployed book failed page/content validation")
        if [scene.kind for scene in result.scenes] != [page.kind for page in request.pages]:
            raise RuntimeError("The deployed book changed the page plan")
    else:
        result = await story(request)
    elapsed = time.monotonic() - started
    record = {
        "model": None if args.server else config.settings.llm_model,
        "effort": None if args.server else config.settings.llm_effort_story,
        "server": args.server,
        "seconds": round(elapsed, 3),
        "deadline_s": None if args.server else config.settings.story_deadline_s,
        "request": request.model_dump(), "result": result.model_dump(),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: record[key] for key in ("model", "effort", "seconds", "deadline_s")}
                     | {"pages": len(result.scenes), "accepted": True}, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
