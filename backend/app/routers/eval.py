"""Owner: 역할 3. Only the owner edits this file.

Endpoints to implement (see guidelines/3_API_명세.md):
#   POST /eval/run

Do not add endpoints beyond that spec without flagging it to 조장.
"""

from fastapi import APIRouter

router = APIRouter(prefix="/eval", tags=["eval"])
