"""Owner: 역할 5. Only the owner edits this file.

Endpoints to implement (see guidelines/3_API_명세.md):
#   GET /template
#   GET /template/{template_id}
#   GET /catalog
#   PUT /catalog/{item_id}

Do not add endpoints beyond that spec without flagging it to 조장.
"""

from fastapi import APIRouter

router = APIRouter(prefix="/template", tags=["template"])
