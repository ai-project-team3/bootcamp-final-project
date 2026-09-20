from __future__ import annotations

import json
import urllib.error
import urllib.request
from collections.abc import Iterator
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime


class HttpStatusError(RuntimeError):
    def __init__(self, status_code: int, url: str, detail: str, headers: dict[str, str]) -> None:
        self.status_code = status_code
        self.url = url
        self.detail = detail
        self.headers = {str(k).lower(): str(v) for k, v in headers.items()}
        super().__init__(f"HTTP {status_code} from {url}: {detail[:1200]}")

    @property
    def retry_after_seconds(self) -> float | None:
        value = self.headers.get("retry-after")
        if not value:
            return None
        try:
            return max(0.0, float(value))
        except ValueError:
            try:
                when = parsedate_to_datetime(value)
                if when.tzinfo is None:
                    when = when.replace(tzinfo=timezone.utc)
                return max(0.0, (when - datetime.now(timezone.utc)).total_seconds())
            except (TypeError, ValueError, OverflowError):
                return None


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
        headers = dict(e.headers.items()) if e.headers else {}
        raise HttpStatusError(e.code, url, detail, headers) from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"network error calling {url}: {e}") from e
