"""Engine, session, and the single Base.

There is exactly one Base in this project and it lives here. Files under
models/ import it - they never declare a second one. Two Bases split the
metadata and one set of tables silently fails to be created.
"""

from collections.abc import Iterator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import settings

engine = create_engine(settings.database_url, pool_pre_ping=True, echo=False)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


class Base(DeclarativeBase):
    """The one and only declarative base. Import it, do not redefine it."""


def get_db() -> Iterator[Session]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
