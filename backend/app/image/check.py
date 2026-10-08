"""Layer 2 for pictures: our image model has no safety filter (rule 8), so every
picture is checked before it can reach a child.

OpenAI omni-moderation reads images. What goes out is the picture we generated —
no child drawing, no voice; the scene words behind it already went to the same
vendor for the judge. **Fails closed**: no answer, or any flag, means preset.
"""
import base64

import httpx

from .. import vendor_errors
from ..config import settings


async def is_safe(png: bytes) -> tuple[bool, str]:
    if not settings.openai_api_key:
        return False, "no moderation key"
    url = "data:image/png;base64," + base64.b64encode(png).decode()
    try:
        async with httpx.AsyncClient(timeout=6) as http:
            r = await http.post(
                f"{settings.openai_base_url.rstrip('/')}/moderations",
                headers={"Authorization": f"Bearer {settings.openai_api_key}"},
                json={"model": settings.moderation_model,
                      "input": [{"type": "image_url", "image_url": {"url": url}}]},
            )
    except httpx.HTTPError as e:
        # still fails closed (preset) and still 200 to the app — but now the monitor sees it (#298)
        tag = vendor_errors.note("openai", vendor_errors.classify(exc=e))
        return False, f"moderation network: {type(e).__name__} {tag}"
    if r.status_code != 200:
        tag = vendor_errors.note("openai", vendor_errors.classify(r.status_code, r))
        return False, f"moderation HTTP {r.status_code} {tag}"
    res = r.json()["results"][0]
    if res.get("flagged"):
        hits = [k for k, v in res.get("categories", {}).items() if v]
        return False, "flagged: " + ",".join(hits)
    return True, "ok"
