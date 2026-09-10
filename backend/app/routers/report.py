"""Owner: 역할 4. Only the owner edits this file.

Endpoints to implement (see guidelines/3_API_명세.md):
#   GET /report/{meeting_id}
#   POST /report/{meeting_id}/confirm-checklist

Do not add endpoints beyond that spec without flagging it to 조장.
"""

from fastapi import APIRouter

router = APIRouter(prefix="/report", tags=["report"])
