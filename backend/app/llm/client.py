"""One LLM, two effort settings. Spec: 노션 「기능별 모델선정」 §2-2.

Judge and story have opposite latency budgets (2s vs 20s) but that is an
effort setting, not a reason for two models. Split only if story quality
misses the bar in 노션 「오늘 모델검증 역할」 §5.

Provider is a setting. Only OpenAI is wired: gpt-6-luna won the 09-25
measurement. Adding another provider is a branch here, not a change in callers.
"""
import json

import httpx

from ..config import settings


class LLMError(RuntimeError):
    """The vendor failed or returned something that is not the schema. Maps to 502."""


def _output_text(body: dict) -> str:
    for item in body.get("output") or []:
        if item.get("type") != "message":
            continue
        for part in item.get("content") or []:
            if part.get("type") == "output_text":
                return part.get("text") or ""
    raise LLMError("no output_text in response")


async def complete(system: str, user: str, schema: dict, *, effort: str,
                   name: str = "judge", max_output_tokens: int = 768) -> dict:
    """Structured output. Schema compliance is enforced (strict json_schema), not requested."""
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
    if effort and effort != "none":
        payload["reasoning"] = {"effort": effort}

    try:
        async with httpx.AsyncClient(timeout=30) as http:
            r = await http.post(
                f"{settings.openai_base_url.rstrip('/')}/responses",
                headers={"Authorization": f"Bearer {settings.openai_api_key}"},
                json=payload,
            )
    except httpx.HTTPError as e:
        raise LLMError(f"network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise LLMError(f"HTTP {r.status_code}")
    try:
        return json.loads(_output_text(r.json()))
    except json.JSONDecodeError as e:
        raise LLMError("output is not JSON") from e
