from __future__ import annotations

import os

from .base import ProviderResult, StreamChunk, Usage
from .http_sse import post_sse

ENV_KEY = "MISTRAL_API_KEY"


def _content_text(content) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, dict):
                text = item.get("text") or item.get("content")
                if text:
                    parts.append(str(text))
        return "".join(parts)
    return ""


class MistralAdapter:
    provider_name = "mistral"

    def __init__(self) -> None:
        self.api_key = os.getenv(ENV_KEY)
        self.base_url = os.getenv("MISTRAL_BASE_URL", "https://api.mistral.ai/v1").rstrip("/")
        if not self.api_key:
            raise RuntimeError(f"missing {ENV_KEY}")

    def stream_judge(
        self,
        *,
        model: str,
        system_prompt: str,
        user_prompt: str,
        schema: dict,
        max_output_tokens: int = 256,
        reasoning_effort: str | None = None,
    ) -> ProviderResult:
        usage = Usage()
        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "response_format": {"type": "json_object"},
            "stream": True,
            "max_tokens": max_output_tokens,
        }
        if reasoning_effort:
            payload["reasoning_effort"] = reasoning_effort

        def generator():
            for event in post_sse(
                url=f"{self.base_url}/chat/completions",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                    "Accept": "text/event-stream",
                },
                payload=payload,
            ):
                u = event.get("usage") or {}
                if u:
                    usage.input_tokens = int(u.get("prompt_tokens") or u.get("input_tokens") or usage.input_tokens or 0)
                    usage.output_tokens = int(u.get("completion_tokens") or u.get("output_tokens") or usage.output_tokens or 0)
                choices = event.get("choices") or []
                if choices:
                    delta = choices[0].get("delta") or {}
                    text = _content_text(delta.get("content"))
                    if text:
                        yield StreamChunk(text)

        return ProviderResult(chunks=generator(), usage=usage)
