"""POST /story — once or twice per session. Latency budget is generous."""
from fastapi import APIRouter
from ..schemas.story import StoryRequest, StoryResult

router = APIRouter()


@router.post("/story", response_model=StoryResult)
async def story(req: StoryRequest) -> StoryResult:
    raise NotImplementedError("W1")
