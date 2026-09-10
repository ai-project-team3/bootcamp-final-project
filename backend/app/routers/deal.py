"""Owner: 역할 5. Only the owner edits this file.

Endpoints to implement (see guidelines/3_API_명세.md):
#   GET /deal
#   POST /deal
#   PATCH /deal/{deal_id}
#   DELETE /deal/{deal_id}

Do not add endpoints beyond that spec without flagging it to 조장.
"""

from fastapi import APIRouter

router = APIRouter(prefix="/deal", tags=["deal"])
