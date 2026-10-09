"""One LLM, two effort settings. Spec: 노션 「기능별 모델선정」 §2-2.

Judge and story have opposite latency budgets (judge_deadline_s 18 s vs story_deadline_s
55 s, effort none vs high) but that is a setting, not a reason for two models. Split only if story quality
misses the bar in 노션 「오늘 모델검증 역할」 §5.

Provider is a setting. Only OpenAI is wired: gpt-6-luna won the 09-25
measurement. Adding another provider is a branch here, not a change in callers.
"""
import asyncio
import json
import logging
from typing import Callable

import httpx

from .. import vendor_errors
from ..config import settings

log = logging.getLogger("llm")

# Every call's tokens, for cost (#30 · 10-02): the server log gets one line per call, and a
# measurement script can hook in to add them up. Never the text — only the call name and counts.
on_usage: Callable[[str, int, int], None] | None = None
# of those input tokens, how many the vendor served from its prompt cache (billed lower) — a measurement
# that prices every input token at list price overstates the bill (#323 · 10-09: about 2×)
on_cached: Callable[[str, int], None] | None = None


class LLMError(RuntimeError):
    """The vendor failed or returned something that is not the schema. Maps to 502.

    [reason] is set when the vendor call itself failed (app/vendor_errors.py · #298); a bad answer leaves it None."""

    def __init__(self, msg: str, reason: str | None = None):
        super().__init__(msg)
        self.reason = reason


def _vendor_failed(msg: str, reason: str) -> LLMError:
    # "HTTP 429 vendor:quota" — the tag after the space is stable; the app only reads `error`
    return LLMError(f"{msg} {vendor_errors.note('openai', reason)}", reason=reason)


def _output_text(body: dict) -> str:
    seen = []
    for item in body.get("output") or []:
        if item.get("type") != "message":
            seen.append(item.get("type"))
            continue
        for part in item.get("content") or []:
            if part.get("type") == "output_text":
                return part.get("text") or ""
            if part.get("type") == "refusal":
                raise LLMError("refusal")
            seen.append(part.get("type"))
    # Say why, not just that: a cut-off answer (max_output_tokens) and a refusal need different fixes
    why = (body.get("incomplete_details") or {}).get("reason")
    raise LLMError(f"no output_text (status={body.get('status')}, reason={why}, parts={seen})")


async def complete(system: str, user: str, schema: dict, *, effort: str,
                   name: str = "judge", max_output_tokens: int = 768, timeout_s: float = 30.0) -> dict:
    """Structured output. Schema compliance is enforced (strict json_schema), not requested.

    [timeout_s] is the **whole** call. httpx's own timeout is per phase (connect · each read),
    so a slow trickle could run past it; each route passes a deadline under the app's wait
    (10-01: /story with effort high passed 30 s and the phone got 502)."""
    if settings.llm_provider != "openai":
        raise LLMError(f"provider not wired: {settings.llm_provider}")
    if not settings.openai_api_key:
        raise LLMError("missing OPENAI_API_KEY")

    payload = {
        "model": settings.llm_model,
        "instructions": system,
        "input": user,
        "text": {"format": {"type": "json_schema", "name": name, "schema": schema, "strict": True}},
        # store=False: the child's (already masked) words are not kept on the vendor side
        "store": False,
        "max_output_tokens": max_output_tokens,
    }
    # Send "none" explicitly. Leaving the field out is NOT "no reasoning": the model then
    # reasons at its default, spends the token budget on it and returns a cut-off answer
    # (09-28: 2 of 6 live turns died with status=incomplete, parts=['reasoning']).
    # The 09-25 measurement sent effort="none" — this is what makes the server match it.
    if effort:
        payload["reasoning"] = {"effort": effort}

    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(timeout_s, connect=5)) as http:
            r = await asyncio.wait_for(http.post(
                f"{settings.openai_base_url.rstrip('/')}/responses",
                headers={"Authorization": f"Bearer {settings.openai_api_key}"},
                json=payload,
            ), timeout=timeout_s)
    except asyncio.TimeoutError as e:
        raise _vendor_failed(f"over {timeout_s:.0f}s", "timeout") from e
    except httpx.HTTPError as e:
        raise _vendor_failed(f"network: {type(e).__name__}", vendor_errors.classify(exc=e)) from e
    if r.status_code != 200:
        raise _vendor_failed(f"HTTP {r.status_code}", vendor_errors.classify(r.status_code, r))
    body = r.json()
    usage = body.get("usage") or {}
    tin, tout = int(usage.get("input_tokens") or 0), int(usage.get("output_tokens") or 0)
    cached = int((usage.get("input_tokens_details") or {}).get("cached_tokens") or 0)
    log.info("llm %s · in %d (cached %d) · out %d tokens", name, tin, cached, tout)
    if on_usage:
        on_usage(name, tin, tout)
    if on_cached:
        on_cached(name, cached)
    try:
        return json.loads(_output_text(body))
    except json.JSONDecodeError as e:
        raise LLMError("output is not JSON") from e
