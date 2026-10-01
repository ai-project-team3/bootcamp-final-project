"""One LLM, two effort settings. Spec: 노션 「기능별 모델선정」 §2-2.

Judge and story have opposite latency budgets (2s vs 20s) but that is an
effort setting, not a reason for two models. Split only if story quality
misses the bar in 노션 「오늘 모델검증 역할」 §5.

Provider is a setting. Only OpenAI is wired: gpt-6-luna won the 09-25
measurement. Adding another provider is a branch here, not a change in callers.
"""
import asyncio
import json

import httpx

from ..config import settings


class LLMError(RuntimeError):
    """The vendor failed or returned something that is not the schema. Maps to 502."""


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
        raise LLMError(f"over {timeout_s:.0f}s") from e
    except httpx.HTTPError as e:
        raise LLMError(f"network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise LLMError(f"HTTP {r.status_code}")
    try:
        return json.loads(_output_text(r.json()))
    except json.JSONDecodeError as e:
        raise LLMError("output is not JSON") from e
