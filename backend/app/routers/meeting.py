"""Owner: 역할 1. Only the owner edits this file.

Endpoints to implement (see guidelines/3_API_명세.md):
#   POST /meeting
#   POST /meeting/{meeting_id}/link
#   DELETE /meeting/{meeting_id}/link
#   POST /meeting/{meeting_id}/end
#   POST /room/join

Do not add endpoints beyond that spec without flagging it to 조장.
"""

from fastapi import APIRouter

router = APIRouter(prefix="/meeting", tags=["meeting"])
