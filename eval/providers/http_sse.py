from __future__ import annotations

import json
import urllib.error
import urllib.request
from collections.abc import Iterator


def post_sse(*, url: str, headers: dict[str, str], payload: dict, timeout: float = 180.0) -> Iterator[dict]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            for raw in resp:
                line = raw.decode("utf-8", errors="replace").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if not data or data == "[DONE]":
                    continue
                try:
                    yield json.loads(data)
                except json.JSONDecodeError:
                    # Never convert a broken SSE control line into a model JSON failure.
                    continue
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {e.code} from {url}: {detail[:1200]}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"network error calling {url}: {e}") from e
