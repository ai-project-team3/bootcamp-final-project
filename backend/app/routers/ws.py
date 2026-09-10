"""Owner: 역할 1. Only the owner edits this file.

Endpoints to implement (see guidelines/3_API_명세.md):
#   WS /ws/meeting/{meeting_id}?session_id={sid}

Do not add endpoints beyond that spec without flagging it to 조장.
"""

from fastapi import APIRouter

router = APIRouter(tags=["ws"])
