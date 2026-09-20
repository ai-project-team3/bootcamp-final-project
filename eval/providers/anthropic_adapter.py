from __future__ import annotations

import os

from .base import ProviderResult, StreamChunk, Usage
from .http_sse import post_sse

ENV_KEY = "ANTHROPIC_API_KEY"
# An org-level key (one not tied to a single workspace) is rejected with HTTP 400
# unless the request names the workspace. Optional: a workspace-scoped key needs
# nothing here. Set ANTHROPIC_WORKSPACE_ID in .env to use an org-level key.
ENV_WORKSPACE = "ANTHROPIC_WORKSPACE_ID"


def _to_anthropic_schema(node):
    """Rewrite judge_schema.json into the subset Anthropic's validator accepts.

    Two incompatibilities, both in how the schema is *written* rather than what
    it accepts — the set of valid outputs is identical before and after, which is
    what keeps models comparable:

    1. "name" sits at the top level because that is OpenAI's json_schema wrapper
       field. It is not JSON Schema, and Anthropic rejects it:
       "For 'object' type, property 'name' is not supported".

    2. A nullable enum is written as type ["string", "null"] with null inside the
       enum. Anthropic checks the enum against each declared type separately and
       fails: "Enum value None does not match declared type '['string','null']'".
       The anyOf spelling means exactly the same thing and is accepted.

    Do not use this to relax a constraint. Only shape changes belong here.
    """
    if isinstance(node, list):
        return [_to_anthropic_schema(v) for v in node]
    if not isinstance(node, dict):
        return node

    types = node.get("type")
    enum = node.get("enum")
    if (
        isinstance(types, list)
        and "null" in types
        and isinstance(enum, list)
        and None in enum
    ):
        concrete = [t for t in types if t != "null"]
        rest = {
            k: _to_anthropic_schema(v)
            for k, v in node.items()
            if k not in ("type", "enum")
        }
        branch = {"type": concrete[0] if len(concrete) == 1 else concrete,
                  "enum": [v for v in enum if v is not None]}
        return {**rest, "anyOf": [branch, {"type": "null"}]}

    return {k: _to_anthropic_schema(v) for k, v in node.items()}


class AnthropicAdapter:
    provider_name = "anthropic"

    def __init__(self) -> None:
        self.api_key = os.getenv(ENV_KEY)
        self.workspace_id = (os.getenv(ENV_WORKSPACE) or "").strip()
        self.base_url = os.getenv("ANTHROPIC_BASE_URL", "https://api.anthropic.com/v1").rstrip("/")
        if not self.api_key:
            raise RuntimeError(f"missing {ENV_KEY}")

    def _headers(self) -> dict:
        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": "2023-06-01",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        }
        if self.workspace_id:
            headers["anthropic-workspace-id"] = self.workspace_id
        return headers

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
        # Strip the wrapper field at the root only — "name" nested anywhere else
        # would be a real property key and must survive.
        body = _to_anthropic_schema({k: v for k, v in schema.items() if k != "name"})
        payload = {
            "model": model,
            "max_tokens": max_output_tokens,
            "system": system_prompt,
            "messages": [{"role": "user", "content": user_prompt}],
            "output_config": {
                "format": {
                    "type": "json_schema",
                    "schema": body,
                }
            },
            "stream": True,
        }

        def generator():
            for event in post_sse(
                url=f"{self.base_url}/messages",
                headers=self._headers(),
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
