from __future__ import annotations

import os

from .base import ProviderResult, StreamChunk, Usage
from .http_sse import post_sse

ENV_KEY = "OPENAI_API_KEY"


class OpenAIAdapter:
    provider_name = "openai"

    def __init__(self) -> None:
        self.api_key = os.getenv(ENV_KEY)
        self.base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
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
            "instructions": system_prompt,
            "input": user_prompt,
            "text": {
                "format": {
                    "type": "json_schema",
                    "name": "judge",
                    "schema": schema,
                    "strict": True,
                }
            },
            "stream": True,
            "store": False,
            "max_output_tokens": max_output_tokens,
        }
        if reasoning_effort:
            payload["reasoning"] = {"effort": reasoning_effort}

        def generator():
            for event in post_sse(
                url=f"{self.base_url}/responses",
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                    "Accept": "text/event-stream",
                },
                payload=payload,
            ):
                etype = event.get("type")
                if etype == "response.output_text.delta":
                    delta = event.get("delta", "")
                    if delta:
                        yield StreamChunk(str(delta))
                elif etype == "response.completed":
                    u = (event.get("response") or {}).get("usage") or {}
                    usage.input_tokens = int(u.get("input_tokens") or 0)
                    usage.output_tokens = int(u.get("output_tokens") or 0)
                elif etype in {"response.failed", "error"}:
                    raise RuntimeError(f"OpenAI stream failed: {event}")

        return ProviderResult(chunks=generator(), usage=usage)
