from __future__ import annotations

import os

from .base import ProviderResult, StreamChunk, Usage
from .http_sse import post_sse

ENV_KEY = "ANTHROPIC_API_KEY"


class AnthropicAdapter:
    provider_name = "anthropic"

    def __init__(self) -> None:
        self.api_key = os.getenv(ENV_KEY)
        self.base_url = os.getenv("ANTHROPIC_BASE_URL", "https://api.anthropic.com/v1").rstrip("/")
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
            "max_tokens": max_output_tokens,
            "system": system_prompt,
            "messages": [{"role": "user", "content": user_prompt}],
            "output_config": {
                "format": {
                    "type": "json_schema",
                    "schema": schema,
                }
            },
            "stream": True,
        }

        def generator():
            for event in post_sse(
                url=f"{self.base_url}/messages",
                headers={
                    "x-api-key": self.api_key,
                    "anthropic-version": "2023-06-01",
                    "Content-Type": "application/json",
                    "Accept": "text/event-stream",
                },
                payload=payload,
            ):
                etype = event.get("type")
                if etype == "message_start":
                    u = (event.get("message") or {}).get("usage") or {}
                    usage.input_tokens = int(u.get("input_tokens") or usage.input_tokens or 0)
                    usage.output_tokens = int(u.get("output_tokens") or usage.output_tokens or 0)
                elif etype == "content_block_delta":
                    delta = event.get("delta") or {}
                    if delta.get("type") == "text_delta" and delta.get("text"):
                        yield StreamChunk(str(delta["text"]))
                elif etype == "message_delta":
                    u = event.get("usage") or {}
                    usage.output_tokens = int(u.get("output_tokens") or usage.output_tokens or 0)
                elif etype == "error":
                    raise RuntimeError(f"Anthropic stream failed: {event}")

        return ProviderResult(chunks=generator(), usage=usage)
