from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse

from .config import settings
from .db import Base, SessionLocal, engine
from .study.router import admin as study_admin, router as study_router
from .routers import agents, ai, auth, goals, me, misc, payments, rates, transactions
from .services.agents import seed_agents_if_empty
from .services.rates import seed_if_empty

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings.validate()
    Base.metadata.create_all(engine)   # use Alembic migrations before a real production launch
    with SessionLocal() as db:
        seed_if_empty(db)
        seed_agents_if_empty(db)
    yield


app = FastAPI(title="CoinSprout API", version="1.0.0", lifespan=lifespan,
              docs_url="/docs" if settings.is_dev else None, redoc_url=None, openapi_url="/openapi.json" if settings.is_dev else None)

for r in (auth.router, me.router, rates.router, ai.router, transactions.router, payments.router, goals.router,
          misc.notifications, misc.admin, agents.router):
    app.include_router(r)
app.include_router(study_router)
app.include_router(study_admin)
if settings.is_dev:
    app.include_router(misc.dev)


@app.get("/api/v1/health", tags=["meta"])
def health():
    return {"status": "ok", "env": settings.env, "mpesaProvider": settings.mpesa_provider}


@app.get("/study", include_in_schema=False)
def study_page():
    """The participant-facing questionnaire (mobile-first, no login, no tracking)."""
    return FileResponse(Path(__file__).parent / "static" / "study.html")
