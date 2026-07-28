"""Test config: a hermetic SQLite file and no external providers, so the whole
suite runs offline. The safety gate's keyword floor is deterministic without a
Gemini key — which is exactly the fail-safe behaviour we want to pin down.
"""
import os
import tempfile

# Must be set BEFORE app/db import (they read these at import time).
_TMP = tempfile.mkdtemp(prefix="aira-test-")
os.environ["SQLITE_PATH"] = os.path.join(_TMP, "aira-test.db")
os.environ.pop("DATABASE_URL", None)
os.environ.pop("GEMINI_API_KEY", None)        # force keyword-only safety + fallback reply
os.environ.pop("APP_SHARED_SECRET", None)     # coarse gate off in tests
os.environ["ENV"] = "development"

import pytest
from fastapi.testclient import TestClient


@pytest.fixture(scope="session")
def client():
    import app
    with TestClient(app.app) as c:   # context manager runs startup (init tables)
        yield c


@pytest.fixture()
def user(client):
    """A registered anonymous device user + auth headers."""
    r = client.post("/device/register")
    assert r.status_code == 200, r.text
    token = r.json()["token"]
    return {"id": r.json()["user_id"], "headers": {"Authorization": f"Bearer {token}"}}
