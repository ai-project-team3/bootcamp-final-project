"""말로 짓는 인형극 — backend proxy.

Thin on purpose. It holds the API keys, runs the rule filter, and calls the
LLM. Everything that can live on the phone already does: VAD, name masking,
preset matching, level calculation, report arithmetic.
"""
from fastapi import FastAPI
from app.routers import judge, story, stt

app = FastAPI(title="말로 짓는 인형극")
app.include_router(judge.router)
app.include_router(story.router)
app.include_router(stt.router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
