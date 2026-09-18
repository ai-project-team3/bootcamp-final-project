"""One LLM, two effort settings. Spec: docs/기능별_모델선정.md §2-2.

Judge and story have opposite latency budgets (2s vs 20s) but that is an
effort setting, not a reason for two models. Split only if story quality
misses the bar in docs/오늘_모델검증_역할.md §5.

Provider is a setting. W1 measurement decides luna vs haiku vs nano, and the
answer must be one line in .env, not a code change.
"""
from ..config import settings


async def complete(prompt: str, schema: dict, *, effort: str) -> dict:
    """Structured output. Schema compliance is enforced, not requested.

    OpenAI  : response_format json_schema, strict=True + reasoning_effort
    Anthropic: constrained decoding
    Mistral : json_object + schema in the prompt
    """
    raise NotImplementedError("W1: implement after the model is chosen")
