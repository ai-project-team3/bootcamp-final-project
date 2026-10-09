"""Capture six sequential public /image requests for issue #218, without credentials.

Run with --base-url and a fresh --out directory. Existing evidence is never overwritten.
PNG files are original decoded response bytes. Human review must judge place fidelity;
HTTP success alone is not an image-quality verdict. No backend configuration is loaded.
"""
import argparse
import base64
import hashlib
import json
import struct
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


CASES = (
    ("park", "공원"),
    ("underwater", "바닷속"),
    ("space", "우주"),
    ("grandma_house", "할머니 집"),
    ("future_city", "미래 도시"),
    ("pizza_village", "피자마을"),
)


def request_json(url, payload=None):
    """Return only evaluation fields, never request headers or environment values."""
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json",
                                                       "User-Agent": "OttoBackgroundEvaluation/1.0"})
    started = time.monotonic()
    result = {"url": url, "started_at": datetime.now(timezone.utc).isoformat()}
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            result["http_status"] = response.status
            result["body"] = json.load(response)
            if not isinstance(result["body"], dict):
                result.pop("body")
                result["error"] = "non_object_json"
    except urllib.error.HTTPError as exc:
        result.update(http_status=exc.code, error="http_error")
    except (urllib.error.URLError, TimeoutError, OSError, ValueError) as exc:
        result["error"] = type(exc).__name__
    result["elapsed_s"] = round(time.monotonic() - started, 3)
    return result


def save_png(encoded, path):
    raw = base64.b64decode(encoded, validate=True)
    if raw[:8] != b"\x89PNG\r\n\x1a\n" or len(raw) < 24 or raw[12:16] != b"IHDR":
        raise ValueError("not a PNG")
    width, height = struct.unpack(">II", raw[16:24])
    if width <= 1 or height <= 1:
        raise ValueError("placeholder PNG")
    path.write_bytes(raw)
    return {"png": path.name, "png_bytes": len(raw), "width": width, "height": height,
            "sha256": hashlib.sha256(raw).hexdigest()}


def run(base, out):
    out.mkdir(parents=True, exist_ok=False)
    report = {"base_url": base, "kind": "background", "mode": "story", "style": "felt",
              "health": request_json(base + "/health"), "cases": []}

    def write_report():
        (out / "responses.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    health = report["health"]
    write_report()
    if (health.get("http_status") != 200 or health.get("body", {}).get("mock") is not False
            or health.get("body", {}).get("status") != "ok"):
        print("Health check failed or mock status unverified; no image requests sent.")
        return 1
    failed = False
    for slug, place in CASES:
        payload = {"kind": "background", "place": place, "mode": "story", "style": "felt"}
        record = request_json(base + "/image", payload)
        record.update(case=slug, request=payload)
        body = record.pop("body", {})
        record["response"] = {key: body.get(key) for key in ("preset", "reason", "scene", "rig")}
        if record.get("http_status") == 200 and body.get("preset") is False and body.get("reason") != "mock":
            try:
                record.update(save_png(body.get("png_base64", ""), out / f"{slug}.png"))
            except (ValueError, TypeError) as exc:
                record["error"] = type(exc).__name__
        record["capture_ok"] = "png" in record and bool(body.get("scene"))
        failed |= not record["capture_ok"]
        report["cases"].append(record)
        write_report()
        print(json.dumps({key: record[key] for key in ("case", "http_status", "elapsed_s", "capture_ok", "response") if key in record}, ensure_ascii=False), flush=True)
        if slug != CASES[-1][0]:
            time.sleep(2)
    return int(failed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    url = urllib.parse.urlsplit(args.base_url)
    if url.scheme != "https" or not url.netloc or url.username or url.password or url.query or url.fragment:
        parser.error("Use a public HTTPS base URL without credentials, query, or fragment.")
    return run(args.base_url.rstrip("/"), args.out)


if __name__ == "__main__":
    raise SystemExit(main())
